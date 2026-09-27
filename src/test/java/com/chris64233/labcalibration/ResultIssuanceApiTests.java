package com.chris64233.labcalibration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 结果签发：有效校准校验、证书版本锁定、幂等。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ResultIssuanceApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.chris64233.labcalibration.repo.ReviewRecordRepository reviewRepository;
    @Autowired
    private com.chris64233.labcalibration.repo.InvalidationRecordRepository invalidationRepository;
    @Autowired
    private com.chris64233.labcalibration.repo.TestResultRepository resultRepository;
    @Autowired
    private com.chris64233.labcalibration.repo.CalibrationCertificateRepository certificateRepository;
    @Autowired
    private com.chris64233.labcalibration.repo.InstrumentRepository instrumentRepository;

    @BeforeEach
    void cleanDatabase() {
        // 测试共享同一应用上下文与内存库，按外键顺序清空保证隔离
        reviewRepository.deleteAll();
        invalidationRepository.deleteAll();
        resultRepository.deleteAll();
        certificateRepository.deleteAll();
        instrumentRepository.deleteAll();
    }

    @BeforeEach
    void setUpInstrument() throws Exception {
        mockMvc.perform(post("/api/instruments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"BAL-ISS","name":"分析天平","calibrationCycleMonths":12,
                                 "applicableTestItems":["WEIGHT"]}
                                """))
                .andExpect(status().isCreated());
        // 证书 v1：2026 上半年合格
        mockMvc.perform(post("/api/instruments/BAL-ISS/calibrations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"validFrom":"2026-01-01T00:00:00Z","validTo":"2026-06-30T23:59:59Z",
                                 "conclusion":"PASS","issuedBy":"省计量院"}
                                """))
                .andExpect(status().isCreated());
        // 证书 v2：2026 下半年合格
        mockMvc.perform(post("/api/instruments/BAL-ISS/calibrations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"validFrom":"2026-07-01T00:00:00Z","validTo":"2026-12-31T23:59:59Z",
                                 "conclusion":"PASS","issuedBy":"省计量院"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void issueLocksInstrumentAndCertificateVersion() throws Exception {
        mockMvc.perform(post("/api/results").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resultNo":"R-LOCK-1","instrumentCode":"BAL-ISS","sampleId":"S-1",
                                 "testItem":"WEIGHT","executedAt":"2026-03-10T09:00:00Z","measuredValue":"10.001g"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultNo").value("R-LOCK-1"))
                .andExpect(jsonPath("$.instrument.code").value("BAL-ISS"))
                .andExpect(jsonPath("$.certificate.version").value(1))
                .andExpect(jsonPath("$.sampleId").value("S-1"))
                .andExpect(jsonPath("$.testItem").value("WEIGHT"))
                .andExpect(jsonPath("$.executedAt").value("2026-03-10T09:00:00Z"))
                .andExpect(jsonPath("$.status").value("ISSUED"));

        // 下半年执行的结果锁定证书 v2
        mockMvc.perform(post("/api/results").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resultNo":"R-LOCK-2","instrumentCode":"BAL-ISS","sampleId":"S-2",
                                 "testItem":"WEIGHT","executedAt":"2026-09-10T09:00:00Z","measuredValue":"10.002g"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.certificate.version").value(2));
    }

    @Test
    void issueRejectedWhenNoValidCalibrationAtExecutionTime() throws Exception {
        // 执行时间落在两个证书有效区间之外
        mockMvc.perform(post("/api/results").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resultNo":"R-NOCERT","instrumentCode":"BAL-ISS","sampleId":"S-3",
                                 "testItem":"WEIGHT","executedAt":"2027-03-10T09:00:00Z","measuredValue":"10.0g"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_VALID_CALIBRATION"));
    }

    @Test
    void issueRejectedWhenCertificateConclusionIsFail() throws Exception {
        mockMvc.perform(post("/api/instruments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"BAL-FAIL","name":"故障天平","calibrationCycleMonths":12,
                                 "applicableTestItems":["WEIGHT"]}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/instruments/BAL-FAIL/calibrations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"validFrom":"2026-01-01T00:00:00Z","validTo":"2026-12-31T00:00:00Z",
                                 "conclusion":"FAIL","issuedBy":"省计量院"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/results").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resultNo":"R-FAILCERT","instrumentCode":"BAL-FAIL","sampleId":"S-4",
                                 "testItem":"WEIGHT","executedAt":"2026-06-01T09:00:00Z","measuredValue":"10.0g"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_VALID_CALIBRATION"));
    }

    @Test
    void issueRejectedWhenTestItemNotApplicable() throws Exception {
        mockMvc.perform(post("/api/results").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resultNo":"R-BADITEM","instrumentCode":"BAL-ISS","sampleId":"S-5",
                                 "testItem":"PH","executedAt":"2026-03-10T09:00:00Z","measuredValue":"7.0"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEST_ITEM_NOT_APPLICABLE"));
    }

    @Test
    void issueIsIdempotentByResultNo() throws Exception {
        String payload = """
                {"resultNo":"R-IDEM","instrumentCode":"BAL-ISS","sampleId":"S-6",
                 "testItem":"WEIGHT","executedAt":"2026-03-10T09:00:00Z","measuredValue":"10.001g"}
                """;
        String first = mockMvc.perform(post("/api/results").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/api/results").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // 幂等重放返回同一条结果（比较业务标识而非序列化文本，避免时间精度差异）
        org.assertj.core.api.Assertions.assertThat(
                        com.jayway.jsonpath.JsonPath.<Number>read(second, "$.id").longValue())
                .isEqualTo(com.jayway.jsonpath.JsonPath.<Number>read(first, "$.id").longValue());
    }

    @Test
    void sameResultNoWithDifferentPayloadConflicts() throws Exception {
        mockMvc.perform(post("/api/results").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resultNo":"R-MISMATCH","instrumentCode":"BAL-ISS","sampleId":"S-7",
                                 "testItem":"WEIGHT","executedAt":"2026-03-10T09:00:00Z","measuredValue":"10.001g"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/results").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resultNo":"R-MISMATCH","instrumentCode":"BAL-ISS","sampleId":"S-OTHER",
                                 "testItem":"WEIGHT","executedAt":"2026-03-10T09:00:00Z","measuredValue":"10.001g"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_PAYLOAD_MISMATCH"));
    }

    @Test
    void issueRejectedWhenCertificateInvalidatedAtExecutionTime() throws Exception {
        // 独立仪器与证书，避免依赖其他数据的自增 ID
        mockMvc.perform(post("/api/instruments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"BAL-INV","name":"待失效天平","calibrationCycleMonths":12,
                                 "applicableTestItems":["WEIGHT"]}
                                """))
                .andExpect(status().isCreated());
        String cert = mockMvc.perform(post("/api/instruments/BAL-INV/calibrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"validFrom":"2026-01-01T00:00:00Z","validTo":"2026-12-31T00:00:00Z",
                                 "conclusion":"PASS","issuedBy":"省计量院"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long certId = com.jayway.jsonpath.JsonPath.<Number>read(cert, "$.id").longValue();

        // 证书在 3 月失效
        mockMvc.perform(post("/api/certificates/" + certId + "/invalidations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invalidFrom":"2026-03-01T00:00:00Z","invalidTo":"2026-03-31T23:59:59Z",
                                 "reason":"标准砝码超差"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/results").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resultNo":"R-INV","instrumentCode":"BAL-INV","sampleId":"S-8",
                                 "testItem":"WEIGHT","executedAt":"2026-03-10T09:00:00Z","measuredValue":"10.0g"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_VALID_CALIBRATION"));
    }
}
