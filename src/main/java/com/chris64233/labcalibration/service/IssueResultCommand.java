package com.chris64233.labcalibration.service;

import java.time.Instant;

/**
 * 签发检测结果的命令。
 *
 * @param resultNo       结果编号（幂等键，由调用方保证业务唯一）
 * @param instrumentCode 仪器编号
 * @param sampleId       样本标识
 * @param testItem       检测项目
 * @param executedAt     检测执行时间
 * @param measuredValue  测量值
 */
public record IssueResultCommand(String resultNo, String instrumentCode, String sampleId,
                                 String testItem, Instant executedAt, String measuredValue) {
}
