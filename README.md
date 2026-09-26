# cc-food-recall

管理原料批次、生产转换和召回隔离，支持召回通知、下游确认和召回有效性统计。

## 开发环境

- JDK 21
- Spring Boot 4.1.1
- Maven Wrapper 3.9.9
- H2

## 本地运行

启动服务：

    ./mvnw spring-boot:run

运行测试：

    ./mvnw clean test

## 领域模型

- **批次（Lot）**：原料/成品库存单位，数量精度 3 位小数。
- **生产转换（Transformation）**：输入批次消耗、输出批次新增，质量守恒（输入 = 输出 + 损耗）。
- **发货去向（Shipment）**：记录批次数量流向的下游持有方，是召回"已知去向"的来源；发货扣减批次库存。
- **召回事件（RecallEvent）**：针对某批次发起，按发起时刻的谱系向下游传播影响。
- **影响清单（RecallImpact）**：召回覆盖的批次集合，创建后只增不改，关闭后仍保留。
- **召回通知（RecallNotification）**：针对每个下游持有方的通知任务，记录创建后不可变。
- **下游报告（RecallReport）**：持有方提交的处置确认（已隔离/已消费/已转交/数量不符）。

## 召回流程

### 发起召回

`POST /api/recalls`

```json
{"recallNumber": "R-1", "lotNumber": "A", "reason": "a-contamination"}
```

- 按发起时刻的批次谱系生成不可变的初始影响清单，受影响批次置为隔离。
- 按发起时刻的已知去向（发货记录）为每个下游持有方创建通知任务；
  同一持有方的多条发货合并为一条初始通知，同一批次经多条谱系路径到达
  同一持有方时不会重复计算受影响数量。
- 通知号按 `{召回号}-N{序号}` 生成，召回号幂等：重复发起相同内容的召回
  返回原结果，不产生重复通知；内容不一致返回 409。

### 登记发货去向

`POST /api/shipments`

```json
{"shipmentKey": "S-1", "lotNumber": "A", "holder": "H1", "quantity": 40}
```

- 发货编号幂等；库存不足返回 409。
- 若批次处于进行中的召回影响清单内，则为该召回**追加**新的通知记录
  （新发现的去向），原通知记录保持不变，并递增召回统计版本。

### 下游报告

`POST /api/recalls/{recallNumber}/reports`

```json
{"reportNumber": "RP-1", "holder": "H1", "type": "ISOLATED", "quantity": 30}
```

- 报告类型：`ISOLATED`（已隔离）、`CONSUMED`（已消费）、
  `TRANSFERRED`（已转交，须填 `transferredTo`）、`MISMATCH`（数量不符）。
- 数量守恒：同一持有方在同一召回下所有报告数量之和不得超过其接收总量
  （接收总量 = 该召回下其全部通知记录数量之和），超出返回 409。
- 转交报告会为接收方追加一条 `TRANSFER` 来源的通知记录，接收方随后
  可在其接收量内继续报告，形成逐级追溯链。
- 报告号幂等：相同报告号相同内容返回原结果，内容不一致返回 409。
- 召回关闭后不再接受报告（409）。

### 关闭召回

`POST /api/recalls/{recallNumber}/close`

```json
{"closeNumber": "C-1", "approvedBy": "qa-lead", "expectedStatsVersion": 2}
```

- 必须满足配置的响应门槛（`app.recall.required-response-ratio`，默认 `1.0`，
  即全部被通知持有方都已提交至少一份报告）才能关闭，否则返回 409。
- 关闭时记录尚未收回数量（`unrecoveredQuantity`）与批准人（`approvedBy`）。
- `expectedStatsVersion` 可选：若客户端基于某次统计作出关闭决定，可携带
  当时的统计版本；期间若有新的下游报告、新发现的去向或新的生产转换传播，
  统计版本已前进，基于旧统计的关闭决定返回 409 失效。
- 关闭号幂等：相同关闭号重复关闭返回原结果，不同关闭号返回 409。
- 关闭后影响清单保留用于统计查询，但批次视图只展示进行中召回的原因。

### 统计口径

- 发货总量（dispatchedQuantity）：首级发货通知数量之和（转交不重复计入）。
- 尚未收回（unrecoveredQuantity）= 发货总量 − 已隔离 − 已消费；
  转交数量作为接收方的接收量继续跟踪，数量不符部分仍属未收回。
- 完成率（completionRate）=（已隔离 + 已消费）/ 发货总量；
  发货总量为 0 时视为 1。
- 响应率（responseRate）= 已响应持有方数 / 被通知持有方数。

## 查询接口

| 接口 | 说明 |
| --- | --- |
| `GET /api/lots/{lotNumber}` | 批次详情（隔离状态、进行中召回原因） |
| `GET /api/lots/{lotNumber}/genealogy` | 批次谱系（上游/下游） |
| `GET /api/recalls/{recallNumber}/impacts` | 影响批次清单 |
| `GET /api/recalls/{recallNumber}/notifications` | 通知任务清单（含追加记录） |
| `GET /api/recalls/{recallNumber}/holders` | 各持有方接收量与分类报告量 |
| `GET /api/recalls/{recallNumber}/discrepancies` | 各持有方接收量与已说明数量的差异 |
| `GET /api/recalls/{recallNumber}/effectiveness` | 召回完成率、响应率、未收回数量、统计版本 |

## 并发设计

- 写操作统一经过 `Transactions.idempotent`：唯一约束冲突时重试一次，
  走幂等查询路径返回已有记录或报 409。
- 行锁顺序统一为"先批次、后召回事件"，避免转换/发货与关闭之间循环等待；
  加召回行锁后通过 `EntityManager.refresh` 读取最新提交状态。
- 统计版本（statsVersion）随报告、通知追加、转换传播递增，
  关闭与上述事件并发时基于旧版本的关闭决定失效。
