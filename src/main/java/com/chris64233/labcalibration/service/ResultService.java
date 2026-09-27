package com.chris64233.labcalibration.service;

import com.chris64233.labcalibration.domain.TestResult;
import com.chris64233.labcalibration.repo.TestResultRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 检测结果签发与发布。
 *
 * 幂等设计：客户端为每次签发请求提供幂等键 requestId，
 * 数据库对 requestId 建唯一约束；重复提交（含并发重试）返回首次签发的同一结果，
 * 结果编号保持不变，不会产生重复结果。
 */
@Service
public class ResultService {

    private final ResultIssuer resultIssuer;
    private final TestResultRepository testResultRepository;

    public ResultService(ResultIssuer resultIssuer, TestResultRepository testResultRepository) {
        this.resultIssuer = resultIssuer;
        this.testResultRepository = testResultRepository;
    }

    /**
     * 签发检测结果（幂等）。同一 requestId 重复提交返回首次签发的结果。
     * 注意：本方法本身不开事务，唯一约束冲突时原事务已回滚，
     * 可安全地重新查询并返回已存在的结果。
     */
    public TestResult issue(IssueResultCommand cmd) {
        validate(cmd);
        return testResultRepository.findByRequestId(cmd.requestId())
                .orElseGet(() -> doIssue(cmd));
    }

    private TestResult doIssue(IssueResultCommand cmd) {
        try {
            return resultIssuer.issueNew(cmd);
        } catch (DataIntegrityViolationException e) {
            // 并发下另一请求已用同一幂等键完成签发，返回其结果，保证不产生重复。
            return testResultRepository.findByRequestId(cmd.requestId())
                    .orElseThrow(() -> e);
        }
    }

    private void validate(IssueResultCommand cmd) {
        if (cmd.requestId() == null || cmd.requestId().isBlank()) {
            throw BusinessException.badRequest("幂等键 requestId 不能为空");
        }
        if (cmd.instrumentCode() == null || cmd.instrumentCode().isBlank()) {
            throw BusinessException.badRequest("仪器编号不能为空");
        }
        if (cmd.sampleId() == null || cmd.sampleId().isBlank()) {
            throw BusinessException.badRequest("样本编号不能为空");
        }
        if (cmd.testItem() == null || cmd.testItem().isBlank()) {
            throw BusinessException.badRequest("检测项目不能为空");
        }
        if (cmd.executedAt() == null) {
            throw BusinessException.badRequest("执行时间不能为空");
        }
        if (cmd.measurement() == null) {
            throw BusinessException.badRequest("测量值不能为空");
        }
    }

    @Transactional(readOnly = true)
    public TestResult getByResultNo(String resultNo) {
        return testResultRepository.findByResultNo(resultNo)
                .orElseThrow(() -> BusinessException.notFound("结果不存在: " + resultNo));
    }

    /**
     * 对外发布。仅 ISSUED / CONFIRMED_VALID 状态可发布；
     * 通过条件更新实现"检查并置位"的原子性——与失效标记、复核并发时，
     * 尚未解决的待复核结果不会被发布。重复发布是幂等操作。
     */
    @Transactional
    public TestResult publish(String resultNo) {
        TestResult result = getByResultNo(resultNo);
        if (result.isPublished()) {
            return result;
        }
        int updated = testResultRepository.markPublished(result.getId());
        if (updated == 0) {
            throw BusinessException.conflict(
                    "结果 " + resultNo + " 当前状态为 " + result.getStatus() + "，禁止对外发布");
        }
        return getByResultNo(resultNo);
    }
}
