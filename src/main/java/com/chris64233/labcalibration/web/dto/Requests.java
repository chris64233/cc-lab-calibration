package com.chris64233.labcalibration.web.dto;

import com.chris64233.labcalibration.domain.CalibrationConclusion;
import com.chris64233.labcalibration.domain.ReviewDecision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/** 请求模型。 */
public final class Requests {

    private Requests() {
    }

    public record CreateInstrumentRequest(
            @NotBlank String code,
            @NotBlank String name,
            @Positive int calibrationCycleMonths,
            @NotEmpty Set<String> applicableTestItems) {
    }

    public record RegisterCertificateRequest(
            @NotBlank String certificateNo,
            @NotNull Instant validFrom,
            @NotNull Instant validTo,
            @NotNull CalibrationConclusion conclusion) {
    }

    public record IssueResultRequest(
            @NotBlank String requestId,
            @NotBlank String instrumentCode,
            @NotBlank String sampleId,
            @NotBlank String testItem,
            @NotNull Instant executedAt,
            @NotNull BigDecimal measurement,
            String unit) {
    }

    public record RegisterInvalidationRequest(
            @NotNull Instant invalidFrom,
            @NotNull Instant invalidTo,
            @NotBlank String reason) {
    }

    public record ReviewRequest(
            @NotNull ReviewDecision decision,
            @NotBlank String decidedBy,
            @NotBlank String reviewerRole,
            @NotBlank String rationale,
            @Valid IssueResultRequest retest) {
    }
}
