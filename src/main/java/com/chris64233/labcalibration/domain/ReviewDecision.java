package com.chris64233.labcalibration.domain;

/**
 * 复核决定。
 */
public enum ReviewDecision {
    /** 授权人员确认原结果仍然有效。 */
    CONFIRM_VALID,
    /** 以重测新结果替代原结果。 */
    RETEST_REPLACE
}
