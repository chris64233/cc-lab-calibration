package com.chris64233.labcalibration.web;

import com.chris64233.labcalibration.domain.InvalidationRecord;
import com.chris64233.labcalibration.service.InvalidationService;
import com.chris64233.labcalibration.service.InvalidationService.InvalidationImpact;
import com.chris64233.labcalibration.web.dto.Requests.RegisterInvalidationRequest;
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
 * 校准失效登记与影响范围查询接口。
 */
@RestController
@RequestMapping("/api")
public class InvalidationController {

    private final InvalidationService invalidationService;

    public InvalidationController(InvalidationService invalidationService) {
        this.invalidationService = invalidationService;
    }

    /** 登记证书失效区间，返回失效记录与一次性标记为待复核的受影响结果。 */
    @PostMapping("/certificates/{certificateId}/invalidations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvalidationImpact register(@PathVariable Long certificateId,
                                       @Valid @RequestBody RegisterInvalidationRequest request) {
        return invalidationService.register(certificateId, request.invalidFrom(),
                request.invalidTo(), request.reason());
    }

    /** 某证书的全部失效登记。 */
    @GetMapping("/certificates/{certificateId}/invalidations")
    public List<InvalidationRecord> listByCertificate(@PathVariable Long certificateId) {
        return invalidationService.listByCertificate(certificateId);
    }

    /** 影响范围查询：某次失效登记波及的全部结果及其当前状态。 */
    @GetMapping("/invalidations/{invalidationId}/impact")
    public InvalidationImpact impact(@PathVariable Long invalidationId) {
        return invalidationService.impactOf(invalidationId);
    }
}
