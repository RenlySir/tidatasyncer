package com.example.sync.admin.repository;

import com.example.sync.admin.domain.SyncJobEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncJobRepository extends JpaRepository<SyncJobEntity, Long> {

    Optional<SyncJobEntity> findByName(String name);
}
