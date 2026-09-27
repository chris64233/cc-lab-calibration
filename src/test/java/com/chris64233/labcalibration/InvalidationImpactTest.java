package com.chris64233.labcalibration;

import com.chris64233.labcalibration.domain.CalibrationCertificate;
import com.chris64233.labcalibration.domain.Instrument;
import com.chris64233.labcalibration.domain.ResultStatus;
import com.chris64233.labcalibration.domain.TestResult;
import com.chris64233.labcalibration.service.BusinessException;
import com.chris64233.labcalibration.service.InvalidationService;
import com.chris64233.labcalibration.service.ResultService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 规则 5：失效登记一次性圈定影响范围，不遗漏、不重复标记；规则 6 的发布守卫。 */
class InvalidationImpactTest extends BaseServiceTest {

    @Autowired
    private InvalidationService invalidationService;
    @Autowired
    private ResultService resultService;

    @Test
    void invalidationMarksExactlyResultsWithinInterval() {
        Instrument instrument = newInstrument("pH值");
        CalibrationCertificate cert = newPassCertificate(instrument);

        TestResult inside1 = resultService.issue(issueCommand("inv-in-1", instrument,
                Instant.parse("2026-03-05T08:00:00Z")));
        TestResult inside2 = resultService.issue(issueCommand("inv-in-2", instrument,
                Instant.parse("2026-03-20T08:00:00Z")));
        TestResult outside = resultService.issue(issueCommand("inv-out", instrument,
                Instant.parse("2026-06-01T08:00:00Z")));

        InvalidationService.ImpactReport report = invalidationService.registerInvalidation(
                cert.getId(), Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-03-31T23:59:59Z"), "校准环境温控失效");

        assertEquals(2, report.newlyMarked());
        assertEquals(2, report.impactedResults().size());
        assertEquals(ResultStatus.PENDING_REVIEW,
                resultService.getByResultNo(inside1.getResultNo()).getStatus());
        assertEquals(ResultStatus.PENDING_REVIEW,
                resultService.getByResultNo(inside2.getResultNo()).getStatus());
        assertEquals(ResultStatus.ISSUED,
                resultService.getByResultNo(outside.getResultNo()).getStatus(), "区间外结果不受影响");
    }

    @Test
    void repeatedInvalidationDoesNotDoubleMark() {
        Instrument instrument = newInstrument("pH值");
        CalibrationCertificate cert = newPassCertificate(instrument);
        TestResult r = resultService.issue(issueCommand("inv-dup", instrument,
                Instant.parse("2026-03-05T08:00:00Z")));

        invalidationService.registerInvalidation(cert.getId(),
                Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-03-31T23:59:59Z"), "原因一");
        InvalidationService.ImpactReport second = invalidationService.registerInvalidation(cert.getId(),
                Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-03-31T23:59:59Z"), "原因二");

        assertEquals(0, second.newlyMarked(), "已处于待复核的结果不得重复标记");
        assertEquals(1, second.impactedResults().size());
        assertEquals(ResultStatus.PENDING_REVIEW,
                resultService.getByResultNo(r.getResultNo()).getStatus());
    }

    @Test
    void impactQueryCoversAllStatusesInInterval() {
        Instrument instrument = newInstrument("pH值");
        CalibrationCertificate cert = newPassCertificate(instrument);
        resultService.issue(issueCommand("impact-1", instrument, Instant.parse("2026-03-05T08:00:00Z")));
        resultService.issue(issueCommand("impact-2", instrument, Instant.parse("2026-07-05T08:00:00Z")));

        invalidationService.registerInvalidation(cert.getId(),
                Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-03-31T23:59:59Z"), "原因");

        List<TestResult> march = invalidationService.impactedResults(cert.getId(),
                Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-03-31T23:59:59Z"));
        assertEquals(1, march.size());

        List<TestResult> all = invalidationService.impactedResults(cert.getId(), null, null);
        assertEquals(2, all.size());
    }

    @Test
    void pendingReviewResultCannotBePublished() {
        Instrument instrument = newInstrument("pH值");
        CalibrationCertificate cert = newPassCertificate(instrument);
        TestResult r = resultService.issue(issueCommand("pub-guard", instrument,
                Instant.parse("2026-03-05T08:00:00Z")));

        invalidationService.registerInvalidation(cert.getId(),
                Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-03-31T23:59:59Z"), "原因");

        BusinessException e = assertThrows(BusinessException.class,
                () -> resultService.publish(r.getResultNo()));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertTrue(e.getMessage().contains("禁止对外发布"));
    }

    @Test
    void issuedResultCanBePublishedAndPublishIsIdempotent() {
        Instrument instrument = newInstrument("pH值");
        newPassCertificate(instrument);
        TestResult r = resultService.issue(issueCommand("pub-ok", instrument,
                Instant.parse("2026-03-05T08:00:00Z")));

        TestResult published = resultService.publish(r.getResultNo());
        assertTrue(published.isPublished());
        TestResult again = resultService.publish(r.getResultNo());
        assertTrue(again.isPublished());
    }
}
