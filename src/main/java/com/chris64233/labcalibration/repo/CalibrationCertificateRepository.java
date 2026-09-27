package com.chris64233.labcalibration.repo;

import com.chris64233.labcalibration.domain.CalibrationCertificate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CalibrationCertificateRepository extends JpaRepository<CalibrationCertificate, Long> {

    List<CalibrationCertificate> findByInstrumentIdOrderByVersionDesc(Long instrumentId);

    /**
     * 悲观写锁读取证书。签发结果与登记失效区间都先锁定证书行，
     * 保证"签发时校准有效"与"失效影响圈定"两个判定不会互相穿越。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CalibrationCertificate c where c.id = :id")
    Optional<CalibrationCertificate> findByIdForUpdate(@Param("id") Long id);

    @Query("select coalesce(max(c.version), 0) from CalibrationCertificate c where c.instrument.id = :instrumentId")
    int maxVersionOf(@Param("instrumentId") Long instrumentId);

    /** 查找某时刻处于有效区间且结论为 PASS 的证书版本，按版本号倒序（取首个即最新）。 */
    @Query("""
            select c from CalibrationCertificate c
            where c.instrument.id = :instrumentId
              and c.validFrom <= :at and c.validTo >= :at
              and c.conclusion = com.chris64233.labcalibration.domain.CalibrationConclusion.PASS
            order by c.version desc
            """)
    List<CalibrationCertificate> findValidPassCertificatesAt(@Param("instrumentId") Long instrumentId,
                                                             @Param("at") Instant at);
}
