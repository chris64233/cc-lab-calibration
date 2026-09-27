package com.chris64233.labcalibration.domain;

/**
 * 检测结果生命周期状态。
 */
public enum ResultStatus {
    /** 已签发（未发布）。 */
    ISSUED,
    /** 因校准失效被置为待复核，禁止对外发布。 */
    PENDING_REVIEW,
    /** 复核确认仍有效，可发布。 */
    CONFIRMED_VALID,
    /** 已被重测结果替代，不可再发布。 */
    REPLACED,
    /** 已对外发布。 */
    PUBLISHED
}
