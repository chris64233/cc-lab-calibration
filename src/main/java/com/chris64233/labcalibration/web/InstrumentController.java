package com.chris64233.labcalibration.web;

import com.chris64233.labcalibration.service.InstrumentService;
import com.chris64233.labcalibration.web.dto.Requests.CreateInstrumentRequest;
import com.chris64233.labcalibration.web.dto.Requests.RegisterCertificateRequest;
import com.chris64233.labcalibration.web.dto.Responses.CertificateResponse;
import com.chris64233.labcalibration.web.dto.Responses.InstrumentResponse;
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

/** 仪器与校准证书版本管理。 */
@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InstrumentResponse register(@Valid @RequestBody CreateInstrumentRequest request) {
        return InstrumentResponse.of(instrumentService.registerInstrument(
                request.code(), request.name(), request.calibrationCycleMonths(),
                request.applicableTestItems()));
    }

    /** 登记一次校准，形成新的证书版本。 */
    @PostMapping("/{code}/certificates")
    @ResponseStatus(HttpStatus.CREATED)
    public CertificateResponse registerCertificate(@PathVariable String code,
                                                   @Valid @RequestBody RegisterCertificateRequest request) {
        return CertificateResponse.of(instrumentService.registerCertificate(
                code, request.certificateNo(), request.validFrom(), request.validTo(),
                request.conclusion()));
    }

    /** 证书版本查询：按版本号倒序返回仪器的全部证书版本。 */
    @GetMapping("/{code}/certificates")
    public List<CertificateResponse> listCertificates(@PathVariable String code) {
        return instrumentService.listCertificateVersions(code).stream()
                .map(CertificateResponse::of)
                .toList();
    }
}
