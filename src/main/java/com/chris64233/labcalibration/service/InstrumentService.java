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

/**
 * 仪器与校准证书管理。
 */
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
    public Instrument createInstrument(String code, String name, int calibrationCycleMonths,
                                       Set<String> applicableTestItems) {
        if (instrumentRepository.existsByCode(code)) {
            throw BusinessException.conflict("INSTRUMENT_CODE_DUPLICATED", "仪器编号已存在: " + code);
        }
        if (calibrationCycleMonths <= 0) {
            throw BusinessException.badRequest("INVALID_CALIBRATION_CYCLE", "校准周期必须为正整数（月）");
        }
        if (applicableTestItems == null || applicableTestItems.isEmpty()) {
            throw BusinessException.badRequest("EMPTY_TEST_ITEMS", "适用检测项目不能为空");
        }
        return instrumentRepository.save(new Instrument(code, name, calibrationCycleMonths, applicableTestItems));
    }

    @Transactional(readOnly = true)
    public Instrument getByCode(String code) {
        return instrumentRepository.findByCode(code)
                .orElseThrow(() -> BusinessException.notFound("仪器不存在: " + code));
    }

    /**
     * 登记一次校准，生成新的证书版本（版本号按仪器单调递增）。
     * 有效区间必须为正且不超过仪器的校准周期。
     */
    @Transactional
    public CalibrationCertificate addCalibration(String instrumentCode, Instant validFrom, Instant validTo,
                                                 CalibrationConclusion conclusion, String issuedBy) {
        Instrument instrument = getByCode(instrumentCode);
        if (!validFrom.isBefore(validTo)) {
            throw BusinessException.badRequest("INVALID_VALIDITY_RANGE", "证书有效区间起点必须早于终点");
        }
        Instant maxValidTo = validFrom.atZone(java.time.ZoneOffset.UTC)
                .plusMonths(instrument.getCalibrationCycleMonths()).toInstant();
        if (validTo.isAfter(maxValidTo)) {
            throw BusinessException.badRequest("VALIDITY_EXCEEDS_CYCLE",
                    "证书有效区间超过仪器校准周期（" + instrument.getCalibrationCycleMonths() + "个月）");
        }
        int version = certificateRepository.maxVersion(instrument.getId()) + 1;
        CalibrationCertificate certificate = new CalibrationCertificate(
                instrument, version, validFrom, validTo, conclusion, issuedBy, Instant.now());
        return certificateRepository.save(certificate);
    }

    @Transactional(readOnly = true)
    public List<CalibrationCertificate> listCertificates(String instrumentCode) {
        Instrument instrument = getByCode(instrumentCode);
        return certificateRepository.findByInstrumentIdOrderByVersionDesc(instrument.getId());
    }
}
