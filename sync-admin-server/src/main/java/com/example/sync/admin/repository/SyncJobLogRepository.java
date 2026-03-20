package com.example.sync.admin.repository;

import com.example.sync.admin.domain.SyncJobLogEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncJobLogRepository extends JpaRepository<SyncJobLogEntity, Long> {

    List<SyncJobLogEntity> findTop200ByJobIdOrderByCreatedAtDesc(Long jobId);
}
