package com.chris64233.labcalibration.repo;

import com.chris64233.labcalibration.domain.CertificateInvalidation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CertificateInvalidationRepository extends JpaRepository<CertificateInvalidation, Long> {

    List<CertificateInvalidation> findByCertificateIdOrderByCreatedAtAsc(Long certificateId);

    /** 判断某证书是否存在覆盖指定时刻的失效区间。 */
    @Query("""
            select count(i) > 0 from CertificateInvalidation i
            where i.certificate.id = :certificateId
              and i.invalidFrom <= :at and i.invalidTo >= :at
            """)
    boolean existsCovering(@Param("certificateId") Long certificateId, @Param("at") Instant at);
}
