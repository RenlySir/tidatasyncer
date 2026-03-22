package com.example.sync.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.sync.admin.dto.CsvDirectoryImportRequest;
import com.example.sync.admin.dto.CsvDirectoryImportResponse;
import com.example.sync.admin.dto.CsvDirectoryPrepareResponse;
import com.example.sync.connectors.importer.TiDbLightningImporter;
import com.example.sync.connectors.util.CsvDirectoryPreprocessor;
import com.example.sync.core.config.TargetConnectionProperties;
import com.example.sync.core.model.DeploymentArchitecture;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvDirectoryImportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void prepareShouldSplitOversizedLightningCsvIntoChunks() throws Exception {
        TiDbLightningImporter importer = mock(TiDbLightningImporter.class);
        CsvDirectoryImportService service = new CsvDirectoryImportService(importer, new CsvDirectoryPreprocessor());
        Path csvFile = tempDir.resolve("target_db.orders.00000001.csv");
        Files.writeString(
                csvFile,
                "id,name\n1,alice\n2,bob\n3,carol\n",
                StandardCharsets.UTF_8
        );

        CsvDirectoryPrepareResponse response = service.prepare(tempDir, 20L, 16L);

        assertThat(response.convertedCharsetFiles()).isEqualTo(0);
        assertThat(response.splitSourceFiles()).isEqualTo(1);
        assertThat(response.generatedChunkFiles()).isGreaterThanOrEqualTo(2);
        assertThat(response.csvFiles()).contains("target_db.orders.00000001.csv");
        assertThat(response.csvFiles()).contains("target_db.orders.00000002.csv");
    }

    @Test
    void prepareShouldRejectInvalidLightningFileName() throws Exception {
        TiDbLightningImporter importer = mock(TiDbLightningImporter.class);
        CsvDirectoryImportService service = new CsvDirectoryImportService(importer, new CsvDirectoryPreprocessor());
        Files.writeString(tempDir.resolve("orders.csv"), "id\n1\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.prepare(tempDir, 20L, 16L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TiDB Lightning format");
    }

    @Test
    void importDirectoryShouldInvokeLightningImporter() throws Exception {
        TiDbLightningImporter importer = mock(TiDbLightningImporter.class);
        doNothing().when(importer).importCsv(any(), any(), any());
        CsvDirectoryImportService service = new CsvDirectoryImportService(importer, new CsvDirectoryPreprocessor());
        Files.writeString(tempDir.resolve("target_db.orders.00000001.csv"), "id\n1\n", StandardCharsets.UTF_8);

        CsvDirectoryImportResponse response = service.importDirectory(new CsvDirectoryImportRequest(
                tempDir.toString(),
                DeploymentArchitecture.AMD64,
                new TargetConnectionProperties(
                        "127.0.0.1",
                        4000,
                        "target_db",
                        "root",
                        "root",
                        "jdbc:mysql://127.0.0.1:4000/target_db",
                        "",
                        "tidb-lightning"
                )
        ));

        verify(importer).importCsv(any(), any(), any());
        assertThat(response.importedCsvFiles()).isEqualTo(1);
    }

    @Test
    void importDirectoryShouldRejectNonUtf8CsvBeforePreparation() throws Exception {
        TiDbLightningImporter importer = mock(TiDbLightningImporter.class);
        CsvDirectoryImportService service = new CsvDirectoryImportService(importer, new CsvDirectoryPreprocessor());
        byte[] latin1Content = new byte[] {
                'i', 'd', '\n',
                '1', '\n',
                'c', 'a', 'f', (byte) 0xE9, '\n'
        };
        Files.write(tempDir.resolve("target_db.orders.00000001.csv"), latin1Content);

        assertThatThrownBy(() -> service.importDirectory(new CsvDirectoryImportRequest(
                tempDir.toString(),
                DeploymentArchitecture.AMD64,
                new TargetConnectionProperties(
                        "127.0.0.1",
                        4000,
                        "target_db",
                        "root",
                        "root",
                        "jdbc:mysql://127.0.0.1:4000/target_db",
                        "",
                        "tidb-lightning"
                )
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("charset must be UTF-8");
    }
}
