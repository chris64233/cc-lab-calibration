package com.chris64233.labcalibration.service;

import java.math.BigDecimal;
import java.time.Instant;

/** 签发检测结果命令。requestId 为客户端幂等键。 */
public record IssueResultCommand(
        String requestId,
        String instrumentCode,
        String sampleId,
        String testItem,
        Instant executedAt,
        BigDecimal measurement,
        String unit) {
}
