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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 检测结果。签发时锁定所用仪器与校准证书版本，
 * 并记录样本、检测项目和执行时间。
 * 结果一经签发不可修改，状态只能按既定生命周期流转。
 */
@Entity
@Table(name = "test_result")
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 结果编号，全局唯一，签发成功后不再变化。
     * 由主键派生，插入后的同一事务内赋值，提交前必然非空。
     */
    @Column(unique = true, length = 32)
    private String resultNo;

    /** 客户端幂等键：同一 requestId 重复提交返回同一结果，保证幂等。 */
    @Column(nullable = false, unique = true, length = 128)
    private String requestId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    /** 签发时锁定的校准证书版本。 */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "certificate_id", nullable = false)
    private CalibrationCertificate certificate;

    @Column(nullable = false, length = 128)
    private String sampleId;

    @Column(nullable = false, length = 128)
    private String testItem;

    /** 检测执行时间，用于校准有效性判定与失效影响圈定。 */
    @Column(nullable = false)
    private Instant executedAt;

    @Column(nullable = false, precision = 24, scale = 6)
    private BigDecimal measurement;

    @Column(length = 32)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ResultStatus status;

    /** 是否已对外发布。 */
    @Column(nullable = false)
    private boolean published;

    @Column(nullable = false)
    private Instant createdAt;

    protected TestResult() {
    }

    public TestResult(String requestId, Instrument instrument, CalibrationCertificate certificate,
                      String sampleId, String testItem, Instant executedAt,
                      BigDecimal measurement, String unit, Instant createdAt) {
        this.requestId = requestId;
        this.instrument = instrument;
        this.certificate = certificate;
        this.sampleId = sampleId;
        this.testItem = testItem;
        this.executedAt = executedAt;
        this.measurement = measurement;
        this.unit = unit;
        this.status = ResultStatus.ISSUED;
        this.published = false;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getResultNo() {
        return resultNo;
    }

    public void assignResultNo(String resultNo) {
        this.resultNo = resultNo;
    }

    public String getRequestId() {
        return requestId;
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

    public BigDecimal getMeasurement() {
        return measurement;
    }

    public String getUnit() {
        return unit;
    }

    public ResultStatus getStatus() {
        return status;
    }

    public boolean isPublished() {
        return published;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
