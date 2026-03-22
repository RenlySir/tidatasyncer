package com.example.sync.admin.repository;

import com.example.sync.admin.domain.SchemaSyncTaskEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchemaSyncTaskRepository extends JpaRepository<SchemaSyncTaskEntity, Long> {

    List<SchemaSyncTaskEntity> findAllByOrderByUpdatedAtDesc();
}
