package com.example.sync.connectors.util;

import com.example.sync.core.model.DeploymentArchitecture;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public final class ProjectManagedToolResolver {

    public static final String TIDB_LIGHTNING_VERSION = "v8.5.5";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private ProjectManagedToolResolver() {
    }

    public static String resolveTidbLightningBinary(String explicitBinary, DeploymentArchitecture deploymentArchitecture)
            throws IOException, InterruptedException {
        if (explicitBinary != null && !explicitBinary.isBlank()) {
            return explicitBinary;
        }

        DeploymentArchitecture selectedArchitecture = selectedArchitecture(deploymentArchitecture);
        Path binary = managedToolBinary("tidb-lightning", selectedArchitecture);
        if (Files.isRegularFile(binary)) {
            makeExecutable(binary);
            return binary.toAbsolutePath().toString();
        }

        ensureTidbLightningInstalled(selectedArchitecture);
        if (!Files.isRegularFile(binary)) {
            throw new IllegalStateException("tidb-lightning was not found after installation: " + binary);
        }
        makeExecutable(binary);
        return binary.toAbsolutePath().toString();
    }

    public static String resolveDumplingBinary(String explicitBinary, DeploymentArchitecture deploymentArchitecture)
            throws IOException, InterruptedException {
        if (explicitBinary != null && !explicitBinary.isBlank()) {
            return explicitBinary;
        }

        DeploymentArchitecture selectedArchitecture = selectedArchitecture(deploymentArchitecture);
        Path binary = managedToolBinary("dumpling", selectedArchitecture);
        if (Files.isRegularFile(binary)) {
            makeExecutable(binary);
            return binary.toAbsolutePath().toString();
        }

        ensureTidbToolkitBinaryInstalled("dumpling", selectedArchitecture);
        if (!Files.isRegularFile(binary)) {
            throw new IllegalStateException("dumpling was not found after installation: " + binary);
        }
        makeExecutable(binary);
        return binary.toAbsolutePath().toString();
    }

    public static String resolveBcpBinary(String explicitBinary, DeploymentArchitecture deploymentArchitecture)
            throws IOException {
        if (explicitBinary != null && !explicitBinary.isBlank()) {
            return explicitBinary;
        }

        DeploymentArchitecture selectedArchitecture = selectedArchitecture(deploymentArchitecture);
        Path binary = managedToolBinary("bcp", selectedArchitecture);
        if (!Files.isRegularFile(binary)) {
            throw new IllegalStateException("bcp is not installed. Place it at " + binary);
        }
        makeExecutable(binary);
        return binary.toAbsolutePath().toString();
    }

    public static String resolveSqlcmdBinary(String explicitBinary, DeploymentArchitecture deploymentArchitecture)
            throws IOException {
        if (explicitBinary != null && !explicitBinary.isBlank()) {
            return explicitBinary;
        }

        DeploymentArchitecture selectedArchitecture = selectedArchitecture(deploymentArchitecture);
        Path binary = managedToolBinary("sqlcmd", selectedArchitecture);
        if (!Files.isRegularFile(binary)) {
            throw new IllegalStateException("sqlcmd is not installed. Place it at " + binary);
        }
        makeExecutable(binary);
        return binary.toAbsolutePath().toString();
    }

    public static String resolveSqluldr2Binary(String explicitBinary, DeploymentArchitecture deploymentArchitecture)
            throws IOException, InterruptedException {
        if (explicitBinary != null && !explicitBinary.isBlank()) {
            return explicitBinary;
        }

        DeploymentArchitecture selectedArchitecture = selectedArchitecture(deploymentArchitecture);
        Path binary = managedToolBinary("sqluldr2", selectedArchitecture);
        if (Files.isRegularFile(binary)) {
            makeExecutable(binary);
            return binary.toAbsolutePath().toString();
        }

        ensureSqluldr2Installed(binary);
        if (!Files.isRegularFile(binary)) {
            throw new IllegalStateException("sqluldr2 is not installed. Place it at " + binary
                    + " or set SQLULDR2_DOWNLOAD_URL / system property sqluldr2.downloadUrl.");
        }
        makeExecutable(binary);
        return binary.toAbsolutePath().toString();
    }

    public static Path managedToolBinary(String toolName) {
        return managedToolBinary(toolName, null);
    }

    public static Path managedToolBinary(String toolName, DeploymentArchitecture deploymentArchitecture) {
        return toolInstallDir(toolName, deploymentArchitecture).resolve(toolName);
    }

    static Path toolInstallDir(String toolName) {
        return toolInstallDir(toolName, null);
    }

    static Path toolInstallDir(String toolName, DeploymentArchitecture deploymentArchitecture) {
        DeploymentArchitecture selectedArchitecture = selectedArchitecture(deploymentArchitecture);
        return projectRoot().resolve("vendor").resolve("tools").resolve(selectedArchitecture.toolFamily()).resolve(toolName);
    }

    static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    static String tidbToolkitDownloadUrl(DeploymentArchitecture deploymentArchitecture, String version) {
        return "https://download.pingcap.com/tidb-community-toolkit-%s-linux-%s.tar.gz"
                .formatted(version, selectedArchitecture(deploymentArchitecture).linuxArch());
    }

    private static void ensureTidbLightningInstalled(DeploymentArchitecture deploymentArchitecture)
            throws IOException, InterruptedException {
        ensureTidbToolkitBinaryInstalled("tidb-lightning", deploymentArchitecture);
    }

    private static void ensureTidbToolkitBinaryInstalled(String toolName, DeploymentArchitecture deploymentArchitecture)
            throws IOException, InterruptedException {
        DeploymentArchitecture selectedArchitecture = selectedArchitecture(deploymentArchitecture);
        Path installDir = toolInstallDir(toolName, selectedArchitecture);
        Files.createDirectories(installDir);
        Path archive = Files.createTempFile(
                "tidb-community-toolkit-" + TIDB_LIGHTNING_VERSION + "-" + selectedArchitecture.linuxArch() + "-",
                ".tar.gz"
        );
        try {
            downloadTo(tidbToolkitDownloadUrl(selectedArchitecture, TIDB_LIGHTNING_VERSION), archive);
            Process process = new ProcessBuilder(
                    "/bin/zsh",
                    "-lc",
                    "tar -xzf '%s' -C '%s' --strip-components=1 'tidb-community-toolkit-%s-linux-%s/%s'"
                            .formatted(
                                    archive.toAbsolutePath(),
                                    installDir.toAbsolutePath(),
                                    TIDB_LIGHTNING_VERSION,
                                    selectedArchitecture.linuxArch(),
                                    toolName
                            )
            ).redirectErrorStream(true).start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                throw new IllegalStateException("Failed to extract " + toolName + ": " + output);
            }
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    private static void ensureSqluldr2Installed(Path binary) throws IOException, InterruptedException {
        String downloadUrl = firstNonBlank(
                System.getenv("SQLULDR2_DOWNLOAD_URL"),
                System.getProperty("sqluldr2.downloadUrl")
        );
        String archiveEntry = firstNonBlank(
                System.getenv("SQLULDR2_ARCHIVE_ENTRY"),
                System.getProperty("sqluldr2.archiveEntry"),
                "sqluldr2"
        );

        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new IllegalStateException(
                    "SQLULDR2 has no stable public official download URL on the author site. "
                            + "Provide SQLULDR2_DOWNLOAD_URL or place the binary at " + binary
            );
        }

        Path installDir = binary.getParent();
        Files.createDirectories(installDir);
        String lowerUrl = downloadUrl.toLowerCase();
        String suffix = lowerUrl.endsWith(".tar.gz") ? ".tar.gz"
                : lowerUrl.endsWith(".tgz") ? ".tgz"
                : lowerUrl.endsWith(".zip") ? ".zip"
                : ".bin";
        Path archive = Files.createTempFile("sqluldr2-", suffix);
        downloadTo(downloadUrl, archive);

        try {
            Process process;
            if (lowerUrl.endsWith(".zip")) {
                process = new ProcessBuilder(
                        "/bin/zsh",
                        "-lc",
                        "unzip -o '%s' '%s' -d '%s'"
                                .formatted(archive.toAbsolutePath(), archiveEntry, installDir.toAbsolutePath())
                ).redirectErrorStream(true).start();
            } else if (lowerUrl.endsWith(".tar.gz") || lowerUrl.endsWith(".tgz")) {
                process = new ProcessBuilder(
                        "/bin/zsh",
                        "-lc",
                        "tar -xzf '%s' -C '%s' '%s'"
                                .formatted(archive.toAbsolutePath(), installDir.toAbsolutePath(), archiveEntry)
                ).redirectErrorStream(true).start();
            } else {
                Files.copy(archive, binary, StandardCopyOption.REPLACE_EXISTING);
                makeExecutable(binary);
                return;
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                throw new IllegalStateException("Failed to install sqluldr2 from archive: " + output);
            }
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    private static void downloadTo(String url, Path target) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Failed to download " + url + ", status=" + response.statusCode());
        }
        try (InputStream body = response.body()) {
            Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static void makeExecutable(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
        }
    }

    private static DeploymentArchitecture selectedArchitecture(DeploymentArchitecture deploymentArchitecture) {
        if (deploymentArchitecture != null) {
            return deploymentArchitecture;
        }
        return switch (ManagedToolPlatform.current().arch()) {
            case "arm64" -> DeploymentArchitecture.ARM64;
            case "amd64" -> DeploymentArchitecture.AMD64;
            default -> DeploymentArchitecture.AMD64;
        };
    }
}
