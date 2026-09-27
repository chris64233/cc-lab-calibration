package com.chris64233.labcalibration.service;

import com.chris64233.labcalibration.domain.CalibrationCertificate;
import com.chris64233.labcalibration.domain.Instrument;
import com.chris64233.labcalibration.domain.TestResult;
import com.chris64233.labcalibration.repo.CalibrationCertificateRepository;
import com.chris64233.labcalibration.repo.InstrumentRepository;
import com.chris64233.labcalibration.repo.InvalidationRecordRepository;
import com.chris64233.labcalibration.repo.TestResultRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 结果签发的事务边界。独立成组件，使唯一约束冲突可以在事务外捕获后安全重查。
 */
@Component
public class ResultIssuanceTx {

    private final InstrumentRepository instrumentRepository;
    private final CalibrationCertificateRepository certificateRepository;
    private final InvalidationRecordRepository invalidationRepository;
    private final TestResultRepository resultRepository;

    public ResultIssuanceTx(InstrumentRepository instrumentRepository,
                            CalibrationCertificateRepository certificateRepository,
                            InvalidationRecordRepository invalidationRepository,
                            TestResultRepository resultRepository) {
        this.instrumentRepository = instrumentRepository;
        this.certificateRepository = certificateRepository;
        this.invalidationRepository = invalidationRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * 校验并落库一条新结果。若结果编号并发冲突，由调用方在事务外捕获后重查。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TestResult issueNew(IssueResultCommand cmd) {
        Instrument instrument = instrumentRepository.findByCode(cmd.instrumentCode())
                .orElseThrow(() -> BusinessException.notFound("仪器不存在: " + cmd.instrumentCode()));
        if (!instrument.supports(cmd.testItem())) {
            throw BusinessException.badRequest("TEST_ITEM_NOT_APPLICABLE",
                    "仪器 " + cmd.instrumentCode() + " 不适用检测项目: " + cmd.testItem());
        }
        CalibrationCertificate certificate = selectUsableCertificate(instrument, cmd.executedAt());
        TestResult result = new TestResult(cmd.resultNo(), instrument, certificate, cmd.sampleId(),
                cmd.testItem(), cmd.executedAt(), cmd.measuredValue(), Instant.now());
        return resultRepository.saveAndFlush(result);
    }

    /**
     * 选择在执行时刻处于有效校准状态的最新证书版本；
     * 该证书若已被登记覆盖执行时刻的失效区间，同样不可用。
     */
    private CalibrationCertificate selectUsableCertificate(Instrument instrument, Instant executedAt) {
        List<CalibrationCertificate> candidates =
                certificateRepository.findValidAt(instrument.getId(), executedAt);
        for (CalibrationCertificate certificate : candidates) {
            if (!invalidationRepository.existsCovering(certificate.getId(), executedAt)) {
                return certificate;
            }
        }
        throw BusinessException.conflict("NO_VALID_CALIBRATION",
                "仪器 " + instrument.getCode() + " 在执行时间 " + executedAt + " 无有效校准证书");
    }
}
