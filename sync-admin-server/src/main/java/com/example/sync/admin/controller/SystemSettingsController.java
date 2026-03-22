package com.example.sync.admin.controller;

import com.example.sync.admin.dto.SystemSettingsResponse;
import com.example.sync.admin.dto.SystemSettingsUpdateRequest;
import com.example.sync.admin.service.SystemSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/system")
public class SystemSettingsController {

    private final SystemSettingsService systemSettingsService;

    public SystemSettingsController(SystemSettingsService systemSettingsService) {
        this.systemSettingsService = systemSettingsService;
    }

    @GetMapping
    public SystemSettingsResponse getSettings() {
        return systemSettingsService.getSettings();
    }

    @PutMapping
    public SystemSettingsResponse updateSettings(@RequestBody SystemSettingsUpdateRequest request) {
        return systemSettingsService.updateSettings(request);
    }
}
