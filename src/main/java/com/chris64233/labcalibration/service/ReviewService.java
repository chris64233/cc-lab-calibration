package com.chris64233.labcalibration.service;

import com.chris64233.labcalibration.domain.ResultStatus;
import com.chris64233.labcalibration.domain.ReviewDecision;
import com.chris64233.labcalibration.domain.ReviewRecord;
import com.chris64233.labcalibration.domain.TestResult;
import com.chris64233.labcalibration.repo.ReviewRecordRepository;
import com.chris64233.labcalibration.repo.TestResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * 待复核结果的处置：确认有效或重测替代。
 *
 * 原结果与原证书保持不可修改——复核不改动结果本体，
 * 只做状态机迁移（条件更新保证并发下只成功一次）并追加复核记录。
 */
@Service
public class ReviewService {

    /** 具备复核授权的角色。 */
    public static final String AUTHORIZED_ROLE = "QUALITY_MANAGER";

    private final TestResultRepository testResultRepository;
    private final ReviewRecordRepository reviewRecordRepository;
    private final ResultService resultService;
    private final Clock clock;

    public ReviewService(TestResultRepository testResultRepository,
                         ReviewRecordRepository reviewRecordRepository,
                         ResultService resultService,
                         Clock clock) {
        this.testResultRepository = testResultRepository;
        this.reviewRecordRepository = reviewRecordRepository;
        this.resultService = resultService;
        this.clock = clock;
    }

    /** 授权人员确认原结果仍然有效。 */
    @Transactional
    public ReviewRecord confirmValid(String resultNo, String decidedBy, String reviewerRole, String rationale) {
        requireAuthorized(reviewerRole);
        requireRationale(rationale);
        requireDecidedBy(decidedBy);
        TestResult result = mustGet(resultNo);
        int updated = testResultRepository.transitionStatus(
                result.getId(), ResultStatus.PENDING_REVIEW, ResultStatus.CONFIRMED_VALID);
        if (updated == 0) {
            throw BusinessException.conflict(
                    "结果 " + resultNo + " 不在待复核状态（当前 " + currentStatus(resultNo) + "），可能已被其他复核处理");
        }
        return reviewRecordRepository.save(new ReviewRecord(
                result, ReviewDecision.CONFIRM_VALID, rationale, decidedBy, clock.instant(), null));
    }

    /**
     * 通过重测替代原结果：先按正常签发流程产生新结果（同样要求有效校准），
     * 再把原结果置为 SUPERSEDED 并记录复核链。整个操作在一个事务内完成。
     * 重测命令必须携带新的幂等键。
     */
    @Transactional
    public ReviewRecord retest(String resultNo, String decidedBy, String reviewerRole,
                               String rationale, IssueResultCommand retestCommand) {
        requireAuthorized(reviewerRole);
        requireRationale(rationale);
        requireDecidedBy(decidedBy);
        if (retestCommand == null) {
            throw BusinessException.badRequest("重测替代必须提供新结果的签发参数");
        }
        TestResult original = mustGet(resultNo);
        if (original.getStatus() != ResultStatus.PENDING_REVIEW) {
            throw BusinessException.conflict(
                    "结果 " + resultNo + " 不在待复核状态（当前 " + original.getStatus() + "）");
        }
        TestResult replacement = resultService.issue(retestCommand);
        int updated = testResultRepository.transitionStatus(
                original.getId(), ResultStatus.PENDING_REVIEW, ResultStatus.SUPERSEDED);
        if (updated == 0) {
            throw BusinessException.conflict(
                    "结果 " + resultNo + " 已被其他复核并发处理");
        }
        return reviewRecordRepository.save(new ReviewRecord(
                original, ReviewDecision.RETEST, rationale, decidedBy, clock.instant(),
                replacement.getResultNo()));
    }

    /** 复核链查询：按时间升序返回某结果的全部复核记录。 */
    @Transactional(readOnly = true)
    public List<ReviewRecord> reviewChain(String resultNo) {
        TestResult result = mustGet(resultNo);
        return reviewRecordRepository.findByTestResultIdOrderByDecidedAtAscIdAsc(result.getId());
    }

    private TestResult mustGet(String resultNo) {
        return testResultRepository.findByResultNo(resultNo)
                .orElseThrow(() -> BusinessException.notFound("结果不存在: " + resultNo));
    }

    private ResultStatus currentStatus(String resultNo) {
        return mustGet(resultNo).getStatus();
    }

    private void requireAuthorized(String reviewerRole) {
        if (!AUTHORIZED_ROLE.equals(reviewerRole)) {
            throw BusinessException.forbidden("复核人角色未授权，须为 " + AUTHORIZED_ROLE);
        }
    }

    private void requireRationale(String rationale) {
        if (rationale == null || rationale.isBlank()) {
            throw BusinessException.badRequest("复核决定必须记录依据");
        }
    }

    private void requireDecidedBy(String decidedBy) {
        if (decidedBy == null || decidedBy.isBlank()) {
            throw BusinessException.badRequest("复核人不能为空");
        }
    }
}
