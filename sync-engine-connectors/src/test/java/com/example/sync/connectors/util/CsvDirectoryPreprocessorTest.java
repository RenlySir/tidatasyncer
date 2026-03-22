package com.example.sync.connectors.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvDirectoryPreprocessorTest {

    private final CsvDirectoryPreprocessor preprocessor = new CsvDirectoryPreprocessor();

    @TempDir
    Path tempDir;

    @Test
    void shouldConvertNonUtf8CsvToUtf8() throws Exception {
        Path csvFile = tempDir.resolve("db1.tab1.00000001.csv");
        byte[] latin1Content = new byte[] {
                'i', 'd', ',', 'n', 'a', 'm', 'e', '\n',
                '1', ',', 'c', 'a', 'f', (byte) 0xE9, '\n'
        };
        Files.write(csvFile, latin1Content);

        PreparedCsvDirectory prepared = preprocessor.prepare(tempDir, Long.MAX_VALUE, 1024L);

        assertThat(prepared.convertedCharsetFiles()).isEqualTo(1);
        String content = Files.readString(csvFile, StandardCharsets.UTF_8);
        assertThat(content).contains("caf\u00E9");
    }
}
