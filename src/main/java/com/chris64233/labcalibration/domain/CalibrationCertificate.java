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
 * 校准证书版本。每台仪器每次校准形成一个新的证书版本，
 * 带有有效区间 [validFrom, validTo] 与校准结论。
 * 证书一经登记不可修改（系统不提供任何修改入口）。
 */
@Entity
@Table(name = "calibration_certificate",
        uniqueConstraints = @UniqueConstraint(columnNames = {"instrument_id", "version"}))
public class CalibrationCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    /** 同一仪器下单调递增的证书版本号。 */
    @Column(nullable = false)
    private int version;

    /** 证书编号（外部校准机构出具）。 */
    @Column(nullable = false, length = 128)
    private String certificateNo;

    @Column(nullable = false)
    private Instant validFrom;

    @Column(nullable = false)
    private Instant validTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CalibrationConclusion conclusion;

    @Column(nullable = false)
    private Instant registeredAt;

    protected CalibrationCertificate() {
    }

    public CalibrationCertificate(Instrument instrument, int version, String certificateNo,
                                  Instant validFrom, Instant validTo, CalibrationConclusion conclusion,
                                  Instant registeredAt) {
        this.instrument = instrument;
        this.version = version;
        this.certificateNo = certificateNo;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.conclusion = conclusion;
        this.registeredAt = registeredAt;
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

    public String getCertificateNo() {
        return certificateNo;
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

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    /** 判断某时刻是否落在证书有效区间内（闭区间）。 */
    public boolean covers(Instant instant) {
        return !instant.isBefore(validFrom) && !instant.isAfter(validTo);
    }
}
