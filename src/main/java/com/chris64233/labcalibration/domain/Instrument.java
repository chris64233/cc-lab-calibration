package com.chris64233.labcalibration.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 实验室仪器。记录校准周期（月）与适用的检测项目。
 */
@Entity
@Table(name = "instrument")
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 仪器编号，业务唯一标识。 */
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false)
    private String name;

    /** 校准周期，单位：月。 */
    @Column(nullable = false)
    private int calibrationCycleMonths;

    /** 该仪器适用的检测项目。 */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "instrument_test_item", joinColumns = @JoinColumn(name = "instrument_id"))
    @Column(name = "test_item", nullable = false)
    private Set<String> applicableTestItems = new LinkedHashSet<>();

    protected Instrument() {
    }

    public Instrument(String code, String name, int calibrationCycleMonths, Set<String> applicableTestItems) {
        this.code = code;
        this.name = name;
        this.calibrationCycleMonths = calibrationCycleMonths;
        this.applicableTestItems = new LinkedHashSet<>(applicableTestItems);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getCalibrationCycleMonths() {
        return calibrationCycleMonths;
    }

    public Set<String> getApplicableTestItems() {
        return applicableTestItems;
    }
}
