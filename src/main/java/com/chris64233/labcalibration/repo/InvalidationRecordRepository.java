package com.chris64233.labcalibration.repo;

import com.chris64233.labcalibration.domain.InvalidationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface InvalidationRecordRepository extends JpaRepository<InvalidationRecord, Long> {

    List<InvalidationRecord> findByCertificateIdOrderByRegisteredAtAsc(Long certificateId);

    /** 判断某证书是否存在覆盖指定时刻的失效登记。 */
    @Query("select count(r) > 0 from InvalidationRecord r where r.certificate.id = :certificateId "
            + "and r.invalidFrom <= :at and r.invalidTo >= :at")
    boolean existsCovering(@Param("certificateId") Long certificateId, @Param("at") Instant at);
}
