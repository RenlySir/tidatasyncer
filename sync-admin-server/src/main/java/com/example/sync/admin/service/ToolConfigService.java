package com.example.sync.admin.service;

import com.example.sync.admin.domain.DatabaseEndpointType;
import com.example.sync.admin.domain.ToolConfigEntity;
import com.example.sync.admin.dto.ToolConfigResponse;
import com.example.sync.admin.dto.ToolConfigUpsertRequest;
import com.example.sync.admin.repository.ToolConfigRepository;
import com.example.sync.connectors.util.ProjectManagedToolResolver;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ToolConfigService {

    private final ToolConfigRepository repository;
    private final SystemSettingsService systemSettingsService;

    public ToolConfigService(ToolConfigRepository repository, SystemSettingsService systemSettingsService) {
        this.repository = repository;
        this.systemSettingsService = systemSettingsService;
    }

    public List<ToolConfigResponse> list() {
        return repository.findAllByOrderByUpdatedAtDesc().stream().map(this::toResponse).toList();
    }

    public ToolConfigResponse get(Long id) {
        return toResponse(findEntity(id));
    }

    public ToolConfigResponse create(ToolConfigUpsertRequest request) {
        ToolConfigEntity entity = new ToolConfigEntity();
        apply(entity, request);
        return toResponse(repository.save(entity));
    }

    public ToolConfigResponse update(Long id, ToolConfigUpsertRequest request) {
        ToolConfigEntity entity = findEntity(id);
        apply(entity, request);
        return toResponse(repository.save(entity));
    }

    public ToolConfigEntity findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tool config not found: " + id));
    }

    private void apply(ToolConfigEntity entity, ToolConfigUpsertRequest request) {
        entity.setName(request.name());
        entity.setDatabaseType(request.databaseType());
        entity.setExportToolBinary(defaultIfBlank(request.exportToolBinary(), request.databaseType()));
        entity.setLightningBinary(defaultLightningIfBlank(request.lightningBinary()));
        entity.setNotes(request.notes());
    }

    private ToolConfigResponse toResponse(ToolConfigEntity entity) {
        return new ToolConfigResponse(
                entity.getId(),
                entity.getName(),
                entity.getDatabaseType(),
                entity.getExportToolBinary(),
                entity.getLightningBinary(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String defaultIfBlank(String value, DatabaseEndpointType databaseType) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        try {
            return switch (databaseType) {
                case ORACLE -> ProjectManagedToolResolver.resolveSqluldr2Binary(null, systemSettingsService.getDeploymentArchitecture());
                case MYSQL -> ProjectManagedToolResolver.resolveDumplingBinary(null, systemSettingsService.getDeploymentArchitecture());
                case MARIADB -> "mariadb-dump";
                case POSTGRESQL -> "psql";
                case SQLSERVER -> "bcp";
                case DB2 -> "db2";
                case HANA -> "hdbsql";
                case MONGODB -> "mongoexport";
                case CSV, TIDB -> "";
            };
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to resolve default tool path for " + databaseType, ex);
        }
    }

    private String defaultLightningIfBlank(String value) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        try {
            return ProjectManagedToolResolver.resolveTidbLightningBinary(null, systemSettingsService.getDeploymentArchitecture());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to resolve TiDB Lightning path", ex);
        }
    }
}
