package com.example.sync.admin.controller;

import com.example.sync.admin.dto.CompatibilityReportResponse;
import com.example.sync.admin.dto.CompatibilityReportUpsertRequest;
import com.example.sync.admin.service.CompatibilityReportService;
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
@RequestMapping("/api/compatibility-reports")
public class CompatibilityReportController {

    private final CompatibilityReportService service;

    public CompatibilityReportController(CompatibilityReportService service) {
        this.service = service;
    }

    @GetMapping
    public List<CompatibilityReportResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public CompatibilityReportResponse get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @PostMapping
    public CompatibilityReportResponse create(@Valid @RequestBody CompatibilityReportUpsertRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public CompatibilityReportResponse update(
            @PathVariable("id") Long id,
            @Valid @RequestBody CompatibilityReportUpsertRequest request
    ) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/execute")
    public CompatibilityReportResponse execute(@PathVariable("id") Long id) {
        return service.execute(id);
    }
}
