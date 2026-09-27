package com.chris64233.labcalibration.web;

import com.chris64233.labcalibration.domain.CalibrationCertificate;
import com.chris64233.labcalibration.domain.Instrument;
import com.chris64233.labcalibration.service.InstrumentService;
import com.chris64233.labcalibration.web.dto.Requests.CreateCalibrationRequest;
import com.chris64233.labcalibration.web.dto.Requests.CreateInstrumentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 仪器与校准证书接口。
 */
@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Instrument create(@Valid @RequestBody CreateInstrumentRequest request) {
        return instrumentService.createInstrument(request.code(), request.name(),
                request.calibrationCycleMonths(), request.applicableTestItems());
    }

    @GetMapping("/{code}")
    public Instrument get(@PathVariable String code) {
        return instrumentService.getByCode(code);
    }

    /** 登记一次校准，生成新的证书版本。 */
    @PostMapping("/{code}/calibrations")
    @ResponseStatus(HttpStatus.CREATED)
    public CalibrationCertificate addCalibration(@PathVariable String code,
                                                 @Valid @RequestBody CreateCalibrationRequest request) {
        return instrumentService.addCalibration(code, request.validFrom(), request.validTo(),
                request.conclusion(), request.issuedBy());
    }

    /** 证书版本查询（按版本倒序）。 */
    @GetMapping("/{code}/certificates")
    public List<CalibrationCertificate> listCertificates(@PathVariable String code) {
        return instrumentService.listCertificates(code);
    }
}
