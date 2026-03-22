package com.example.sync.admin.service;

import com.example.sync.admin.domain.SystemSettingEntity;
import com.example.sync.admin.dto.SystemSettingsResponse;
import com.example.sync.admin.dto.SystemSettingsUpdateRequest;
import com.example.sync.admin.repository.SystemSettingRepository;
import com.example.sync.core.model.DeploymentArchitecture;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemSettingsService {

    static final String DEPLOYMENT_ARCHITECTURE_KEY = "deploymentArchitecture";

    private final SystemSettingRepository repository;

    public SystemSettingsService(SystemSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public SystemSettingsResponse getSettings() {
        SystemSettingEntity entity = repository.findById(DEPLOYMENT_ARCHITECTURE_KEY).orElse(null);
        if (entity == null) {
            return new SystemSettingsResponse(DeploymentArchitecture.AMD64, null);
        }
        return new SystemSettingsResponse(
                DeploymentArchitecture.valueOf(entity.getSettingValue()),
                entity.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public DeploymentArchitecture getDeploymentArchitecture() {
        return getSettings().deploymentArchitecture();
    }

    @Transactional
    public SystemSettingsResponse updateSettings(SystemSettingsUpdateRequest request) {
        SystemSettingEntity entity = repository.findById(DEPLOYMENT_ARCHITECTURE_KEY)
                .orElseGet(() -> {
                    SystemSettingEntity created = new SystemSettingEntity();
                    created.setSettingKey(DEPLOYMENT_ARCHITECTURE_KEY);
                    return created;
                });
        entity.setSettingValue(request.deploymentArchitecture().name());
        SystemSettingEntity saved = repository.save(entity);
        return new SystemSettingsResponse(
                DeploymentArchitecture.valueOf(saved.getSettingValue()),
                saved.getUpdatedAt()
        );
    }
}
