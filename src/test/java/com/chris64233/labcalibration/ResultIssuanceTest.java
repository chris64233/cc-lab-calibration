package com.chris64233.labcalibration;

import com.chris64233.labcalibration.domain.CalibrationCertificate;
import com.chris64233.labcalibration.domain.CalibrationConclusion;
import com.chris64233.labcalibration.domain.Instrument;
import com.chris64233.labcalibration.domain.ResultStatus;
import com.chris64233.labcalibration.domain.TestResult;
import com.chris64233.labcalibration.repo.TestResultRepository;
import com.chris64233.labcalibration.service.BusinessException;
import com.chris64233.labcalibration.service.InvalidationService;
import com.chris64233.labcalibration.service.IssueResultCommand;
import com.chris64233.labcalibration.service.ResultService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 规则 2/3/4：有效校准校验、签发锁定证书版本、结果编号幂等与并发去重。 */
class ResultIssuanceTest extends BaseServiceTest {

    @Autowired
    private ResultService resultService;
    @Autowired
    private InvalidationService invalidationService;
    @Autowired
    private TestResultRepository testResultRepository;

    @Test
    void issueLocksInstrumentAndCertificateVersionAndRecordsExecutionContext() {
        Instrument instrument = newInstrument("pH值");
        CalibrationCertificate cert = newPassCertificate(instrument);

        TestResult result = resultService.issue(
                issueCommand("req-issue-1", instrument, Instant.parse("2026-03-10T08:00:00Z")));

        assertNotNull(result.getResultNo());
        assertTrue(result.getResultNo().matches("R\\d{8}"));
        assertEquals(instrument.getId(), result.getInstrument().getId());
        assertEquals(cert.getId(), result.getCertificate().getId());
        assertEquals(cert.getVersion(), result.getCertificate().getVersion());
        assertEquals(ResultStatus.ISSUED, result.getStatus());
        assertFalse(result.isPublished());
    }

    @Test
    void rejectWhenNoValidCertificateAtExecutionTime() {
        Instrument instrument = newInstrument("pH值");
        newPassCertificate(instrument); // 有效区间 2026-01-01 ~ 2026-12-31

        IssueResultCommand before = issueCommand("req-before", instrument,
                Instant.parse("2025-12-31T23:59:59Z"));
        BusinessException e = assertThrows(BusinessException.class, () -> resultService.issue(before));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test
    void rejectWhenCalibrationConclusionIsFail() {
        Instrument instrument = newInstrument("pH值");
        instrumentService.registerCertificate(instrument.getCode(), "CERT-FAIL",
                VALID_FROM, VALID_TO, CalibrationConclusion.FAIL);

        BusinessException e = assertThrows(BusinessException.class,
                () -> resultService.issue(issueCommand("req-fail", instrument, T0)));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test
    void rejectWhenInstrumentNotApplicableToTestItem() {
        Instrument instrument = newInstrument("pH值");
        newPassCertificate(instrument);
        IssueResultCommand wrong = new IssueResultCommand("req-wrong-item", instrument.getCode(),
                "S1", "重金属", T0, new BigDecimal("1"), "mg/L");

        BusinessException e = assertThrows(BusinessException.class, () -> resultService.issue(wrong));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void sameRequestIdIsIdempotentAndReturnsSameResultNo() {
        Instrument instrument = newInstrument("pH值");
        newPassCertificate(instrument);
        IssueResultCommand cmd = issueCommand("req-idem-seq", instrument, T0);

        TestResult first = resultService.issue(cmd);
        TestResult second = resultService.issue(cmd);

        assertEquals(first.getResultNo(), second.getResultNo());
        assertEquals(1, testResultRepository.findAll().stream()
                .filter(r -> r.getRequestId().equals("req-idem-seq")).count());
    }

    @Test
    void concurrentIssuesWithSameRequestIdProduceExactlyOneResult() throws Exception {
        Instrument instrument = newInstrument("pH值");
        newPassCertificate(instrument);
        String requestId = "req-concurrent-" + UUID.randomUUID();

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<String> resultNos = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicInteger failures = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    TestResult r = resultService.issue(issueCommand(requestId, instrument, T0));
                    resultNos.add(r.getResultNo());
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(0, failures.get(), "并发签发不应抛出异常（败者应回退到已存在结果）");
        assertEquals(threads, resultNos.size());
        assertEquals(1, resultNos.stream().distinct().count(), "全部请求必须返回同一个结果编号");
        assertEquals(1, testResultRepository.findAll().stream()
                .filter(r -> r.getRequestId().equals(requestId)).count());
    }

    @Test
    void rejectIssuanceAfterCertificateInvalidationCoversExecutionTime() {
        Instrument instrument = newInstrument("pH值");
        CalibrationCertificate cert = instrumentService.registerCertificate(instrument.getCode(), "CERT-1",
                VALID_FROM, VALID_TO, CalibrationConclusion.PASS);

        // 登记失效区间覆盖 2026-03 月
        invalidationService.registerInvalidation(cert.getId(),
                Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-03-31T23:59:59Z"),
                "标准物质溯源链断裂");

        // 失效区间内的执行时刻 → 拒绝提交
        IssueResultCommand inside = issueCommand("req-inside-invalidation", instrument,
                Instant.parse("2026-03-10T08:00:00Z"));
        BusinessException e = assertThrows(BusinessException.class, () -> resultService.issue(inside));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());

        // 失效区间外、证书仍有效的执行时刻 → 允许提交
        TestResult outside = resultService.issue(issueCommand("req-outside-invalidation", instrument,
                Instant.parse("2026-05-10T08:00:00Z")));
        assertEquals(ResultStatus.ISSUED, outside.getStatus());
    }
}
