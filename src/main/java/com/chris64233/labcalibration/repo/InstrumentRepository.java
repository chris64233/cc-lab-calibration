package com.chris64233.labcalibration.repo;

import com.chris64233.labcalibration.domain.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InstrumentRepository extends JpaRepository<Instrument, Long> {

    Optional<Instrument> findByCode(String code);

    boolean existsByCode(String code);
}
