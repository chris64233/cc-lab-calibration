package com.chris64233.labcalibration.service;

import com.chris64233.labcalibration.domain.CalibrationCertificate;
import com.chris64233.labcalibration.domain.CalibrationConclusion;
import com.chris64233.labcalibration.domain.Instrument;
import com.chris64233.labcalibration.repo.CalibrationCertificateRepository;
import com.chris64233.labcalibration.repo.InstrumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 仪器与校准证书版本管理。 */
@Service
public class InstrumentService {

    private final InstrumentRepository instrumentRepository;
    private final CalibrationCertificateRepository certificateRepository;

    public InstrumentService(InstrumentRepository instrumentRepository,
                             CalibrationCertificateRepository certificateRepository) {
        this.instrumentRepository = instrumentRepository;
        this.certificateRepository = certificateRepository;
    }

    @Transactional
    public Instrument registerInstrument(String code, String name, int calibrationCycleMonths,
                                         Set<String> applicableTestItems) {
        if (code == null || code.isBlank()) {
            throw BusinessException.badRequest("仪器编号不能为空");
        }
        if (name == null || name.isBlank()) {
            throw BusinessException.badRequest("仪器名称不能为空");
        }
        if (calibrationCycleMonths <= 0) {
            throw BusinessException.badRequest("校准周期必须为正数（月）");
        }
        if (applicableTestItems == null || applicableTestItems.isEmpty()) {
            throw BusinessException.badRequest("适用检测项目不能为空");
        }
        if (instrumentRepository.existsByCode(code)) {
            throw BusinessException.conflict("仪器编号已存在: " + code);
        }
        return instrumentRepository.save(
                new Instrument(code, name, calibrationCycleMonths, applicableTestItems));
    }

    /**
     * 登记一次校准，形成新的证书版本（版本号 = 当前最大版本 + 1）。
     * 证书一经登记不可修改。
     */
    @Transactional
    public CalibrationCertificate registerCertificate(String instrumentCode, String certificateNo,
                                                      Instant validFrom, Instant validTo,
                                                      CalibrationConclusion conclusion) {
        Instrument instrument = instrumentRepository.findByCode(instrumentCode)
                .orElseThrow(() -> BusinessException.notFound("仪器不存在: " + instrumentCode));
        if (certificateNo == null || certificateNo.isBlank()) {
            throw BusinessException.badRequest("证书编号不能为空");
        }
        if (validFrom == null || validTo == null || validTo.isBefore(validFrom)) {
            throw BusinessException.badRequest("证书有效区间不合法");
        }
        if (conclusion == null) {
            throw BusinessException.badRequest("校准结论不能为空");
        }
        int nextVersion = certificateRepository.maxVersionOf(instrument.getId()) + 1;
        return certificateRepository.save(new CalibrationCertificate(
                instrument, nextVersion, certificateNo, validFrom, validTo, conclusion, Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<CalibrationCertificate> listCertificateVersions(String instrumentCode) {
        Instrument instrument = instrumentRepository.findByCode(instrumentCode)
                .orElseThrow(() -> BusinessException.notFound("仪器不存在: " + instrumentCode));
        return certificateRepository.findByInstrumentIdOrderByVersionDesc(instrument.getId());
    }

    @Transactional(readOnly = true)
    public CalibrationCertificate getCertificate(long certificateId) {
        return certificateRepository.findById(certificateId)
                .orElseThrow(() -> BusinessException.notFound("证书不存在: " + certificateId));
    }
}
