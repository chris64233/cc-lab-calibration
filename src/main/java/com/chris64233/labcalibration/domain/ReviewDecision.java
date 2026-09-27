package com.chris64233.labcalibration.domain;

/** 复核决定类型。 */
public enum ReviewDecision {
    /** 授权人员确认原结果仍然有效。 */
    CONFIRM_VALID,
    /** 通过重测产生新结果替代原结果。 */
    RETEST
}
