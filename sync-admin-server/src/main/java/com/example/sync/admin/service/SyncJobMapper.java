package com.example.sync.admin.service;

import com.example.sync.admin.domain.SyncJobEntity;
import com.example.sync.admin.dto.SyncJobResponse;
import com.example.sync.core.config.SyncJobDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class SyncJobMapper {

    private final ObjectMapper objectMapper;

    public SyncJobMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(SyncJobDefinition definition) {
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize job definition", ex);
        }
    }

    public SyncJobDefinition toDefinition(SyncJobEntity entity) {
        try {
            return objectMapper.readValue(entity.getDefinitionJson(), SyncJobDefinition.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize job definition", ex);
        }
    }

    public SyncJobResponse toResponse(SyncJobEntity entity) {
        return new SyncJobResponse(
                entity.getId(),
                entity.getName(),
                entity.getSyncMode(),
                entity.getStatus(),
                entity.getPhase(),
                entity.getProgressPercent(),
                entity.getLastMessage(),
                entity.getLastError(),
                entity.getLastLagMillis(),
                entity.getLatestCatalog(),
                entity.getLatestSchema(),
                entity.getLatestTable(),
                entity.getLatestPrimaryKey(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getStartedAt(),
                entity.getStoppedAt()
        );
    }
}
