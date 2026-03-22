package com.example.sync.admin.controller;

import com.example.sync.admin.dto.SchemaSyncTaskResponse;
import com.example.sync.admin.dto.SchemaSyncTaskUpsertRequest;
import com.example.sync.admin.service.SchemaSyncTaskService;
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
@RequestMapping("/api/schema-tasks")
public class SchemaSyncTaskController {

    private final SchemaSyncTaskService service;

    public SchemaSyncTaskController(SchemaSyncTaskService service) {
        this.service = service;
    }

    @GetMapping
    public List<SchemaSyncTaskResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public SchemaSyncTaskResponse get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @PostMapping
    public SchemaSyncTaskResponse create(@Valid @RequestBody SchemaSyncTaskUpsertRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public SchemaSyncTaskResponse update(@PathVariable("id") Long id, @Valid @RequestBody SchemaSyncTaskUpsertRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/execute")
    public SchemaSyncTaskResponse execute(@PathVariable("id") Long id) {
        return service.execute(id);
    }
}
