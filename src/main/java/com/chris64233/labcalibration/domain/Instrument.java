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
 * 实验室仪器：记录校准周期与适用检测项目。
 */
@Entity
@Table(name = "instruments")
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 仪器编号，全局唯一。 */
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false)
    private String name;

    /** 校准周期（月），证书有效区间不得超过该周期。 */
    @Column(nullable = false)
    private int calibrationCycleMonths;

    /** 适用检测项目。 */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "instrument_test_items", joinColumns = @JoinColumn(name = "instrument_id"))
    @Column(name = "test_item", nullable = false, length = 128)
    private Set<String> applicableTestItems = new LinkedHashSet<>();

    protected Instrument() {
    }

    public Instrument(String code, String name, int calibrationCycleMonths, Set<String> applicableTestItems) {
        this.code = code;
        this.name = name;
        this.calibrationCycleMonths = calibrationCycleMonths;
        this.applicableTestItems = new LinkedHashSet<>(applicableTestItems);
    }

    public boolean supports(String testItem) {
        return applicableTestItems.contains(testItem);
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
