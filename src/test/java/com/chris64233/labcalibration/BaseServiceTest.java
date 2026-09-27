package com.chris64233.labcalibration;

import com.chris64233.labcalibration.domain.CalibrationCertificate;
import com.chris64233.labcalibration.domain.CalibrationConclusion;
import com.chris64233.labcalibration.domain.Instrument;
import com.chris64233.labcalibration.service.InstrumentService;
import com.chris64233.labcalibration.service.IssueResultCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
public abstract class BaseServiceTest {

    protected static final Instant T0 = Instant.parse("2026-03-01T00:00:00Z");
    protected static final Instant VALID_FROM = Instant.parse("2026-01-01T00:00:00Z");
    protected static final Instant VALID_TO = Instant.parse("2026-12-31T23:59:59Z");

    @Autowired
    protected InstrumentService instrumentService;

    // 静态计数器：JUnit 每个测试方法新建实例，但应用上下文与数据库共享，编号须全局唯一。
    private static final AtomicInteger SEQ = new AtomicInteger();

    protected String nextCode(String prefix) {
        return prefix + "-" + SEQ.incrementAndGet();
    }

    protected Instrument newInstrument(String... testItems) {
        return instrumentService.registerInstrument(
                nextCode("INS"), "测试仪器", 12, Set.of(testItems));
    }

    protected CalibrationCertificate newPassCertificate(Instrument instrument) {
        return instrumentService.registerCertificate(
                instrument.getCode(), "CERT-" + instrument.getCode() + "-1",
                VALID_FROM, VALID_TO, CalibrationConclusion.PASS);
    }

    protected IssueResultCommand issueCommand(String requestId, Instrument instrument, Instant executedAt) {
        return new IssueResultCommand(requestId, instrument.getCode(), "SAMPLE-1",
                instrument.getApplicableTestItems().iterator().next(), executedAt,
                new BigDecimal("1.234"), "mg/L");
    }
}
