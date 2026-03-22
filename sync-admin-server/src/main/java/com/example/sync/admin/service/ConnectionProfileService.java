package com.example.sync.admin.service;

import com.example.sync.admin.domain.ConnectionProfileEntity;
import com.example.sync.admin.domain.ConnectionProfileRole;
import com.example.sync.admin.dto.ConnectionProfileResponse;
import com.example.sync.admin.dto.ConnectionProfileUpsertRequest;
import com.example.sync.admin.repository.ConnectionProfileRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConnectionProfileService {

    private final ConnectionProfileRepository repository;

    public ConnectionProfileService(ConnectionProfileRepository repository) {
        this.repository = repository;
    }

    public List<ConnectionProfileResponse> list(ConnectionProfileRole role) {
        List<ConnectionProfileEntity> entities = role == null
                ? repository.findAll().stream().sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt())).toList()
                : repository.findByRoleOrderByUpdatedAtDesc(role);
        return entities.stream().map(this::toResponse).toList();
    }

    public ConnectionProfileResponse get(Long id) {
        return toResponse(findEntity(id));
    }

    public ConnectionProfileResponse create(ConnectionProfileUpsertRequest request) {
        ConnectionProfileEntity entity = new ConnectionProfileEntity();
        apply(entity, request);
        return toResponse(repository.save(entity));
    }

    public ConnectionProfileResponse update(Long id, ConnectionProfileUpsertRequest request) {
        ConnectionProfileEntity entity = findEntity(id);
        apply(entity, request);
        return toResponse(repository.save(entity));
    }

    public ConnectionProfileEntity findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Connection profile not found: " + id));
    }

    private void apply(ConnectionProfileEntity entity, ConnectionProfileUpsertRequest request) {
        entity.setName(request.name());
        entity.setRole(request.role());
        entity.setDatabaseType(request.databaseType());
        entity.setHost(request.host());
        entity.setPort(request.port());
        entity.setDatabaseName(request.databaseName());
        entity.setSchemaName(request.schemaName());
        entity.setUsername(request.username());
        entity.setPassword(request.password());
        entity.setJdbcUrl(request.jdbcUrl());
        entity.setJdbcParameters(request.jdbcParameters());
        entity.setCsvDirectory(request.csvDirectory());
        entity.setPermissionNote(request.permissionNote());
        entity.setTidbStatusPort(request.tidbStatusPort());
    }

    private ConnectionProfileResponse toResponse(ConnectionProfileEntity entity) {
        return new ConnectionProfileResponse(
                entity.getId(),
                entity.getName(),
                entity.getRole(),
                entity.getDatabaseType(),
                entity.getHost(),
                entity.getPort(),
                entity.getDatabaseName(),
                entity.getSchemaName(),
                entity.getUsername(),
                entity.getPassword(),
                entity.getJdbcUrl(),
                entity.getJdbcParameters(),
                entity.getCsvDirectory(),
                entity.getPermissionNote(),
                entity.getTidbStatusPort(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
