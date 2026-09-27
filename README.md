# cc-lab-calibration

实验室仪器、校准证书与检测资料管理服务。

覆盖仪器校准周期管理、校准证书版本、检测结果签发、校准失效影响追踪与复核处置的完整闭环。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 主要业务规则

### 仪器与校准证书

- 仪器登记校准周期（月）与适用检测项目；只有适用项目才允许用该仪器提交结果。
- 每次校准形成一个新的**证书版本**（同一仪器下版本号单调递增），证书记录有效区间
  `[validFrom, validTo]`（闭区间）与校准结论（PASS / FAIL）。
- 证书一经登记**不可修改**，系统不提供任何修改入口；证书历史版本全部保留可查。

### 结果签发

- 只有在**检测执行时刻**处于有效校准状态的仪器才能提交结果：存在覆盖执行时刻且结论为
  PASS 的证书版本，且执行时刻未落在该证书已登记的失效区间内。
- 签发时**锁定**所用仪器与证书版本，并记录样本、检测项目、执行时间、测量值。
- 结果编号（`R########`）签发成功后不再变化；客户端以 `requestId` 作为幂等键，
  数据库对其建唯一约束——重复提交或并发重试都返回首次签发的同一结果，不会产生重复结果。
- 签发与失效登记通过对证书行的悲观写锁串行化，"签发时校准有效"的判定不会被并发的失效登记穿越。

### 校准失效与影响追踪

- 发现某次校准不可信时，可对证书登记**失效区间**（含原因）。
- 登记失效在同一事务内用一条批量 UPDATE 把区间内依赖该证书的全部 `ISSUED` 结果置为
  `PENDING_REVIEW`：单条语句保证原子性不遗漏，状态前置条件保证重复登记不会重复标记。
- 失效登记返回完整影响报告（受影响结果清单 + 本次新标记数量）。

### 复核与发布

- 待复核结果只能由授权角色（`QUALITY_MANAGER`）处置，且**必须记录依据**：
  - `CONFIRM_VALID`：确认原结果仍有效，状态转为 `CONFIRMED_VALID`，可对外发布；
  - `RETEST`：按正常签发流程产生新结果（同样要求有效校准），原结果转为 `SUPERSEDED`，
    复核记录关联新结果编号。
- 原结果与原证书保持不可修改，复核只做状态机迁移并追加复核记录；每次处置形成一条
  不可修改的复核记录，构成该结果的**复核链**。
- 状态迁移用条件更新（`WHERE status = 期望值`）实现，并发复核只成功一次，败者收到 409。
- 对外发布仅允许 `ISSUED` / `CONFIRMED_VALID` 状态，通过"检查并置位"的原子更新实现：
  与失效标记、复核并发时，尚未解决的待复核结果不会被发布；重复发布是幂等操作。

## API 概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/instruments` | 注册仪器（校准周期、适用检测项目） |
| POST | `/api/instruments/{code}/certificates` | 登记校准，形成新证书版本 |
| GET | `/api/instruments/{code}/certificates` | 证书版本查询（版本号倒序） |
| GET | `/api/certificates/{id}` | 证书详情 |
| POST | `/api/certificates/{id}/invalidations` | 登记失效区间并圈定影响（返回影响报告） |
| GET | `/api/certificates/{id}/invalidations` | 失效登记历史 |
| GET | `/api/certificates/{id}/impact?from=&to=` | 影响范围查询（区间可缺省） |
| POST | `/api/results` | 签发检测结果（幂等键 `requestId`） |
| GET | `/api/results/{resultNo}` | 检测结果查询 |
| POST | `/api/results/{resultNo}/publish` | 对外发布 |
| POST | `/api/results/{resultNo}/reviews` | 复核处置（CONFIRM_VALID / RETEST） |
| GET | `/api/results/{resultNo}/reviews` | 复核链查询 |

错误响应统一为 `{timestamp, status, error, message}`；业务冲突返回 409，
未授权复核返回 403，参数校验失败返回 400。

## 测试

`src/test/java/com/chris64233/labcalibration/` 下的自动化测试覆盖：

- `ResultIssuanceTest`：有效校准校验、签发锁定证书版本、幂等键去重、8 线程并发签发只产生一个结果；
- `InvalidationImpactTest`：失效圈定不遗漏不重复、影响范围查询、待复核禁止发布；
- `ReviewFlowTest`：授权与依据校验、确认有效、重测替代与复核链、并发复核只成功一次；
- `ApiFlowTest`：REST 端到端闭环（注册 → 证书版本 → 签发 → 失效 → 复核 → 发布）。
