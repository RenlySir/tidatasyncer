package com.example.sync.admin.controller;

import com.example.sync.admin.dto.SyncJobLogResponse;
import com.example.sync.admin.dto.SyncJobResponse;
import com.example.sync.admin.dto.SyncJobDefinitionResponse;
import com.example.sync.admin.dto.SyncJobUpsertRequest;
import com.example.sync.admin.service.SyncJobRuntimeManager;
import com.example.sync.admin.service.SyncJobService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class SyncJobController {

    private final SyncJobService jobService;
    private final SyncJobRuntimeManager runtimeManager;

    public SyncJobController(SyncJobService jobService, SyncJobRuntimeManager runtimeManager) {
        this.jobService = jobService;
        this.runtimeManager = runtimeManager;
    }

    @GetMapping
    public List<SyncJobResponse> list() {
        return jobService.list();
    }

    @GetMapping("/{id}")
    public SyncJobResponse get(@PathVariable("id") Long id) {
        return jobService.get(id);
    }

    @GetMapping("/{id}/definition")
    public SyncJobDefinitionResponse getDefinition(@PathVariable("id") Long id) {
        return jobService.getDefinition(id);
    }

    @PostMapping
    public SyncJobResponse create(@Valid @RequestBody SyncJobUpsertRequest request) {
        return jobService.create(request);
    }

    @PutMapping("/{id}")
    public SyncJobResponse update(@PathVariable("id") Long id, @Valid @RequestBody SyncJobUpsertRequest request) {
        return jobService.update(id, request);
    }

    @PostMapping("/{id}/start")
    public void start(@PathVariable("id") Long id) {
        runtimeManager.start(id);
    }

    @PostMapping("/{id}/stop")
    public void stop(@PathVariable("id") Long id) {
        runtimeManager.stop(id);
    }

    @GetMapping("/{id}/logs")
    public List<SyncJobLogResponse> logs(@PathVariable("id") Long id) {
        return jobService.getLogs(id);
    }
}
