package com.example.sync.admin.controller;

import com.example.sync.admin.dto.CsvDirectoryImportRequest;
import com.example.sync.admin.dto.CsvDirectoryImportResponse;
import com.example.sync.admin.dto.CsvDirectoryPrepareRequest;
import com.example.sync.admin.dto.CsvDirectoryPrepareResponse;
import com.example.sync.admin.service.CsvDirectoryImportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/csv-import")
public class CsvImportController {

    private final CsvDirectoryImportService csvDirectoryImportService;

    public CsvImportController(CsvDirectoryImportService csvDirectoryImportService) {
        this.csvDirectoryImportService = csvDirectoryImportService;
    }

    @PostMapping("/prepare")
    public CsvDirectoryPrepareResponse prepare(@Valid @RequestBody CsvDirectoryPrepareRequest request) throws Exception {
        return csvDirectoryImportService.prepare(request);
    }

    @PostMapping("/start")
    public CsvDirectoryImportResponse start(@Valid @RequestBody CsvDirectoryImportRequest request) throws Exception {
        return csvDirectoryImportService.importDirectory(request);
    }
}
