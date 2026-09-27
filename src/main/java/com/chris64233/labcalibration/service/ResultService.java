package com.chris64233.labcalibration.service;

import com.chris64233.labcalibration.domain.ResultStatus;
import com.chris64233.labcalibration.domain.TestResult;
import com.chris64233.labcalibration.repo.TestResultRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 检测结果签发与发布。
 */
@Service
public class ResultService {

    private final TestResultRepository resultRepository;
    private final ResultIssuanceTx issuanceTx;

    public ResultService(TestResultRepository resultRepository, ResultIssuanceTx issuanceTx) {
        this.resultRepository = resultRepository;
        this.issuanceTx = issuanceTx;
    }

    /**
     * 签发结果（幂等）。结果编号为幂等键：
     * 相同编号且载荷一致的重复请求返回已存在的结果；
     * 编号相同但载荷不一致视为冲突；并发下由数据库唯一约束兜底，不产生重复结果。
     */
    public TestResult issue(IssueResultCommand cmd) {
        return resultRepository.findByResultNo(cmd.resultNo())
                .map(existing -> verifyReplay(existing, cmd))
                .orElseGet(() -> {
                    try {
                        return issuanceTx.issueNew(cmd);
                    } catch (DataIntegrityViolationException e) {
                        // 并发签发同一编号：唯一约束拦截后重查，保证只有一个结果
                        return resultRepository.findByResultNo(cmd.resultNo())
                                .map(existing -> verifyReplay(existing, cmd))
                                .orElseThrow(() -> e);
                    }
                });
    }

    private TestResult verifyReplay(TestResult existing, IssueResultCommand cmd) {
        if (!existing.matchesPayload(cmd.instrumentCode(), cmd.sampleId(), cmd.testItem(),
                cmd.executedAt(), cmd.measuredValue())) {
            throw BusinessException.conflict("IDEMPOTENCY_PAYLOAD_MISMATCH",
                    "结果编号 " + cmd.resultNo() + " 已存在且载荷不一致");
        }
        return existing;
    }

    @Transactional(readOnly = true)
    public TestResult getByResultNo(String resultNo) {
        return resultRepository.findByResultNo(resultNo)
                .orElseThrow(() -> BusinessException.notFound("检测结果不存在: " + resultNo));
    }

    /**
     * 对外发布。对待复核/已替代结果拒绝发布；与复核共用悲观写锁，
     * 保证复核进行中不会发布尚未解决的结果。重复发布幂等返回。
     */
    @Transactional
    public TestResult publish(String resultNo) {
        TestResult result = resultRepository.findByResultNoForUpdate(resultNo)
                .orElseThrow(() -> BusinessException.notFound("检测结果不存在: " + resultNo));
        if (result.getStatus() == ResultStatus.PENDING_REVIEW) {
            throw BusinessException.conflict("RESULT_PENDING_REVIEW",
                    "结果 " + resultNo + " 处于待复核状态，禁止发布");
        }
        if (result.getStatus() == ResultStatus.REPLACED) {
            throw BusinessException.conflict("RESULT_REPLACED",
                    "结果 " + resultNo + " 已被重测结果替代，禁止发布");
        }
        result.publish(Instant.now());
        return resultRepository.save(result);
    }
}
