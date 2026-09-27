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
 * 复核记录。对待复核结果的每一次处置（确认有效 / 重测替代）
 * 都形成一条不可修改的复核记录，构成该结果的复核链。
 */
@Entity
@Table(name = "review_record")
public class ReviewRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "test_result_id", nullable = false)
    private TestResult testResult;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReviewDecision decision;

    /** 复核依据，必填。 */
    @Column(nullable = false, length = 1024)
    private String rationale;

    /** 复核人（须具备授权角色）。 */
    @Column(nullable = false, length = 128)
    private String decidedBy;

    @Column(nullable = false)
    private Instant decidedAt;

    /** 重测替代时指向新结果编号；确认有效时为空。 */
    @Column(length = 32)
    private String replacementResultNo;

    protected ReviewRecord() {
    }

    public ReviewRecord(TestResult testResult, ReviewDecision decision, String rationale,
                        String decidedBy, Instant decidedAt, String replacementResultNo) {
        this.testResult = testResult;
        this.decision = decision;
        this.rationale = rationale;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.replacementResultNo = replacementResultNo;
    }

    public Long getId() {
        return id;
    }

    public TestResult getTestResult() {
        return testResult;
    }

    public ReviewDecision getDecision() {
        return decision;
    }

    public String getRationale() {
        return rationale;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getReplacementResultNo() {
        return replacementResultNo;
    }
}
