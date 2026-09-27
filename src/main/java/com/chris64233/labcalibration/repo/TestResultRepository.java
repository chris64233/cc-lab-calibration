package com.chris64233.labcalibration.repo;

import com.chris64233.labcalibration.domain.TestResult;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TestResultRepository extends JpaRepository<TestResult, Long> {

    Optional<TestResult> findByResultNo(String resultNo);

    /** 悲观写锁：发布与复核共用，保证并发互斥。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TestResult r where r.resultNo = :resultNo")
    Optional<TestResult> findByResultNoForUpdate(@Param("resultNo") String resultNo);

    /** 影响范围：依赖指定证书且执行时间落在失效区间内的全部结果。 */
    @Query("select r from TestResult r where r.certificate.id = :certificateId "
            + "and r.executedAt >= :from and r.executedAt <= :to order by r.id")
    List<TestResult> findAffected(@Param("certificateId") Long certificateId,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to);

    /**
     * 一次性将受影响结果置为待复核。
     * 仅覆盖 ISSUED / CONFIRMED_VALID / PUBLISHED 状态：
     * 已是待复核的不重复标记，已被替代的结果视为已解决不再触碰。
     */
    @Modifying
    @Query("update TestResult r set r.status = com.chris64233.labcalibration.domain.ResultStatus.PENDING_REVIEW "
            + "where r.id in :ids and r.status in ("
            + "com.chris64233.labcalibration.domain.ResultStatus.ISSUED, "
            + "com.chris64233.labcalibration.domain.ResultStatus.CONFIRMED_VALID, "
            + "com.chris64233.labcalibration.domain.ResultStatus.PUBLISHED)")
    int markPendingReview(@Param("ids") Collection<Long> ids);
}
