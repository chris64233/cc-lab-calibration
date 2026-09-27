package com.chris64233.labcalibration.web.dto;

import com.chris64233.labcalibration.domain.CalibrationConclusion;
import com.chris64233.labcalibration.domain.ReviewDecision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Set;

/**
 * API 请求 DTO。
 */
public final class Requests {

    private Requests() {
    }

    public record CreateInstrumentRequest(
            @NotBlank String code,
            @NotBlank String name,
            @Min(1) int calibrationCycleMonths,
            @NotEmpty Set<String> applicableTestItems) {
    }

    public record CreateCalibrationRequest(
            @NotNull Instant validFrom,
            @NotNull Instant validTo,
            @NotNull CalibrationConclusion conclusion,
            @NotBlank String issuedBy) {
    }

    public record IssueResultRequest(
            @NotBlank String resultNo,
            @NotBlank String instrumentCode,
            @NotBlank String sampleId,
            @NotBlank String testItem,
            @NotNull Instant executedAt,
            @NotBlank String measuredValue) {
    }

    public record RegisterInvalidationRequest(
            @NotNull Instant invalidFrom,
            @NotNull Instant invalidTo,
            @NotBlank String reason) {
    }

    public record ReviewRequest(
            @NotNull ReviewDecision decision,
            @NotBlank String basis,
            @NotBlank String decidedBy,
            @Valid RetestPayload retest) {
    }

    public record RetestPayload(
            @NotBlank String resultNo,
            @NotNull Instant executedAt,
            @NotBlank String measuredValue) {
    }
}
