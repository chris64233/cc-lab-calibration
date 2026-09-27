package com.chris64233.labcalibration.service;

import com.chris64233.labcalibration.domain.CalibrationCertificate;
import com.chris64233.labcalibration.domain.InvalidationRecord;
import com.chris64233.labcalibration.domain.TestResult;
import com.chris64233.labcalibration.repo.CalibrationCertificateRepository;
import com.chris64233.labcalibration.repo.InvalidationRecordRepository;
import com.chris64233.labcalibration.repo.TestResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 校准失效登记与影响追踪。
 */
@Service
public class InvalidationService {

    private final CalibrationCertificateRepository certificateRepository;
    private final InvalidationRecordRepository invalidationRepository;
    private final TestResultRepository resultRepository;

    public InvalidationService(CalibrationCertificateRepository certificateRepository,
                               InvalidationRecordRepository invalidationRepository,
                               TestResultRepository resultRepository) {
        this.certificateRepository = certificateRepository;
        this.invalidationRepository = invalidationRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * 登记证书失效区间，并在同一事务内一次性找出该区间内依赖该证书的全部
     * 已签发结果置为待复核（已待复核的不重复标记，已被替代的不再触碰）。
     *
     * @return 失效登记与受影响结果列表
     */
    @Transactional
    public InvalidationImpact register(Long certificateId, Instant invalidFrom, Instant invalidTo, String reason) {
        if (!invalidFrom.isBefore(invalidTo)) {
            throw BusinessException.badRequest("INVALID_INVALIDATION_RANGE", "失效区间起点必须早于终点");
        }
        CalibrationCertificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> BusinessException.notFound("校准证书不存在: " + certificateId));
        InvalidationRecord record = invalidationRepository.save(
                new InvalidationRecord(certificate, invalidFrom, invalidTo, reason, Instant.now()));

        List<TestResult> affected = resultRepository.findAffected(certificateId, invalidFrom, invalidTo);
        if (!affected.isEmpty()) {
            resultRepository.markPendingReview(affected.stream().map(TestResult::getId).toList());
        }
        return new InvalidationImpact(record, affected);
    }

    @Transactional(readOnly = true)
    public InvalidationImpact impactOf(Long invalidationId) {
        InvalidationRecord record = invalidationRepository.findById(invalidationId)
                .orElseThrow(() -> BusinessException.notFound("失效登记不存在: " + invalidationId));
        List<TestResult> affected = resultRepository.findAffected(
                record.getCertificate().getId(), record.getInvalidFrom(), record.getInvalidTo());
        return new InvalidationImpact(record, affected);
    }

    @Transactional(readOnly = true)
    public List<InvalidationRecord> listByCertificate(Long certificateId) {
        if (!certificateRepository.existsById(certificateId)) {
            throw BusinessException.notFound("校准证书不存在: " + certificateId);
        }
        return invalidationRepository.findByCertificateIdOrderByRegisteredAtAsc(certificateId);
    }

    /**
     * 失效登记及其影响范围。
     */
    public record InvalidationImpact(InvalidationRecord invalidation, List<TestResult> affectedResults) {
    }
}
