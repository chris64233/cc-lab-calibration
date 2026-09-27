package com.chris64233.labcalibration;

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
 * 仪器与校准证书：校准周期、适用项目、证书版本。
 */
@SpringBootTest
@AutoConfigureMockMvc
class InstrumentCalibrationApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createInstrumentAndQuery() throws Exception {
        mockMvc.perform(post("/api/instruments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"BAL-01","name":"电子天平","calibrationCycleMonths":12,
                                 "applicableTestItems":["WEIGHT","DENSITY"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("BAL-01"))
                .andExpect(jsonPath("$.calibrationCycleMonths").value(12))
                .andExpect(jsonPath("$.applicableTestItems", containsInAnyOrder("WEIGHT", "DENSITY")));

        mockMvc.perform(get("/api/instruments/BAL-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("电子天平"));
    }

    @Test
    void duplicateInstrumentCodeRejected() throws Exception {
        String body = """
                {"code":"BAL-DUP","name":"天平","calibrationCycleMonths":12,"applicableTestItems":["WEIGHT"]}
                """;
        mockMvc.perform(post("/api/instruments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/instruments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUMENT_CODE_DUPLICATED"));
    }

    @Test
    void calibrationCreatesIncrementingCertificateVersions() throws Exception {
        mockMvc.perform(post("/api/instruments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PH-01","name":"pH计","calibrationCycleMonths":6,"applicableTestItems":["PH"]}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/instruments/PH-01/calibrations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"validFrom":"2026-01-01T00:00:00Z","validTo":"2026-06-30T23:59:59Z",
                                 "conclusion":"PASS","issuedBy":"省计量院"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.conclusion").value("PASS"));

        mockMvc.perform(post("/api/instruments/PH-01/calibrations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"validFrom":"2026-07-01T00:00:00Z","validTo":"2026-12-31T23:59:59Z",
                                 "conclusion":"FAIL","issuedBy":"省计量院"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(get("/api/instruments/PH-01/certificates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[1].version").value(1));
    }

    @Test
    void validityRangeMustBePositiveAndWithinCycle() throws Exception {
        mockMvc.perform(post("/api/instruments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"UV-01","name":"紫外分光光度计","calibrationCycleMonths":12,"applicableTestItems":["ABSORBANCE"]}
                                """))
                .andExpect(status().isCreated());

        // 起点不早于终点
        mockMvc.perform(post("/api/instruments/UV-01/calibrations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"validFrom":"2026-06-01T00:00:00Z","validTo":"2026-01-01T00:00:00Z",
                                 "conclusion":"PASS","issuedBy":"省计量院"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VALIDITY_RANGE"));

        // 有效区间超过校准周期（12 个月）
        mockMvc.perform(post("/api/instruments/UV-01/calibrations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"validFrom":"2026-01-01T00:00:00Z","validTo":"2027-06-01T00:00:00Z",
                                 "conclusion":"PASS","issuedBy":"省计量院"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDITY_EXCEEDS_CYCLE"));
    }

    @Test
    void unknownInstrumentReturns404() throws Exception {
        mockMvc.perform(get("/api/instruments/NOPE"))
                .andExpect(status().isNotFound());
    }
}
