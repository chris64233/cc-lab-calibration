package com.chris64233.labcalibration.repo;

import com.chris64233.labcalibration.domain.ReviewRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRecordRepository extends JpaRepository<ReviewRecord, Long> {

    List<ReviewRecord> findByOriginalResultIdOrderByDecidedAtAsc(Long originalResultId);
}
