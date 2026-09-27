package com.chris64233.labcalibration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 校准失效影响追踪与复核：一次性标记、发布互斥、确认有效、重测替代、复核链。
 */
@SpringBootTest
@AutoConfigureMockMvc
class InvalidationReviewApiTests {

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

    private long certV1Id;

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
    void setUp() throws Exception {
        mockMvc.perform(post("/api/instruments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"BAL-REV","name":"复核天平","calibrationCycleMonths":12,
                                 "applicableTestItems":["WEIGHT"]}
                                """))
                .andExpect(status().isCreated());
        // v1：2026 上半年；v2：2026 下半年（供重测使用）
        String v1 = mockMvc.perform(post("/api/instruments/BAL-REV/calibrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"validFrom":"2026-01-01T00:00:00Z","validTo":"2026-06-30T23:59:59Z",
                                 "conclusion":"PASS","issuedBy":"省计量院"}
                                """))
                .andReturn().getResponse().getContentAsString();
        certV1Id = JsonPath.<Number>read(v1, "$.id").longValue();
        mockMvc.perform(post("/api/instruments/BAL-REV/calibrations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"validFrom":"2026-07-01T00:00:00Z","validTo":"2026-12-31T23:59:59Z",
                                 "conclusion":"PASS","issuedBy":"省计量院"}
                                """))
                .andExpect(status().isCreated());

        issue("R-REV-1", "2026-02-10T09:00:00Z");   // 失效区间之前
        issue("R-REV-2", "2026-05-10T09:00:00Z");   // 失效区间之内
        issue("R-REV-3", "2026-06-10T09:00:00Z");   // 失效区间之内
    }

    private void issue(String resultNo, String executedAt) throws Exception {
        mockMvc.perform(post("/api/results").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resultNo":"%s","instrumentCode":"BAL-REV","sampleId":"S-%s",
                                 "testItem":"WEIGHT","executedAt":"%s","measuredValue":"10.0g"}
                                """.formatted(resultNo, resultNo, executedAt)))
                .andExpect(status().isCreated());
    }

    private String registerInvalidation() throws Exception {
        return mockMvc.perform(post("/api/certificates/" + certV1Id + "/invalidations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invalidFrom":"2026-04-01T00:00:00Z","invalidTo":"2026-06-30T23:59:59Z",
                                 "reason":"期间核查发现示值漂移"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void invalidationMarksExactlyTheResultsInRange() throws Exception {
        String body = registerInvalidation();
        // 一次性找出区间内的 R-REV-2、R-REV-3，区间外的 R-REV-1 不受影响
        org.assertj.core.api.Assertions.assertThat(
                        JsonPath.<java.util.List<String>>read(body, "$.affectedResults[*].resultNo"))
                .containsExactlyInAnyOrder("R-REV-2", "R-REV-3");

        mockMvc.perform(get("/api/results/R-REV-1"))
                .andExpect(jsonPath("$.status").value("ISSUED"));
        mockMvc.perform(get("/api/results/R-REV-2"))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        mockMvc.perform(get("/api/results/R-REV-3"))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    @Test
    void overlappingInvalidationDoesNotDuplicateMarking() throws Exception {
        registerInvalidation();
        // 再次登记重叠失效区间：已待复核的结果不重复标记、不遗漏
        String second = mockMvc.perform(post("/api/certificates/" + certV1Id + "/invalidations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invalidFrom":"2026-05-01T00:00:00Z","invalidTo":"2026-07-15T00:00:00Z",
                                 "reason":"复核确认漂移持续"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(
                        JsonPath.<java.util.List<String>>read(second, "$.affectedResults[*].resultNo"))
                .containsExactlyInAnyOrder("R-REV-2", "R-REV-3");
        mockMvc.perform(get("/api/results/R-REV-2"))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    @Test
    void pendingReviewResultCannotBePublished() throws Exception {
        registerInvalidation();
        mockMvc.perform(post("/api/results/R-REV-2/publish"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULT_PENDING_REVIEW"));
    }

    @Test
    void publishSucceedsForUnaffectedResult() throws Exception {
        registerInvalidation();
        mockMvc.perform(post("/api/results/R-REV-1/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        // 重复发布幂等
        mockMvc.perform(post("/api/results/R-REV-1/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void confirmValidRequiresAuthorizedReviewerAndBasis() throws Exception {
        registerInvalidation();
        // 无角色
        mockMvc.perform(post("/api/results/R-REV-2/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"CONFIRM_VALID","basis":"期间核查合格","decidedBy":"张工"}
                                """))
                .andExpect(status().isForbidden());
        // 非授权角色
        mockMvc.perform(post("/api/results/R-REV-2/reviews")
                        .header("X-Role", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"CONFIRM_VALID","basis":"期间核查合格","decidedBy":"张工"}
                                """))
                .andExpect(status().isForbidden());
        // 缺依据
        mockMvc.perform(post("/api/results/R-REV-2/reviews")
                        .header("X-Role", "REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"CONFIRM_VALID","basis":"","decidedBy":"张工"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmValidRestoresPublishabilityAndRecordsChain() throws Exception {
        registerInvalidation();
        mockMvc.perform(post("/api/results/R-REV-2/reviews")
                        .header("X-Role", "REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"CONFIRM_VALID","basis":"留样复测偏差在允差内","decidedBy":"张工"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("CONFIRM_VALID"))
                .andExpect(jsonPath("$.basis").value("留样复测偏差在允差内"))
                .andExpect(jsonPath("$.decidedBy").value("张工"));

        mockMvc.perform(get("/api/results/R-REV-2"))
                .andExpect(jsonPath("$.status").value("CONFIRMED_VALID"));
        // 确认有效后可发布
        mockMvc.perform(post("/api/results/R-REV-2/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        // 已解决的结果不能再复核
        mockMvc.perform(post("/api/results/R-REV-2/reviews")
                        .header("X-Role", "REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"CONFIRM_VALID","basis":"重复复核","decidedBy":"张工"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULT_NOT_PENDING_REVIEW"));
        // 复核链可查
        mockMvc.perform(get("/api/results/R-REV-2/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].decision").value("CONFIRM_VALID"));
    }

    @Test
    void retestReplaceCreatesNewResultAndKeepsOriginalImmutable() throws Exception {
        registerInvalidation();
        // 重测时间落在证书 v2 有效区间内
        mockMvc.perform(post("/api/results/R-REV-3/reviews")
                        .header("X-Role", "REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"RETEST_REPLACE","basis":"使用新校准证书重新检测","decidedBy":"李工",
                                 "retest":{"resultNo":"R-REV-3-R","executedAt":"2026-08-10T09:00:00Z",
                                           "measuredValue":"10.005g"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("RETEST_REPLACE"))
                .andExpect(jsonPath("$.replacementResult.resultNo").value("R-REV-3-R"));

        // 原结果保持不可修改：业务字段不变，仅状态流转为 REPLACED，且禁止发布
        mockMvc.perform(get("/api/results/R-REV-3"))
                .andExpect(jsonPath("$.status").value("REPLACED"))
                .andExpect(jsonPath("$.measuredValue").value("10.0g"))
                .andExpect(jsonPath("$.certificate.version").value(1))
                .andExpect(jsonPath("$.executedAt").value("2026-06-10T09:00:00Z"));
        mockMvc.perform(post("/api/results/R-REV-3/publish"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULT_REPLACED"));

        // 新结果继承样本/项目/仪器，锁定新证书版本，可正常发布
        mockMvc.perform(get("/api/results/R-REV-3-R"))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.sampleId").value("S-R-REV-3"))
                .andExpect(jsonPath("$.testItem").value("WEIGHT"))
                .andExpect(jsonPath("$.certificate.version").value(2))
                .andExpect(jsonPath("$.measuredValue").value("10.005g"));
        mockMvc.perform(post("/api/results/R-REV-3-R/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // 复核链包含替代关系
        mockMvc.perform(get("/api/results/R-REV-3/reviews"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].decision").value("RETEST_REPLACE"))
                .andExpect(jsonPath("$[0].replacementResult.resultNo").value("R-REV-3-R"));
    }

    @Test
    void impactQueryReturnsAffectedResults() throws Exception {
        String body = registerInvalidation();
        long invalidationId = JsonPath.<Number>read(body, "$.invalidation.id").longValue();

        mockMvc.perform(get("/api/invalidations/" + invalidationId + "/impact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invalidation.reason").value("期间核查发现示值漂移"))
                .andExpect(jsonPath("$.affectedResults[*].resultNo",
                        containsInAnyOrder("R-REV-2", "R-REV-3")))
                .andExpect(jsonPath("$.affectedResults[*].status",
                        containsInAnyOrder("PENDING_REVIEW", "PENDING_REVIEW")));
    }
}
