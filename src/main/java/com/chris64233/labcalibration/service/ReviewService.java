package com.chris64233.labcalibration.service;

import com.chris64233.labcalibration.domain.ResultStatus;
import com.chris64233.labcalibration.domain.ReviewDecision;
import com.chris64233.labcalibration.domain.ReviewRecord;
import com.chris64233.labcalibration.domain.TestResult;
import com.chris64233.labcalibration.repo.ReviewRecordRepository;
import com.chris64233.labcalibration.repo.TestResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 待复核结果处理：确认仍有效或重测替代，形成复核链。
 */
@Service
public class ReviewService {

    /** 授权复核角色（通过请求头 X-Role 传入）。 */
    public static final String REVIEWER_ROLE = "REVIEWER";

    private final TestResultRepository resultRepository;
    private final ReviewRecordRepository reviewRepository;
    private final ResultIssuanceTx issuanceTx;

    public ReviewService(TestResultRepository resultRepository,
                         ReviewRecordRepository reviewRepository,
                         ResultIssuanceTx issuanceTx) {
        this.resultRepository = resultRepository;
        this.reviewRepository = reviewRepository;
        this.issuanceTx = issuanceTx;
    }

    /**
     * 复核决定。与发布共用结果行悲观写锁，保证复核与发布互斥。
     * 原结果与原证书不被修改，仅追加复核记录并流转状态。
     *
     * @param retest 重测替代时的新结果参数（结果编号、执行时间、测量值），样本/项目/仪器继承原结果
     */
    @Transactional
    public ReviewRecord review(String resultNo, ReviewDecision decision, String basis, String decidedBy,
                               String role, RetestCommand retest) {
        if (!REVIEWER_ROLE.equals(role)) {
            throw BusinessException.forbidden("仅授权复核人员（角色 " + REVIEWER_ROLE + "）可执行复核");
        }
        if (basis == null || basis.isBlank()) {
            throw BusinessException.badRequest("REVIEW_BASIS_REQUIRED", "复核决定必须记录依据");
        }
        TestResult original = resultRepository.findByResultNoForUpdate(resultNo)
                .orElseThrow(() -> BusinessException.notFound("检测结果不存在: " + resultNo));
        if (original.getStatus() != ResultStatus.PENDING_REVIEW) {
            throw BusinessException.conflict("RESULT_NOT_PENDING_REVIEW",
                    "结果 " + resultNo + " 当前状态为 " + original.getStatus() + "，不属于待复核");
        }

        return switch (decision) {
            case CONFIRM_VALID -> confirmValid(original, basis, decidedBy);
            case RETEST_REPLACE -> retestReplace(original, basis, decidedBy, retest);
        };
    }

    private ReviewRecord confirmValid(TestResult original, String basis, String decidedBy) {
        original.confirmValid();
        resultRepository.save(original);
        return reviewRepository.save(new ReviewRecord(original, ReviewDecision.CONFIRM_VALID,
                basis, decidedBy, Instant.now(), null));
    }

    private ReviewRecord retestReplace(TestResult original, String basis, String decidedBy,
                                       RetestCommand retest) {
        if (retest == null) {
            throw BusinessException.badRequest("RETEST_RESULT_REQUIRED", "重测替代必须提供新结果参数");
        }
        // 重测结果需满足与正常签发相同的有效校准校验（原证书已不可信，须落在其他有效证书区间内）
        TestResult replacement = issuanceTx.issueNew(new IssueResultCommand(
                retest.resultNo(), original.getInstrument().getCode(), original.getSampleId(),
                original.getTestItem(), retest.executedAt(), retest.measuredValue()));
        original.markReplaced();
        resultRepository.save(original);
        return reviewRepository.save(new ReviewRecord(original, ReviewDecision.RETEST_REPLACE,
                basis, decidedBy, Instant.now(), replacement));
    }

    @Transactional(readOnly = true)
    public List<ReviewRecord> reviewChain(String resultNo) {
        TestResult result = resultRepository.findByResultNo(resultNo)
                .orElseThrow(() -> BusinessException.notFound("检测结果不存在: " + resultNo));
        return reviewRepository.findByOriginalResultIdOrderByDecidedAtAsc(result.getId());
    }

    /**
     * 重测替代的新结果参数。
     */
    public record RetestCommand(String resultNo, Instant executedAt, String measuredValue) {
    }
}
