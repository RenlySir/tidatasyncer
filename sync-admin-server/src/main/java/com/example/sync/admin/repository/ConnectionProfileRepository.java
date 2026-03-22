package com.example.sync.admin.repository;

import com.example.sync.admin.domain.ConnectionProfileEntity;
import com.example.sync.admin.domain.ConnectionProfileRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectionProfileRepository extends JpaRepository<ConnectionProfileEntity, Long> {

    List<ConnectionProfileEntity> findByRoleOrderByUpdatedAtDesc(ConnectionProfileRole role);
}
