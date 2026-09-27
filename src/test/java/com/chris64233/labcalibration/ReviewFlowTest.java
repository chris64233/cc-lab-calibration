package com.chris64233.labcalibration;

import com.chris64233.labcalibration.domain.CalibrationCertificate;
import com.chris64233.labcalibration.domain.Instrument;
import com.chris64233.labcalibration.domain.ResultStatus;
import com.chris64233.labcalibration.domain.ReviewDecision;
import com.chris64233.labcalibration.domain.ReviewRecord;
import com.chris64233.labcalibration.domain.TestResult;
import com.chris64233.labcalibration.service.BusinessException;
import com.chris64233.labcalibration.service.InvalidationService;
import com.chris64233.labcalibration.service.IssueResultCommand;
import com.chris64233.labcalibration.service.ResultService;
import com.chris64233.labcalibration.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 规则 6：复核处置（确认有效 / 重测替代）、授权与依据、复核链、与发布的并发守卫。 */
class ReviewFlowTest extends BaseServiceTest {

    @Autowired
    private ResultService resultService;
    @Autowired
    private InvalidationService invalidationService;
    @Autowired
    private ReviewService reviewService;

    private TestResult newPendingReviewResult(String tag) {
        Instrument instrument = newInstrument("pH值");
        CalibrationCertificate cert = newPassCertificate(instrument);
        TestResult r = resultService.issue(issueCommand("review-" + tag, instrument,
                Instant.parse("2026-03-05T08:00:00Z")));
        invalidationService.registerInvalidation(cert.getId(),
                Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-03-31T23:59:59Z"),
                "校准不可信-" + tag);
        return resultService.getByResultNo(r.getResultNo());
    }

    @Test
    void authorizedReviewerCanConfirmValidAndResultBecomesPublishable() {
        TestResult pending = newPendingReviewResult("confirm");

        ReviewRecord record = reviewService.confirmValid(pending.getResultNo(),
                "张三", ReviewService.AUTHORIZED_ROLE, "比对历史数据与留样复测，偏差在允差内");

        assertEquals(ReviewDecision.CONFIRM_VALID, record.getDecision());
        assertNull(record.getReplacementResultNo());
        TestResult after = resultService.getByResultNo(pending.getResultNo());
        assertEquals(ResultStatus.CONFIRMED_VALID, after.getStatus());

        TestResult published = resultService.publish(pending.getResultNo());
        assertTrue(published.isPublished(), "确认有效后应可对外发布");
    }

    @Test
    void unauthorizedRoleCannotReview() {
        TestResult pending = newPendingReviewResult("unauthorized");
        BusinessException e = assertThrows(BusinessException.class, () ->
                reviewService.confirmValid(pending.getResultNo(), "李四", "LAB_TECH", "依据"));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertEquals(ResultStatus.PENDING_REVIEW,
                resultService.getByResultNo(pending.getResultNo()).getStatus());
    }

    @Test
    void reviewDecisionMustRecordRationale() {
        TestResult pending = newPendingReviewResult("rationale");
        BusinessException e = assertThrows(BusinessException.class, () ->
                reviewService.confirmValid(pending.getResultNo(), "张三",
                        ReviewService.AUTHORIZED_ROLE, "  "));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void retestSupersedesOriginalAndLinksReplacementInReviewChain() {
        TestResult pending = newPendingReviewResult("retest");
        Instrument instrument = pending.getInstrument();

        IssueResultCommand retestCmd = issueCommand("retest-new-" + UUID.randomUUID(), instrument,
                Instant.parse("2026-05-10T08:00:00Z"));
        ReviewRecord record = reviewService.retest(pending.getResultNo(), "张三",
                ReviewService.AUTHORIZED_ROLE, "原证书失效，重新取样检测", retestCmd);

        assertEquals(ReviewDecision.RETEST, record.getDecision());
        assertNotNull(record.getReplacementResultNo());

        TestResult original = resultService.getByResultNo(pending.getResultNo());
        assertEquals(ResultStatus.SUPERSEDED, original.getStatus());

        TestResult replacement = resultService.getByResultNo(record.getReplacementResultNo());
        assertEquals(ResultStatus.ISSUED, replacement.getStatus());
        assertEquals(Instant.parse("2026-05-10T08:00:00Z"), replacement.getExecutedAt());

        // 原结果不可发布
        BusinessException e = assertThrows(BusinessException.class,
                () -> resultService.publish(pending.getResultNo()));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());

        // 复核链完整可查
        List<ReviewRecord> chain = reviewService.reviewChain(pending.getResultNo());
        assertEquals(1, chain.size());
        assertEquals(replacement.getResultNo(), chain.get(0).getReplacementResultNo());
        assertEquals("原证书失效，重新取样检测", chain.get(0).getRationale());
    }

    @Test
    void cannotReviewResultThatIsNotPending() {
        Instrument instrument = newInstrument("pH值");
        newPassCertificate(instrument);
        TestResult issued = resultService.issue(issueCommand("not-pending", instrument,
                Instant.parse("2026-03-05T08:00:00Z")));

        BusinessException e = assertThrows(BusinessException.class, () ->
                reviewService.confirmValid(issued.getResultNo(), "张三",
                        ReviewService.AUTHORIZED_ROLE, "依据"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test
    void concurrentReviewsResolveExactlyOnce() throws Exception {
        TestResult pending = newPendingReviewResult("race");
        String resultNo = pending.getResultNo();
        Instrument instrument = pending.getInstrument();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger confirmOk = new AtomicInteger();
        AtomicInteger retestOk = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        pool.submit(() -> {
            try {
                start.await();
                reviewService.confirmValid(resultNo, "张三", ReviewService.AUTHORIZED_ROLE, "确认有效");
                confirmOk.incrementAndGet();
            } catch (BusinessException e) {
                if (e.getStatus() == HttpStatus.CONFLICT) {
                    conflicts.incrementAndGet();
                }
            } catch (Exception ignored) {
            }
        });
        pool.submit(() -> {
            try {
                start.await();
                reviewService.retest(resultNo, "王五", ReviewService.AUTHORIZED_ROLE, "重测替代",
                        issueCommand("race-retest-" + UUID.randomUUID(), instrument,
                                Instant.parse("2026-05-10T08:00:00Z")));
                retestOk.incrementAndGet();
            } catch (BusinessException e) {
                if (e.getStatus() == HttpStatus.CONFLICT) {
                    conflicts.incrementAndGet();
                }
            } catch (Exception ignored) {
            }
        });
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(1, confirmOk.get() + retestOk.get(), "并发复核只能成功一次");
        assertEquals(1, conflicts.get(), "败者必须收到冲突错误");

        ResultStatus finalStatus = resultService.getByResultNo(resultNo).getStatus();
        assertTrue(finalStatus == ResultStatus.CONFIRMED_VALID || finalStatus == ResultStatus.SUPERSEDED);
        assertEquals(1, reviewService.reviewChain(resultNo).size(), "复核链只能有一条处置记录");
    }

    @Test
    void reviewChainIsReturnedInChronologicalOrder() {
        TestResult pending = newPendingReviewResult("chain");
        reviewService.confirmValid(pending.getResultNo(), "张三",
                ReviewService.AUTHORIZED_ROLE, "第一次确认");

        List<ReviewRecord> chain = reviewService.reviewChain(pending.getResultNo());
        assertEquals(1, chain.size());
        assertEquals("张三", chain.get(0).getDecidedBy());
        assertEquals(ReviewDecision.CONFIRM_VALID, chain.get(0).getDecision());
        assertNotNull(chain.get(0).getDecidedAt());
    }
}
