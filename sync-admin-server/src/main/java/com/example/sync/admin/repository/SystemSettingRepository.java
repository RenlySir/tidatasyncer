package com.example.sync.admin.repository;

import com.example.sync.admin.domain.SystemSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingRepository extends JpaRepository<SystemSettingEntity, String> {
}
