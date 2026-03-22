package com.example.sync.admin.repository;

import com.example.sync.admin.domain.CompatibilityReportEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompatibilityReportRepository extends JpaRepository<CompatibilityReportEntity, Long> {

    List<CompatibilityReportEntity> findAllByOrderByUpdatedAtDesc();
}
