package com.example.sync.admin.controller;

import com.example.sync.admin.domain.ConnectionProfileRole;
import com.example.sync.admin.dto.ConnectionProfilePermissionCheckResponse;
import com.example.sync.admin.dto.ConnectionProfileResponse;
import com.example.sync.admin.dto.ConnectionProfileUpsertRequest;
import com.example.sync.admin.service.ConnectionProfilePermissionCheckService;
import com.example.sync.admin.service.ConnectionProfileService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/connection-profiles")
public class ConnectionProfileController {

    private final ConnectionProfileService service;
    private final ConnectionProfilePermissionCheckService permissionCheckService;

    public ConnectionProfileController(
            ConnectionProfileService service,
            ConnectionProfilePermissionCheckService permissionCheckService
    ) {
        this.service = service;
        this.permissionCheckService = permissionCheckService;
    }

    @GetMapping
    public List<ConnectionProfileResponse> list(
            @RequestParam(value = "role", required = false) ConnectionProfileRole role
    ) {
        return service.list(role);
    }

    @GetMapping("/{id}")
    public ConnectionProfileResponse get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/permission-check")
    public ConnectionProfilePermissionCheckResponse checkPermissions(@PathVariable("id") Long id) {
        return permissionCheckService.check(id);
    }

    @PostMapping
    public ConnectionProfileResponse create(@Valid @RequestBody ConnectionProfileUpsertRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ConnectionProfileResponse update(@PathVariable("id") Long id, @Valid @RequestBody ConnectionProfileUpsertRequest request) {
        return service.update(id, request);
    }
}
