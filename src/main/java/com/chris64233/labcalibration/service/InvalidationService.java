package com.chris64233.labcalibration.service;

import com.chris64233.labcalibration.domain.CalibrationCertificate;
import com.chris64233.labcalibration.domain.CertificateInvalidation;
import com.chris64233.labcalibration.domain.TestResult;
import com.chris64233.labcalibration.repo.CalibrationCertificateRepository;
import com.chris64233.labcalibration.repo.CertificateInvalidationRepository;
import com.chris64233.labcalibration.repo.TestResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 校准失效登记与影响追踪。
 *
 * 登记失效区间时，先对证书行加悲观写锁（与结果签发互斥），
 * 再用一条批量 UPDATE 把区间内依赖该证书的全部已签发结果置为待复核：
 * 单条语句保证原子性（不遗漏），状态前置条件保证幂等（不重复标记）。
 */
@Service
public class InvalidationService {

    private final CalibrationCertificateRepository certificateRepository;
    private final CertificateInvalidationRepository invalidationRepository;
    private final TestResultRepository testResultRepository;
    private final Clock clock;

    public InvalidationService(CalibrationCertificateRepository certificateRepository,
                               CertificateInvalidationRepository invalidationRepository,
                               TestResultRepository testResultRepository,
                               Clock clock) {
        this.certificateRepository = certificateRepository;
        this.invalidationRepository = invalidationRepository;
        this.testResultRepository = testResultRepository;
        this.clock = clock;
    }

    /**
     * 登记证书失效区间，并一次性圈定受影响结果。
     *
     * @return 影响报告：失效登记 + 受影响结果清单 + 本次新标记为待复核的数量
     */
    @Transactional
    public ImpactReport registerInvalidation(long certificateId, Instant invalidFrom,
                                             Instant invalidTo, String reason) {
        if (invalidFrom == null || invalidTo == null || invalidTo.isBefore(invalidFrom)) {
            throw BusinessException.badRequest("失效区间不合法");
        }
        if (reason == null || reason.isBlank()) {
            throw BusinessException.badRequest("失效原因不能为空");
        }
        CalibrationCertificate certificate = certificateRepository.findByIdForUpdate(certificateId)
                .orElseThrow(() -> BusinessException.notFound("证书不存在: " + certificateId));

        CertificateInvalidation invalidation = invalidationRepository.save(
                new CertificateInvalidation(certificate, invalidFrom, invalidTo, reason, clock.instant()));

        int newlyMarked = testResultRepository.bulkMarkPendingReview(certificateId, invalidFrom, invalidTo);
        List<TestResult> impacted = impactedResults(certificateId, invalidFrom, invalidTo);
        return new ImpactReport(invalidation, impacted, newlyMarked);
    }

    /** 查询证书在指定区间内的全部受影响结果（含各状态，用于影响范围查询）。 */
    @Transactional(readOnly = true)
    public List<TestResult> impactedResults(long certificateId, Instant from, Instant to) {
        certificateRepository.findById(certificateId)
                .orElseThrow(() -> BusinessException.notFound("证书不存在: " + certificateId));
        Instant effectiveFrom = from != null ? from : Instant.EPOCH;
        Instant effectiveTo = to != null ? to : Instant.MAX;
        return testResultRepository.findByCertificateIdAndExecutedAtBetweenOrderByExecutedAtAsc(
                certificateId, effectiveFrom, effectiveTo);
    }

    @Transactional(readOnly = true)
    public List<CertificateInvalidation> listInvalidations(long certificateId) {
        certificateRepository.findById(certificateId)
                .orElseThrow(() -> BusinessException.notFound("证书不存在: " + certificateId));
        return invalidationRepository.findByCertificateIdOrderByCreatedAtAsc(certificateId);
    }

    /** 失效登记的影响报告。 */
    public record ImpactReport(CertificateInvalidation invalidation,
                               List<TestResult> impactedResults,
                               int newlyMarked) {
    }
}
