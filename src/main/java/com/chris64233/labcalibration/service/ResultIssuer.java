package com.chris64233.labcalibration.service;

import com.chris64233.labcalibration.domain.CalibrationCertificate;
import com.chris64233.labcalibration.domain.Instrument;
import com.chris64233.labcalibration.domain.TestResult;
import com.chris64233.labcalibration.repo.CalibrationCertificateRepository;
import com.chris64233.labcalibration.repo.CertificateInvalidationRepository;
import com.chris64233.labcalibration.repo.InstrumentRepository;
import com.chris64233.labcalibration.repo.TestResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 结果签发的事务化核心。所有校验与插入在同一事务内完成，
 * 并对锁定的证书版本加悲观写锁，与失效登记互斥，
 * 保证"签发时仪器处于有效校准状态"这一判定不会被并发的失效登记穿越。
 */
@Service
public class ResultIssuer {

    private final InstrumentRepository instrumentRepository;
    private final CalibrationCertificateRepository certificateRepository;
    private final CertificateInvalidationRepository invalidationRepository;
    private final TestResultRepository testResultRepository;
    private final Clock clock;

    public ResultIssuer(InstrumentRepository instrumentRepository,
                        CalibrationCertificateRepository certificateRepository,
                        CertificateInvalidationRepository invalidationRepository,
                        TestResultRepository testResultRepository,
                        Clock clock) {
        this.instrumentRepository = instrumentRepository;
        this.certificateRepository = certificateRepository;
        this.invalidationRepository = invalidationRepository;
        this.testResultRepository = testResultRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public TestResult issueNew(IssueResultCommand cmd) {
        Instrument instrument = instrumentRepository.findByCode(cmd.instrumentCode())
                .orElseThrow(() -> BusinessException.notFound("仪器不存在: " + cmd.instrumentCode()));

        if (!instrument.getApplicableTestItems().contains(cmd.testItem())) {
            throw BusinessException.badRequest(
                    "仪器 " + cmd.instrumentCode() + " 不适用于检测项目 " + cmd.testItem());
        }

        // 只有在检测执行时刻处于有效校准状态（证书覆盖执行时间且结论合格）的仪器才能提交结果。
        CalibrationCertificate certificate = certificateRepository
                .findValidPassCertificatesAt(instrument.getId(), cmd.executedAt())
                .stream().findFirst()
                .orElseThrow(() -> BusinessException.conflict(
                        "仪器 " + cmd.instrumentCode() + " 在执行时刻 " + cmd.executedAt()
                                + " 无有效校准证书，禁止提交结果"));

        // 锁定证书行，与失效登记串行化。
        certificateRepository.findByIdForUpdate(certificate.getId())
                .orElseThrow(() -> BusinessException.notFound("证书不存在: " + certificate.getId()));

        // 执行时刻已落在该证书的失效区间内的，禁止提交。
        if (invalidationRepository.existsCovering(certificate.getId(), cmd.executedAt())) {
            throw BusinessException.conflict(
                    "证书版本 v" + certificate.getVersion() + " 在执行时刻 " + cmd.executedAt()
                            + " 已被登记失效，禁止提交结果");
        }

        TestResult result = new TestResult(cmd.requestId(), instrument, certificate,
                cmd.sampleId(), cmd.testItem(), cmd.executedAt(),
                cmd.measurement(), cmd.unit(), clock.instant());
        TestResult saved = testResultRepository.saveAndFlush(result);
        // 结果编号由主键派生，全局唯一，签发成功后不再变化。
        saved.assignResultNo(String.format("R%08d", saved.getId()));
        return testResultRepository.saveAndFlush(saved);
    }
}
