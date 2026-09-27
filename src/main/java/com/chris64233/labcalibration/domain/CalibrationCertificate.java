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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 校准证书版本：每次校准生成一个带有效区间与结论的新版本。
 * 证书一经创建不可修改（无更新入口，实体不暴露 setter）。
 */
@Entity
@Table(name = "calibration_certificates",
        uniqueConstraints = @UniqueConstraint(name = "uk_cert_instrument_version",
                columnNames = {"instrument_id", "version"}))
public class CalibrationCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    /** 同一仪器下单调递增的版本号。 */
    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private Instant validFrom;

    @Column(nullable = false)
    private Instant validTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CalibrationConclusion conclusion;

    /** 校准机构/人员。 */
    @Column(nullable = false)
    private String issuedBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected CalibrationCertificate() {
    }

    public CalibrationCertificate(Instrument instrument, int version, Instant validFrom, Instant validTo,
                                  CalibrationConclusion conclusion, String issuedBy, Instant createdAt) {
        this.instrument = instrument;
        this.version = version;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.conclusion = conclusion;
        this.issuedBy = issuedBy;
        this.createdAt = createdAt;
    }

    /** 判断给定时刻是否处于本证书有效校准状态（区间闭区间且结论合格）。 */
    public boolean isValidAt(Instant at) {
        return conclusion == CalibrationConclusion.PASS && !at.isBefore(validFrom) && !at.isAfter(validTo);
    }

    public Long getId() {
        return id;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public int getVersion() {
        return version;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public CalibrationConclusion getConclusion() {
        return conclusion;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
