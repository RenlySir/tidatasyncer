package com.example.sync.admin.service;

import com.example.sync.admin.domain.SyncJobEntity;
import com.example.sync.admin.dto.SyncJobLogResponse;
import com.example.sync.admin.dto.SyncJobResponse;
import com.example.sync.admin.dto.SyncJobDefinitionResponse;
import com.example.sync.admin.dto.SyncJobUpsertRequest;
import com.example.sync.admin.repository.SyncJobLogRepository;
import com.example.sync.admin.repository.SyncJobRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncJobService {

    private final SyncJobRepository jobRepository;
    private final SyncJobLogRepository logRepository;
    private final SyncJobMapper mapper;

    public SyncJobService(SyncJobRepository jobRepository, SyncJobLogRepository logRepository, SyncJobMapper mapper) {
        this.jobRepository = jobRepository;
        this.logRepository = logRepository;
        this.mapper = mapper;
    }

    @Transactional
    public SyncJobResponse create(SyncJobUpsertRequest request) {
        SyncJobEntity entity = new SyncJobEntity();
        entity.setName(request.name());
        entity.setSyncMode(request.definition().syncMode());
        entity.setDefinitionJson(mapper.toJson(request.definition()));
        return mapper.toResponse(jobRepository.save(entity));
    }

    @Transactional
    public SyncJobResponse update(Long id, SyncJobUpsertRequest request) {
        SyncJobEntity entity = getEntity(id);
        entity.setName(request.name());
        entity.setSyncMode(request.definition().syncMode());
        entity.setDefinitionJson(mapper.toJson(request.definition()));
        return mapper.toResponse(jobRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<SyncJobResponse> list() {
        return jobRepository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SyncJobResponse get(Long id) {
        return mapper.toResponse(getEntity(id));
    }

    @Transactional(readOnly = true)
    public SyncJobDefinitionResponse getDefinition(Long id) {
        SyncJobEntity entity = getEntity(id);
        return new SyncJobDefinitionResponse(entity.getId(), entity.getName(), mapper.toDefinition(entity));
    }

    @Transactional(readOnly = true)
    public SyncJobEntity getEntity(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<SyncJobLogResponse> getLogs(Long jobId) {
        return logRepository.findTop200ByJobIdOrderByCreatedAtDesc(jobId).stream()
                .map(log -> new SyncJobLogResponse(log.getId(), log.getJobId(), log.getLevel(), log.getMessage(), log.getCreatedAt()))
                .toList();
    }
}
