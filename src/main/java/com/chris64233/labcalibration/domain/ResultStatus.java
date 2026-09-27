package com.chris64233.labcalibration.domain;

/** 检测结果生命周期状态。 */
public enum ResultStatus {
    /** 已签发，可对外发布。 */
    ISSUED,
    /** 因校准证书失效被标记，等待复核；此状态禁止对外发布。 */
    PENDING_REVIEW,
    /** 经授权人员复核确认仍然有效，可对外发布。 */
    CONFIRMED_VALID,
    /** 已被重测结果替代，不可对外发布。 */
    SUPERSEDED
}
