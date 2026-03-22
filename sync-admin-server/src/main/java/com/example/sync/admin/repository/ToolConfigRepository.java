package com.example.sync.admin.repository;

import com.example.sync.admin.domain.ToolConfigEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolConfigRepository extends JpaRepository<ToolConfigEntity, Long> {

    List<ToolConfigEntity> findAllByOrderByUpdatedAtDesc();
}
