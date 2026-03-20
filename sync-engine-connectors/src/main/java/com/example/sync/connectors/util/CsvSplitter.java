package com.example.sync.connectors.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class CsvSplitter {

    private CsvSplitter() {
    }

    public static List<Path> splitForLightning(
            Path sourceFile,
            Path outputDir,
            String targetDatabase,
            String targetTable,
            long maxFileSizeBytes
    ) throws IOException {
        Files.createDirectories(outputDir);
        List<Path> tempFiles = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(sourceFile, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) {
                Path emptyTarget = outputDir.resolve(LightningCsvNaming.singleFileName(targetDatabase, targetTable));
                Files.move(sourceFile, emptyTarget, StandardCopyOption.REPLACE_EXISTING);
                return List.of(emptyTarget);
            }

            byte[] newline = System.lineSeparator().getBytes(StandardCharsets.UTF_8);
            long headerBytes = header.getBytes(StandardCharsets.UTF_8).length + newline.length;
            int chunkIndex = 0;
            long currentSize = 0L;
            int currentRows = 0;
            BufferedWriter writer = null;

            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    byte[] lineBytes = line.getBytes(StandardCharsets.UTF_8);
                    long rowBytes = lineBytes.length + newline.length;
                    if (writer == null || (currentRows > 0 && currentSize + rowBytes > maxFileSizeBytes)) {
                        if (writer != null) {
                            writer.close();
                        }
                        chunkIndex++;
                        Path tempFile = outputDir.resolve(".tmp-" + targetDatabase + "." + targetTable + "." + chunkIndex + ".csv");
                        tempFiles.add(tempFile);
                        writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8);
                        writer.write(header);
                        writer.newLine();
                        currentSize = headerBytes;
                        currentRows = 0;
                    }
                    writer.write(line);
                    writer.newLine();
                    currentSize += rowBytes;
                    currentRows++;
                }

                if (writer == null) {
                    chunkIndex++;
                    Path tempFile = outputDir.resolve(".tmp-" + targetDatabase + "." + targetTable + "." + chunkIndex + ".csv");
                    tempFiles.add(tempFile);
                    writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8);
                    writer.write(header);
                    writer.newLine();
                }
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
        }

        Files.deleteIfExists(sourceFile);
        return renameForLightning(tempFiles, outputDir, targetDatabase, targetTable);
    }

    private static List<Path> renameForLightning(
            List<Path> tempFiles,
            Path outputDir,
            String targetDatabase,
            String targetTable
    ) throws IOException {
        List<Path> finalFiles = new ArrayList<>();
        boolean chunked = tempFiles.size() > 1;
        for (int i = 0; i < tempFiles.size(); i++) {
            Path finalFile = outputDir.resolve(
                    chunked
                            ? LightningCsvNaming.chunkFileName(targetDatabase, targetTable, i + 1)
                            : LightningCsvNaming.singleFileName(targetDatabase, targetTable)
            );
            Files.move(tempFiles.get(i), finalFile, StandardCopyOption.REPLACE_EXISTING);
            finalFiles.add(finalFile);
        }
        return finalFiles;
    }
}
