package com.example.sync.admin.controller;

import com.example.sync.admin.dto.RuntimeLogFileResponse;
import com.example.sync.admin.dto.RuntimeLogTailResponse;
import com.example.sync.admin.service.RuntimeLogService;
import java.io.IOException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runtime-logs")
public class RuntimeLogController {

    private final RuntimeLogService runtimeLogService;

    public RuntimeLogController(RuntimeLogService runtimeLogService) {
        this.runtimeLogService = runtimeLogService;
    }

    @GetMapping("/files")
    public List<RuntimeLogFileResponse> listFiles() {
        return runtimeLogService.listFiles();
    }

    @GetMapping("/tail")
    public RuntimeLogTailResponse tail(
            @RequestParam("key") String key,
            @RequestParam(value = "lines", defaultValue = "200") int lines
    ) throws IOException {
        return runtimeLogService.tail(key, lines);
    }
}
