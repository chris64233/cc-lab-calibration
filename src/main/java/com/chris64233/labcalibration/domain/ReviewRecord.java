package com.chris64233.labcalibration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 复核记录：对待复核结果的处理决定，构成复核链。
 */
@Entity
@Table(name = "review_records")
public class ReviewRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "original_result_id", nullable = false)
    private TestResult originalResult;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReviewDecision decision;

    /** 复核依据，必填。 */
    @Column(nullable = false, length = 512)
    private String basis;

    /** 复核人（授权人员）。 */
    @Column(nullable = false)
    private String decidedBy;

    @Column(nullable = false)
    private Instant decidedAt;

    /** 重测替代时的新结果。 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "replacement_result_id")
    private TestResult replacementResult;

    protected ReviewRecord() {
    }

    public ReviewRecord(TestResult originalResult, ReviewDecision decision, String basis, String decidedBy,
                        Instant decidedAt, TestResult replacementResult) {
        this.originalResult = originalResult;
        this.decision = decision;
        this.basis = basis;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.replacementResult = replacementResult;
    }

    public Long getId() {
        return id;
    }

    public TestResult getOriginalResult() {
        return originalResult;
    }

    public ReviewDecision getDecision() {
        return decision;
    }

    public String getBasis() {
        return basis;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public TestResult getReplacementResult() {
        return replacementResult;
    }
}
