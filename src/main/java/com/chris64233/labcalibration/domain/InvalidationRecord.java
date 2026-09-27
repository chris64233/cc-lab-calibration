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
 * 校准失效登记：声明某证书版本在指定区间内不可信。
 */
@Entity
@Table(name = "invalidation_records")
public class InvalidationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "certificate_id", nullable = false)
    private CalibrationCertificate certificate;

    @Column(nullable = false)
    private Instant invalidFrom;

    @Column(nullable = false)
    private Instant invalidTo;

    @Column(nullable = false, length = 512)
    private String reason;

    @Column(nullable = false)
    private Instant registeredAt;

    protected InvalidationRecord() {
    }

    public InvalidationRecord(CalibrationCertificate certificate, Instant invalidFrom, Instant invalidTo,
                              String reason, Instant registeredAt) {
        this.certificate = certificate;
        this.invalidFrom = invalidFrom;
        this.invalidTo = invalidTo;
        this.reason = reason;
        this.registeredAt = registeredAt;
    }

    public boolean covers(Instant at) {
        return !at.isBefore(invalidFrom) && !at.isAfter(invalidTo);
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

    public Instant getRegisteredAt() {
        return registeredAt;
    }
}
