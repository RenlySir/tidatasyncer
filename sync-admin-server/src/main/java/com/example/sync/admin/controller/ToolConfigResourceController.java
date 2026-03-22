package com.example.sync.admin.controller;

import com.example.sync.admin.dto.ToolConfigResponse;
import com.example.sync.admin.dto.ToolConfigUpsertRequest;
import com.example.sync.admin.service.ToolConfigService;
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
@RequestMapping("/api/tool-configs")
public class ToolConfigResourceController {

    private final ToolConfigService service;

    public ToolConfigResourceController(ToolConfigService service) {
        this.service = service;
    }

    @GetMapping
    public List<ToolConfigResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ToolConfigResponse get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @PostMapping
    public ToolConfigResponse create(@Valid @RequestBody ToolConfigUpsertRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ToolConfigResponse update(@PathVariable("id") Long id, @Valid @RequestBody ToolConfigUpsertRequest request) {
        return service.update(id, request);
    }
}
