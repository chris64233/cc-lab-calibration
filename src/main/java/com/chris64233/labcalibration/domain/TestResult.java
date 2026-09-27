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
 * 检测结果：签发时锁定所用仪器与校准证书版本。
 * 业务字段（样本、项目、执行时间、仪器、证书、测量值）创建后不可修改，
 * 仅生命周期状态可流转。
 */
@Entity
@Table(name = "test_results")
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 结果编号，全局唯一，作为幂等键。 */
    @Column(nullable = false, unique = true, length = 64)
    private String resultNo;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    /** 签发时锁定的校准证书版本。 */
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "certificate_id", nullable = false)
    private CalibrationCertificate certificate;

    @Column(nullable = false)
    private String sampleId;

    @Column(nullable = false, length = 128)
    private String testItem;

    @Column(nullable = false)
    private Instant executedAt;

    @Column(nullable = false)
    private String measuredValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ResultStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant publishedAt;

    protected TestResult() {
    }

    public TestResult(String resultNo, Instrument instrument, CalibrationCertificate certificate,
                      String sampleId, String testItem, Instant executedAt, String measuredValue,
                      Instant createdAt) {
        this.resultNo = resultNo;
        this.instrument = instrument;
        this.certificate = certificate;
        this.sampleId = sampleId;
        this.testItem = testItem;
        this.executedAt = executedAt;
        this.measuredValue = measuredValue;
        this.status = ResultStatus.ISSUED;
        this.createdAt = createdAt;
    }

    /** 签发/复核字段是否与幂等重放的请求一致。 */
    public boolean matchesPayload(String instrumentCode, String sampleId, String testItem,
                                  Instant executedAt, String measuredValue) {
        return instrument.getCode().equals(instrumentCode)
                && this.sampleId.equals(sampleId)
                && this.testItem.equals(testItem)
                && this.executedAt.equals(executedAt)
                && this.measuredValue.equals(measuredValue);
    }

    public void markPendingReview() {
        this.status = ResultStatus.PENDING_REVIEW;
    }

    public void confirmValid() {
        requireStatus(ResultStatus.PENDING_REVIEW, "仅待复核结果可确认有效");
        this.status = ResultStatus.CONFIRMED_VALID;
    }

    public void markReplaced() {
        requireStatus(ResultStatus.PENDING_REVIEW, "仅待复核结果可被替代");
        this.status = ResultStatus.REPLACED;
    }

    public void publish(Instant publishedAt) {
        if (status == ResultStatus.PUBLISHED) {
            return;
        }
        if (status != ResultStatus.ISSUED && status != ResultStatus.CONFIRMED_VALID) {
            throw new IllegalStateException("当前状态不允许发布: " + status);
        }
        this.status = ResultStatus.PUBLISHED;
        this.publishedAt = publishedAt;
    }

    private void requireStatus(ResultStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message + "，当前状态: " + status);
        }
    }

    public Long getId() {
        return id;
    }

    public String getResultNo() {
        return resultNo;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public CalibrationCertificate getCertificate() {
        return certificate;
    }

    public String getSampleId() {
        return sampleId;
    }

    public String getTestItem() {
        return testItem;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public String getMeasuredValue() {
        return measuredValue;
    }

    public ResultStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
