package com.chris64233.labcalibration.domain;

/** 校准证书结论。 */
public enum CalibrationConclusion {
    /** 校准合格，证书在有效区间内可用于检测。 */
    PASS,
    /** 校准不合格，证书不可用于检测。 */
    FAIL
}
