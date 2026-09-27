# cc-lab-calibration

实验室仪器、校准证书与检测资料管理服务。

覆盖仪器校准周期管理、校准证书版本、检测结果签发、校准失效影响追踪与复核闭环。

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

- 仪器登记**校准周期（月）**与**适用检测项目**；仪器编号全局唯一。
- 每次校准生成一个**新的证书版本**（同一仪器下版本号单调递增），证书包含**有效区间**与**结论**（PASS/FAIL）。
- 证书有效区间必须为正，且不得超过仪器的校准周期。
- 证书一经创建**不可修改**（无更新入口）；发现不可信时通过登记失效区间处理，而不是改证书。

### 结果签发

- 只有在**检测执行时间**处于有效校准状态（存在 PASS 证书覆盖该时刻，且该证书未被登记覆盖该时刻的失效区间）的仪器才能提交结果。
- 检测项目必须在仪器的适用项目内。
- 签发时**锁定**所用仪器与校准证书版本，并记录样本、检测项目、执行时间、测量值；这些业务字段创建后不可修改，仅生命周期状态可流转。
- **结果编号为幂等键**：相同编号且载荷一致的重复请求返回原结果；编号相同但载荷不一致返回 409；并发签发由数据库唯一约束兜底，不会产生重复结果。

### 校准失效与影响追踪

- 可对某证书版本登记**失效区间**（原因必填）。
- 登记时系统在同一事务内**一次性**找出该区间内依赖该证书的全部已签发结果（含已发布、已确认有效的），统一置为**待复核**；已是待复核的不重复标记，已被替代的结果视为已解决不再触碰——不遗漏、不重复。
- 影响范围可随时通过失效登记查询。

### 复核与发布

- 待复核结果有两种处理方式，均须**授权复核人员**（请求头 `X-Role: REVIEWER`）并**记录依据**：
  - **确认仍有效**：结果转为 CONFIRMED_VALID，恢复可发布；
  - **重测替代**：以相同样本/项目/仪器签发新结果（新结果须落在其他有效证书区间内，原证书已不可信），原结果转为 REPLACED，复核记录关联新旧结果形成**复核链**。
- 原结果与原证书保持不可修改，复核只追加复核记录并流转状态。
- 发布与复核对同一结果行加**悲观写锁**：待复核或已替代的结果禁止发布，复核进行中也不会发布尚未解决的结果；重复发布幂等。

### 结果状态机

```
ISSUED ──失效登记──▶ PENDING_REVIEW ──确认有效──▶ CONFIRMED_VALID ──▶ PUBLISHED
   │                       │
   └──▶ PUBLISHED          └──重测替代──▶ REPLACED（终态，新结果另行签发）
```

## API 概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/instruments` | 登记仪器（编号、校准周期、适用项目） |
| GET | `/api/instruments/{code}` | 仪器查询 |
| POST | `/api/instruments/{code}/calibrations` | 登记校准，生成新证书版本 |
| GET | `/api/instruments/{code}/certificates` | 证书版本查询（版本倒序） |
| POST | `/api/results` | 签发结果（按结果编号幂等） |
| GET | `/api/results/{resultNo}` | 结果查询 |
| POST | `/api/results/{resultNo}/publish` | 对外发布（待复核/已替代拒绝） |
| POST | `/api/results/{resultNo}/reviews` | 复核决定（需 `X-Role: REVIEWER`） |
| GET | `/api/results/{resultNo}/reviews` | 复核链查询 |
| POST | `/api/certificates/{id}/invalidations` | 登记失效区间，返回受影响结果 |
| GET | `/api/certificates/{id}/invalidations` | 某证书的全部失效登记 |
| GET | `/api/invalidations/{id}/impact` | 影响范围查询 |

错误响应统一为 `{"code": ..., "message": ...}`，主要错误码：
`INSTRUMENT_CODE_DUPLICATED`、`INVALID_VALIDITY_RANGE`、`VALIDITY_EXCEEDS_CYCLE`、
`TEST_ITEM_NOT_APPLICABLE`、`NO_VALID_CALIBRATION`、`IDEMPOTENCY_PAYLOAD_MISMATCH`、
`INVALID_INVALIDATION_RANGE`、`RESULT_PENDING_REVIEW`、`RESULT_REPLACED`、
`RESULT_NOT_PENDING_REVIEW`、`REVIEW_BASIS_REQUIRED`、`FORBIDDEN`、`NOT_FOUND`。

## 测试

- `InstrumentCalibrationApiTests`：仪器登记、编号唯一、证书版本递增、有效区间与校准周期校验。
- `ResultIssuanceApiTests`：有效校准校验、证书版本锁定、适用项目校验、幂等重放与载荷冲突、失效区间拦截签发。
- `ResultIssuanceConcurrencyTests`：16 线程并发签发同一编号，断言只产生一条结果。
- `InvalidationReviewApiTests`：失效区间精确标记、重叠登记不重复、待复核禁发、授权与依据校验、确认有效恢复发布、重测替代与原结果不可变、影响范围与复核链查询。
