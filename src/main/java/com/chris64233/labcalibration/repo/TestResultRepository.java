package com.chris64233.labcalibration.repo;

import com.chris64233.labcalibration.domain.ResultStatus;
import com.chris64233.labcalibration.domain.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TestResultRepository extends JpaRepository<TestResult, Long> {

    Optional<TestResult> findByRequestId(String requestId);

    Optional<TestResult> findByResultNo(String resultNo);

    List<TestResult> findByCertificateIdOrderByExecutedAtAsc(Long certificateId);

    List<TestResult> findByCertificateIdAndExecutedAtBetweenOrderByExecutedAtAsc(
            Long certificateId, Instant from, Instant to);

    /**
     * 失效影响圈定：把指定证书在失效区间内、当前仍处于 ISSUED 状态的结果
     * 一次性批量置为 PENDING_REVIEW。单条 UPDATE 保证原子性，
     * 状态前置条件保证不遗漏（区间内全部命中）且不重复标记（已流转的结果不受影响）。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TestResult r set r.status = com.chris64233.labcalibration.domain.ResultStatus.PENDING_REVIEW
            where r.certificate.id = :certificateId
              and r.executedAt >= :from and r.executedAt <= :to
              and r.status = com.chris64233.labcalibration.domain.ResultStatus.ISSUED
            """)
    int bulkMarkPendingReview(@Param("certificateId") Long certificateId,
                              @Param("from") Instant from,
                              @Param("to") Instant to);

    /**
     * 状态机迁移：仅当当前状态等于期望值时才更新，返回受影响行数。
     * 用于复核处置（PENDING_REVIEW -> CONFIRMED_VALID / SUPERSEDED），
     * 并发下同一结果只会被成功处置一次。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TestResult r set r.status = :to where r.id = :id and r.status = :from")
    int transitionStatus(@Param("id") Long id,
                         @Param("from") ResultStatus from,
                         @Param("to") ResultStatus to);

    /**
     * 对外发布：仅当结果处于可发布状态（ISSUED / CONFIRMED_VALID）且尚未发布时才更新。
     * 与失效标记 / 复核并发时，条件不满足则更新 0 行，保证未解决的结果不会被发布。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TestResult r set r.published = true
            where r.id = :id and r.published = false
              and r.status in (com.chris64233.labcalibration.domain.ResultStatus.ISSUED,
                               com.chris64233.labcalibration.domain.ResultStatus.CONFIRMED_VALID)
            """)
    int markPublished(@Param("id") Long id);
}
