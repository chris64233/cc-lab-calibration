package com.chris64233.labcalibration.domain;

/**
 * 校准结论。
 */
public enum CalibrationConclusion {
    /** 校准合格，仪器在有效区间内可用于检测。 */
    PASS,
    /** 校准不合格，仪器不得用于检测。 */
    FAIL
}
