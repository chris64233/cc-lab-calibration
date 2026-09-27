package com.chris64233.labcalibration.repo;

import com.chris64233.labcalibration.domain.CalibrationCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CalibrationCertificateRepository extends JpaRepository<CalibrationCertificate, Long> {

    List<CalibrationCertificate> findByInstrumentIdOrderByVersionDesc(Long instrumentId);

    @Query("select coalesce(max(c.version), 0) from CalibrationCertificate c where c.instrument.id = :instrumentId")
    int maxVersion(@Param("instrumentId") Long instrumentId);

    /** 查找仪器在给定时刻处于有效校准状态（合格且在有效区间内）的证书，按版本倒序。 */
    @Query("select c from CalibrationCertificate c where c.instrument.id = :instrumentId "
            + "and c.conclusion = com.chris64233.labcalibration.domain.CalibrationConclusion.PASS "
            + "and c.validFrom <= :at and c.validTo >= :at order by c.version desc")
    List<CalibrationCertificate> findValidAt(@Param("instrumentId") Long instrumentId, @Param("at") Instant at);
}
