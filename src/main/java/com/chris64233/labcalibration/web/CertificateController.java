package com.chris64233.labcalibration.web;

import com.chris64233.labcalibration.service.InstrumentService;
import com.chris64233.labcalibration.service.InvalidationService;
import com.chris64233.labcalibration.web.dto.Requests.RegisterInvalidationRequest;
import com.chris64233.labcalibration.web.dto.Responses.CertificateResponse;
import com.chris64233.labcalibration.web.dto.Responses.ImpactResponse;
import com.chris64233.labcalibration.web.dto.Responses.InvalidationResponse;
import com.chris64233.labcalibration.web.dto.Responses.TestResultResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** 校准证书：失效登记与影响范围查询。 */
@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    private final InstrumentService instrumentService;
    private final InvalidationService invalidationService;

    public CertificateController(InstrumentService instrumentService,
                                 InvalidationService invalidationService) {
        this.instrumentService = instrumentService;
        this.invalidationService = invalidationService;
    }

    @GetMapping("/{id}")
    public CertificateResponse get(@PathVariable long id) {
        return CertificateResponse.of(instrumentService.getCertificate(id));
    }

    /**
     * 登记证书失效区间。系统在同一事务内一次性找出区间内依赖该证书的
     * 全部已签发结果并置为待复核，返回完整影响报告。
     */
    @PostMapping("/{id}/invalidations")
    public ImpactResponse registerInvalidation(@PathVariable long id,
                                               @Valid @RequestBody RegisterInvalidationRequest request) {
        InvalidationService.ImpactReport report = invalidationService.registerInvalidation(
                id, request.invalidFrom(), request.invalidTo(), request.reason());
        return new ImpactResponse(
                InvalidationResponse.of(report.invalidation()),
                report.impactedResults().stream().map(TestResultResponse::of).toList(),
                report.newlyMarked());
    }

    @GetMapping("/{id}/invalidations")
    public List<InvalidationResponse> listInvalidations(@PathVariable long id) {
        return invalidationService.listInvalidations(id).stream()
                .map(InvalidationResponse::of)
                .toList();
    }

    /** 影响范围查询：证书在指定区间（缺省为全部时间）内被哪些结果依赖。 */
    @GetMapping("/{id}/impact")
    public List<TestResultResponse> impact(
            @PathVariable long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return invalidationService.impactedResults(id, from, to).stream()
                .map(TestResultResponse::of)
                .toList();
    }
}
