package com.chris64233.labcalibration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 规则 1/7：证书版本、检测结果、影响范围与复核链的 REST 查询，以及完整业务闭环。 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiFlowTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void fullLifecycleOverHttp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String instrumentCode = "INS-API-" + suffix;

        // 1. 注册仪器（校准周期 12 个月，适用 pH值）
        mvc.perform(post("/api/instruments").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "酸度计", "calibrationCycleMonths": 12,
                 "applicableTestItems": ["pH值"]}
                """.formatted(instrumentCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is(instrumentCode)));

        // 2. 登记两个校准证书版本
        mvc.perform(post("/api/instruments/{code}/certificates", instrumentCode)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                {"certificateNo": "CERT-A", "validFrom": "2025-01-01T00:00:00Z",
                 "validTo": "2025-12-31T23:59:59Z", "conclusion": "PASS"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version", is(1)));
        MvcResult cert2 = mvc.perform(post("/api/instruments/{code}/certificates", instrumentCode)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                {"certificateNo": "CERT-B", "validFrom": "2026-01-01T00:00:00Z",
                 "validTo": "2026-12-31T23:59:59Z", "conclusion": "PASS"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version", is(2)))
                .andReturn();
        long certId = Long.parseLong(
                com.jayway.jsonpath.JsonPath.read(cert2.getResponse().getContentAsString(), "$.id").toString());

        // 证书版本查询
        mvc.perform(get("/api/instruments/{code}/certificates", instrumentCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].version", is(2)));

        // 3. 签发结果（锁定证书 v2）
        String requestId = "req-api-" + suffix;
        MvcResult issued = mvc.perform(post("/api/results").contentType(MediaType.APPLICATION_JSON).content("""
                {"requestId": "%s", "instrumentCode": "%s", "sampleId": "SMP-1",
                 "testItem": "pH值", "executedAt": "2026-03-10T08:00:00Z",
                 "measurement": 7.02, "unit": "pH"}
                """.formatted(requestId, instrumentCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.certificateVersion", is(2)))
                .andExpect(jsonPath("$.status", is("ISSUED")))
                .andReturn();
        String resultNo = com.jayway.jsonpath.JsonPath.read(
                issued.getResponse().getContentAsString(), "$.resultNo");

        // 幂等重试 → 同一结果编号
        mvc.perform(post("/api/results").contentType(MediaType.APPLICATION_JSON).content("""
                {"requestId": "%s", "instrumentCode": "%s", "sampleId": "SMP-1",
                 "testItem": "pH值", "executedAt": "2026-03-10T08:00:00Z",
                 "measurement": 7.02, "unit": "pH"}
                """.formatted(requestId, instrumentCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultNo", is(resultNo)));

        // 4. 登记失效 → 结果被置为待复核
        mvc.perform(post("/api/certificates/{id}/invalidations", certId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                {"invalidFrom": "2026-03-01T00:00:00Z", "invalidTo": "2026-03-31T23:59:59Z",
                 "reason": "标准缓冲液过期"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newlyMarked", is(1)))
                .andExpect(jsonPath("$.impactedResults", hasSize(1)))
                .andExpect(jsonPath("$.impactedResults[0].resultNo", is(resultNo)))
                .andExpect(jsonPath("$.impactedResults[0].status", is("PENDING_REVIEW")));

        // 影响范围查询
        mvc.perform(get("/api/certificates/{id}/impact", certId)
                        .param("from", "2026-03-01T00:00:00Z")
                        .param("to", "2026-03-31T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // 5. 待复核结果禁止发布
        mvc.perform(post("/api/results/{resultNo}/publish", resultNo))
                .andExpect(status().isConflict());

        // 未授权角色复核 → 403
        mvc.perform(post("/api/results/{resultNo}/reviews", resultNo)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                {"decision": "CONFIRM_VALID", "decidedBy": "李四",
                 "reviewerRole": "LAB_TECH", "rationale": "依据"}
                """))
                .andExpect(status().isForbidden());

        // 6. 授权人员确认有效
        mvc.perform(post("/api/results/{resultNo}/reviews", resultNo)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                {"decision": "CONFIRM_VALID", "decidedBy": "张三",
                 "reviewerRole": "QUALITY_MANAGER", "rationale": "留样复测偏差在允差内"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision", is("CONFIRM_VALID")));

        // 复核链查询
        mvc.perform(get("/api/results/{resultNo}/reviews", resultNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].rationale", is("留样复测偏差在允差内")));

        // 7. 确认有效后可发布
        mvc.perform(post("/api/results/{resultNo}/publish", resultNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published", is(true)));

        // 结果查询
        mvc.perform(get("/api/results/{resultNo}", resultNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED_VALID")))
                .andExpect(jsonPath("$.sampleId", is("SMP-1")))
                .andExpect(jsonPath("$.testItem", is("pH值")));
    }

    @Test
    void issueWithoutValidCalibrationReturns409() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String instrumentCode = "INS-NOCAL-" + suffix;
        mvc.perform(post("/api/instruments").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "未校准仪器", "calibrationCycleMonths": 12,
                 "applicableTestItems": ["pH值"]}
                """.formatted(instrumentCode)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/results").contentType(MediaType.APPLICATION_JSON).content("""
                {"requestId": "req-nocal-%s", "instrumentCode": "%s", "sampleId": "S1",
                 "testItem": "pH值", "executedAt": "2026-03-10T08:00:00Z", "measurement": 7.0}
                """.formatted(suffix, instrumentCode)))
                .andExpect(status().isConflict());
    }
}
