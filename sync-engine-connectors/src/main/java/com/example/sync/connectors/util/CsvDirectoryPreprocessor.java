package com.example.sync.connectors.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.stereotype.Component;

@Component
public class CsvDirectoryPreprocessor {

    public static final long SPLIT_TRIGGER_SIZE_BYTES = 200L * 1024L * 1024L;
    public static final long CHUNK_SIZE_BYTES = 128L * 1024L * 1024L;
    private static final Pattern LIGHTNING_NUMBERED_FILE = Pattern.compile("^([^.]+)\\.([^.]+)\\.(\\d{8})\\.csv$");

    public PreparedCsvDirectory prepare(Path directory) throws IOException {
        return prepare(directory, SPLIT_TRIGGER_SIZE_BYTES, CHUNK_SIZE_BYTES);
    }

    public PreparedCsvDirectory prepare(Path directory, long splitTriggerSizeBytes, long chunkSizeBytes) throws IOException {
        List<Path> csvFiles = listCsvFiles(directory);
        if (csvFiles.isEmpty()) {
            throw new IllegalArgumentException("No CSV files found in directory: " + directory);
        }

        int convertedCharsetFiles = 0;
        int splitSourceFiles = 0;
        int generatedChunkFiles = 0;
        int unchangedFiles = 0;

        for (Path csvFile : csvFiles) {
            validateLightningFileName(csvFile.getFileName().toString());

            boolean converted = ensureUtf8(csvFile);
            if (converted) {
                convertedCharsetFiles++;
            }

            long size = Files.size(csvFile);
            if (size > splitTriggerSizeBytes) {
                ParsedLightningFile parsed = parseNumberedLightningFile(csvFile.getFileName().toString());
                List<Path> chunks = CsvSplitter.splitForLightning(
                        csvFile,
                        directory,
                        parsed.databaseName(),
                        parsed.tableName(),
                        chunkSizeBytes
                );
                splitSourceFiles++;
                generatedChunkFiles += chunks.size();
            } else if (!converted) {
                unchangedFiles++;
            }
        }

        List<Path> finalFiles = listPreparedCsvFiles(directory);
        return new PreparedCsvDirectory(
                directory,
                finalFiles.size(),
                convertedCharsetFiles,
                splitSourceFiles,
                generatedChunkFiles,
                unchangedFiles,
                finalFiles
        );
    }

    public void validatePreparedForImport(Path directory) throws IOException {
        listPreparedCsvFiles(directory);
    }

    public List<Path> listPreparedCsvFiles(Path directory) throws IOException {
        List<Path> csvFiles = listCsvFiles(directory);
        if (csvFiles.isEmpty()) {
            throw new IllegalArgumentException("No CSV files found in directory: " + directory);
        }
        for (Path csvFile : csvFiles) {
            validateLightningFileName(csvFile.getFileName().toString());
            Charset detectedCharset = detectCharset(csvFile);
            if (!StandardCharsets.UTF_8.equals(detectedCharset)) {
                throw new IllegalStateException("CSV file charset must be UTF-8 before import: " + csvFile.getFileName());
            }
            if (Files.size(csvFile) > SPLIT_TRIGGER_SIZE_BYTES) {
                throw new IllegalStateException("CSV file exceeds 200 MiB and must be prepared before import: " + csvFile.getFileName());
            }
        }
        return csvFiles;
    }

    private boolean ensureUtf8(Path csvFile) throws IOException {
        Charset detectedCharset = detectCharset(csvFile);
        if (detectedCharset == null || StandardCharsets.UTF_8.equals(detectedCharset)) {
            return false;
        }
        Path tempFile = Files.createTempFile(csvFile.getParent(), ".charset-", ".csv");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(csvFile), detectedCharset));
             BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                writer.write(buffer, 0, read);
            }
        }
        Files.move(tempFile, csvFile, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    Charset detectCharset(Path csvFile) throws IOException {
        try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(csvFile))) {
            UniversalDetector detector = new UniversalDetector(null);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) > 0 && !detector.isDone()) {
                detector.handleData(buffer, 0, read);
            }
            detector.dataEnd();
            String detected = detector.getDetectedCharset();
            detector.reset();
            if (detected == null || detected.isBlank()) {
                return StandardCharsets.UTF_8;
            }
            String normalized = detected.trim().toUpperCase(Locale.ROOT);
            if ("UTF-8".equals(normalized)
                    || "UTF8".equals(normalized)
                    || "US-ASCII".equals(normalized)
                    || "ASCII".equals(normalized)) {
                return StandardCharsets.UTF_8;
            }
            return Charset.forName(detected);
        } catch (Exception ex) {
            return StandardCharsets.UTF_8;
        }
    }

    private List<Path> listCsvFiles(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private void validateLightningFileName(String fileName) {
        if (LIGHTNING_NUMBERED_FILE.matcher(fileName).matches()) {
            return;
        }
        throw new IllegalArgumentException(
                "CSV file name must match TiDB Lightning format db.table.00000001.csv: " + fileName
        );
    }

    private ParsedLightningFile parseNumberedLightningFile(String fileName) {
        Matcher matcher = LIGHTNING_NUMBERED_FILE.matcher(fileName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid TiDB Lightning CSV file name: " + fileName);
        }
        return new ParsedLightningFile(matcher.group(1), matcher.group(2));
    }

    private record ParsedLightningFile(String databaseName, String tableName) {
    }
}
