package com.chris64233.labcalibration.web.dto;

import com.chris64233.labcalibration.domain.CalibrationCertificate;
import com.chris64233.labcalibration.domain.CalibrationConclusion;
import com.chris64233.labcalibration.domain.CertificateInvalidation;
import com.chris64233.labcalibration.domain.Instrument;
import com.chris64233.labcalibration.domain.ResultStatus;
import com.chris64233.labcalibration.domain.ReviewDecision;
import com.chris64233.labcalibration.domain.ReviewRecord;
import com.chris64233.labcalibration.domain.TestResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/** 对外暴露的响应模型，全部从实体映射，实体本身不直接序列化。 */
public final class Responses {

    private Responses() {
    }

    public record InstrumentResponse(Long id, String code, String name,
                                     int calibrationCycleMonths,
                                     Set<String> applicableTestItems) {
        public static InstrumentResponse of(Instrument i) {
            return new InstrumentResponse(i.getId(), i.getCode(), i.getName(),
                    i.getCalibrationCycleMonths(), i.getApplicableTestItems());
        }
    }

    public record CertificateResponse(Long id, String instrumentCode, int version,
                                      String certificateNo, Instant validFrom, Instant validTo,
                                      CalibrationConclusion conclusion, Instant registeredAt) {
        public static CertificateResponse of(CalibrationCertificate c) {
            return new CertificateResponse(c.getId(), c.getInstrument().getCode(), c.getVersion(),
                    c.getCertificateNo(), c.getValidFrom(), c.getValidTo(),
                    c.getConclusion(), c.getRegisteredAt());
        }
    }

    public record TestResultResponse(String resultNo, String requestId,
                                     String instrumentCode, Long certificateId, int certificateVersion,
                                     String sampleId, String testItem, Instant executedAt,
                                     BigDecimal measurement, String unit,
                                     ResultStatus status, boolean published, Instant createdAt) {
        public static TestResultResponse of(TestResult r) {
            return new TestResultResponse(r.getResultNo(), r.getRequestId(),
                    r.getInstrument().getCode(), r.getCertificate().getId(),
                    r.getCertificate().getVersion(), r.getSampleId(), r.getTestItem(),
                    r.getExecutedAt(), r.getMeasurement(), r.getUnit(),
                    r.getStatus(), r.isPublished(), r.getCreatedAt());
        }
    }

    public record InvalidationResponse(Long id, Long certificateId,
                                       Instant invalidFrom, Instant invalidTo,
                                       String reason, Instant createdAt) {
        public static InvalidationResponse of(CertificateInvalidation i) {
            return new InvalidationResponse(i.getId(), i.getCertificate().getId(),
                    i.getInvalidFrom(), i.getInvalidTo(), i.getReason(), i.getCreatedAt());
        }
    }

    public record ImpactResponse(InvalidationResponse invalidation,
                                 java.util.List<TestResultResponse> impactedResults,
                                 int newlyMarked) {
    }

    public record ReviewRecordResponse(Long id, String resultNo, ReviewDecision decision,
                                       String rationale, String decidedBy, Instant decidedAt,
                                       String replacementResultNo) {
        public static ReviewRecordResponse of(ReviewRecord r) {
            return new ReviewRecordResponse(r.getId(), r.getTestResult().getResultNo(),
                    r.getDecision(), r.getRationale(), r.getDecidedBy(), r.getDecidedAt(),
                    r.getReplacementResultNo());
        }
    }
}
