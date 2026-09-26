# cc-food-recall

管理原料批次、生产转换、召回隔离，以及召回通知、下游确认和召回有效性统计。

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

```
Lot（批次，工厂库存台账）
  └─ Transformation（生产转换：多批次投入 → 多批次产出 + 损耗，质量守恒，谱系无环）
DownstreamHolder（下游持有方：经销商/零售商/客户）
LotDestination（去向：批次发往持有方的不可变发货流水）
RecallEvent（召回事件，含响应门槛与统计版本号）
  ├─ RecallImpact（影响清单：受影响批次，只追加，批次号 1=初始清单，2..=后续追加）
  ├─ RecallNotification（通知任务：每次通知一条，原通知永不改写）
  │    └─ NotificationItem（通知批次明细及受影响数量，按持有方+批次聚合去重）
  ├─ DownstreamReport（下游响应报告，报告号幂等）
  │    └─ ReportItem（已隔离/已消费/已转交/数量不符）
  └─ RecallClosure（关闭记录：批准人、未收回数量、依据版本、响应率/完成率）
```

## 召回有效性的关键规则

### 1. 不可变的初始影响清单与通知

发起召回时，系统根据**当时**的批次谱系（沿转换产出边求后代闭包）和工厂台账中
**已知去向**（已发货记录）：

- 生成初始影响清单 `RecallImpact`（`batchNumber=1`），记录每个受影响批次及当时库存快照、
  根批次归因比例；
- 为每个下游持有方创建一条通知任务，同一持有方的多个批次聚合在一条通知中；
- 通知号由“召回号-N批次号-持有方编码”确定性生成，保证重复发起/追加时幂等。

召回**之后**新发现的去向（召回进行中又发生了新的生产转换，产出新的后代批次）：

- 以新的批次号（2、3…）**追加**影响清单与通知；
- 原来的通知记录**永不修改、永不删除**，关闭召回后清单也保留作为历史。

受进行中召回影响（已隔离）的批次禁止再发货。

### 2. 谱系归因与多路径去重

一次转换中，每个输出批次来自召回根批次的比例为：

```
输出根批次比例 = Σ(受影响投入量 × 该投入的根批次比例) / 输出总量
```

同一批次经由多条谱系路径到达时（菱形汇合），各路径贡献在转换节点的**比例层面相加**，
而产品数量本身只持有一份，因此同一持有方+同一批次在任何统计中都**不会重复计算**。

### 3. 下游报告与数量守恒

下游可报告四种处置：

| disposition | 含义 | 是否收回 |
|---|---|---|
| `QUARANTINED` | 已隔离封存 | 是 |
| `CONSUMED` | 已消费/使用 | 否 |
| `TRANSFERRED` | 已转交其他持有方（须给 `transferredToHolderCode`） | 继续追踪 |
| `DISCREPANCY` | 数量不符，账面找不到 | 否 |

守恒规则（每个持有方 × 每个批次）：

```
接收量（初始发货 + 后续转入） ≥ 已隔离 + 已消费 + 已转交 + 数量不符（历次报告累计）
```

超出接收量的报告返回 409 且整份报告不生效。报告可分多次提交。

“已转交”会为接收持有方生成一条**新的追加通知**（新批次号），接收方继续响应；
转交量沿持有方链条只计量一次，不重复计入召回影响总量分母。

持有方对其每个批次都交代清楚后，对应通知任务从 `PENDING` 变为 `RESPONDED`。

### 4. 关闭门槛、批准人与并发失效

- 每个召回有响应率门槛 `minResponseRate`（配置项 `recall.min-response-rate`，默认 `1.0`，
  发起时可按 0~1 覆盖）。当前响应率未达门槛时关闭返回 409。
- 关闭必须提供批准人 `approver`，关闭记录中明确写入：
  依据的统计版本、当时响应率/完成率、**尚未收回数量**。
- 召回带有 `statisticsVersion`：每追加一批影响通知、每收到一份报告都会 +1。
  关闭时携带 `expectedVersion`，若与当前版本不一致（说明关闭确认与新的报告/新的生产转换
  并发，关闭决定基于过期统计），关闭返回 409 失效，需重新确认。
- 关闭后不能再提交下游报告。

并发安全通过统一加锁顺序（写操作先锁批次行、再锁召回行；隔离标记释放在关闭事务
提交后的新事务中执行）与悲观锁实现，关闭与报告/转换之间不会出现“已关闭但有报告
未计入统计”的状态。

### 5. 幂等号

- 通知号：确定性生成（召回号 + 批次号 + 持有方编码）；
- 报告号 `reportNumber`：同号重放返回首次结果，同号不同内容返回 409；
- 关闭号 `closureNumber`：同号重放幂等，召回已用其他关闭号关闭时返回 409；
- 批次号、转换号、发货单号、持有方编码同样幂等。

### 6. 统计口径

- **响应率** = (初始影响量 − 尚未报告量) / 初始影响量（尚未报告含转交后下游未响应）
- **完成率（召回收回率）** = 已隔离量 / 初始影响量
- **尚未收回量** = 已消费 + 数量不符 + 尚未报告 = 初始影响量 − 已隔离

