package com.chris64233.labcalibration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 校准证书失效登记。当某次校准被认定不可信时，
 * 登记其失效区间，系统据此圈定受影响的已签发结果。
 */
@Entity
@Table(name = "certificate_invalidation")
public class CertificateInvalidation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "certificate_id", nullable = false)
    private CalibrationCertificate certificate;

    @Column(nullable = false)
    private Instant invalidFrom;

    @Column(nullable = false)
    private Instant invalidTo;

    @Column(nullable = false, length = 512)
    private String reason;

    @Column(nullable = false)
    private Instant createdAt;

    protected CertificateInvalidation() {
    }

    public CertificateInvalidation(CalibrationCertificate certificate, Instant invalidFrom,
                                   Instant invalidTo, String reason, Instant createdAt) {
        this.certificate = certificate;
        this.invalidFrom = invalidFrom;
        this.invalidTo = invalidTo;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public CalibrationCertificate getCertificate() {
        return certificate;
    }

    public Instant getInvalidFrom() {
        return invalidFrom;
    }

    public Instant getInvalidTo() {
        return invalidTo;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
