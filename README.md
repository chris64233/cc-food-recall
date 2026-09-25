# cc-food-recall

管理原料批次、生产转换和召回隔离。

## 主要业务规则

- **批次登记**：批次号唯一，数量为 `BigDecimal` 固定精度（3 位小数）。同一批次号重复登记且数量一致时幂等返回（200），数量不一致返回 409。
- **生产转换**：一笔转换消耗一个或多个现有批次并产生一个或多个新批次。输入总量必须等于输出总量加声明损耗（质量守恒，违反返回 422）；输入库存不足、输出批次号已存在（409）或会形成环路（422）时整笔回滚，输入扣减、输出创建与谱系边在同一事务中原子完成。转换携带客户端提供的唯一 `transformationId`，内容一致重放幂等返回，内容冲突返回 409。
- **并发安全**：所有变更操作（登记、转换、召回发起/关闭）经全局互斥门串行化，并对输入批次按批次号排序加悲观写锁（`PESSIMISTIC_WRITE`），并发转换不会重复消耗同一数量；唯一约束作为最后防线。
- **召回传播**：以唯一召回事件号对任一批次发起召回，该批次及全部后代批次进入隔离，每个受影响批次记录召回原因（`recall_impacts` 按 `(recall_id, lot_id)` 唯一，多层、多父节点、汇合路径经 BFS 去重，不产生重复影响记录）。召回事件号幂等，内容冲突返回 409。
- **隔离解除**：批次隔离状态由“是否存在未关闭召回的影响记录”推导。关闭召回只移除该召回的原因效力（影响记录保留作审计），批次仍受其他未关闭召回影响时保持隔离。关闭操作幂等。
- **不逃逸保证**：召回传播遍历每个批次时持有其悲观写锁，转换创建新边前必须持有输入批次锁；转换提交前会把作用于输入批次的未关闭召回传播到新输出批次，因此召回与新转换并发时新产生的后代不会逃逸有效召回。

## API 概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/lots` | 登记批次 `{lotNumber, quantity}` |
| GET | `/api/lots/{lotNumber}` | 库存、隔离状态及生效召回原因 |
| GET | `/api/lots/{lotNumber}/recalls` | 该批次全部召回原因（含已关闭） |
| GET | `/api/lots/{lotNumber}/lineage/upstream` | 上游谱系 |
| GET | `/api/lots/{lotNumber}/lineage/downstream` | 下游谱系 |
| POST | `/api/transformations` | 生产转换 `{transformationId, inputs[], outputs[], loss}` |
| POST | `/api/recalls` | 发起召回 `{recallNumber, lotNumber, reason}` |
| POST | `/api/recalls/{recallNumber}/close` | 关闭召回（幂等） |
| GET | `/api/recalls/{recallNumber}` | 召回详情及受影响批次 |

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