平衡恒等式（按持有方×批次汇总）：

```
初始影响量 = 已隔离 + 已消费 + 数量不符 + 尚未报告
```

`已转交` 是持有方之间的**流量**指标（每一跳记录一次，多级转交链条会逐跳累计显示），
不是终态，因此不进入上述恒等式，也不重复计入初始影响量分母；
货物转交到下一级后尚未被处置的部分体现在“尚未报告量”中。

## HTTP 接口

### 批次与谱系（既有）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/lots` | 登记批次（`lotNumber` 幂等） |
| GET | `/api/lots/{lotNumber}` | 批次库存/隔离状态/进行中的召回原因 |
| GET | `/api/lots/{lotNumber}/genealogy` | 上下游谱系 |
| POST | `/api/transformations` | 生产转换（`transformationId` 幂等，质量守恒校验） |

### 持有方与去向

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/holders` | 登记持有方（`holderCode` 幂等） |
| GET | `/api/holders/{holderCode}` | 查询持有方 |
| POST | `/api/destinations` | 登记发货去向（`destinationNumber` 幂等；隔离批次禁发） |

发货请求：

```json
{
  "destinationNumber": "DS-001",
  "lotNumber": "A",
  "holderCode": "H1",
  "quantity": 50
}
```

### 召回

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/recalls` | 发起召回，生成初始清单与通知 |
| GET | `/api/recalls/{recallNumber}` | 召回概要（状态、影响批次数、统计版本） |
| POST | `/api/recalls/{recallNumber}/reports` | 下游提交响应报告（`reportNumber` 幂等） |
| POST | `/api/recalls/{recallNumber}/close` | 满足门槛后关闭（`closureNumber` 幂等） |
| GET | `/api/recalls/{recallNumber}/impacts` | 影响批次清单（初始 + 追加） |
| GET | `/api/recalls/{recallNumber}/notifications` | 通知任务及明细 |
| GET | `/api/recalls/{recallNumber}/holder-responses` | 持有方响应汇总 |
| GET | `/api/recalls/{recallNumber}/discrepancies` | 数量差异明细 |
| GET | `/api/recalls/{recallNumber}/effectiveness` | 召回完成率（有效性）总览 |

发起召回：

```json
{
  "recallNumber": "R-1",
  "lotNumber": "A",
  "reason": "a-contamination",
  "minResponseRate": 1.0
}
```

下游报告（可多条明细、可多次提交，累计须守恒）：

```json
{
  "reportNumber": "RP-1",
  "holderCode": "H1",
  "items": [
    {"lotNumber": "A", "disposition": "QUARANTINED", "quantity": 40},
    {"lotNumber": "A", "disposition": "CONSUMED", "quantity": 10}
  ]
}
```

转交报告（必须指定接收持有方，系统为其创建追加通知）：

```json
{
  "reportNumber": "RP-2",
  "holderCode": "H1",
  "items": [
    {"lotNumber": "A", "disposition": "TRANSFERRED", "quantity": 20,
     "transferredToHolderCode": "H2"}
  ]
}
```

关闭（携带读取统计时看到的 `statisticsVersion` 作为乐观凭证）：

```json
{
  "closureNumber": "C-1",
  "approver": "qa-lead",
  "expectedVersion": 3
}
```

有效性响应示例：

```json
{
  "recallNumber": "R-1",
  "status": "OPEN",
  "minResponseRate": 1.000,
  "statisticsVersion": 2,
  "affectedLotCount": 6,
  "notifiedHolderCount": 2,
  "affectedQuantity": 60.000,
  "quarantinedQuantity": 40.000,
  "consumedQuantity": 10.000,
  "transferredQuantity": 0.000,
  "discrepancyQuantity": 0.000,
  "unreportedQuantity": 10.000,
  "unrecoveredQuantity": 20.000,
  "responseRate": 0.833,
  "completionRate": 0.667,
  "closure": null
}
```

关闭后 `closure` 字段包含关闭号、批准人、关闭依据版本及当时的未收回数量与比率。

## 错误约定

- `400 Bad Request`：参数缺失/非法（数量非正、精度超限、未知处置类型、守恒不成立的请求格式等）；
- `404 Not Found`：批次/持有方/召回等资源不存在；
- `409 Conflict`：幂等号内容冲突、库存不足、隔离批次发货、报告超量不守恒、
  未达关闭门槛、关闭依据的统计版本已过期、召回已关闭等。

## 测试

- `LotApiTests` / `TransformationApiTests` / `RecallApiTests`：批次、转换、召回隔离的既有行为；
- `RecallEffectivenessApiTests`：初始清单与通知、多路径不重复计量、报告守恒、转交追加通知、
  数量差异、关闭门槛/批准人/未收回量、版本失效、追加影响、幂等号等；
- `ConcurrencyTests` / `RecallEffectivenessConcurrencyTests`：库存超发、召回与转换竞态、
  关闭与报告/转换并发的失效语义（重复 20 次以覆盖两种调度结局）、并发报告守恒。
