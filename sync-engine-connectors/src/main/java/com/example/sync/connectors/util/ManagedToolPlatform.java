package com.example.sync.connectors.util;

public record ManagedToolPlatform(String os, String arch) {

    public static ManagedToolPlatform current() {
        String rawOs = System.getProperty("os.name", "").toLowerCase();
        String rawArch = System.getProperty("os.arch", "").toLowerCase();

        String normalizedOs;
        if (rawOs.contains("linux")) {
            normalizedOs = "linux";
        } else if (rawOs.contains("mac") || rawOs.contains("darwin")) {
            normalizedOs = "darwin";
        } else if (rawOs.contains("win")) {
            normalizedOs = "windows";
        } else {
            normalizedOs = rawOs.replaceAll("\\s+", "-");
        }

        String normalizedArch = switch (rawArch) {
            case "aarch64", "arm64" -> "arm64";
            case "x86_64", "amd64" -> "amd64";
            default -> rawArch;
        };

        return new ManagedToolPlatform(normalizedOs, normalizedArch);
    }

    public String key() {
        return os + "-" + arch;
    }

    public String toolFamily() {
        return switch (arch) {
            case "arm64" -> "arm";
            case "amd64" -> "x86";
            default -> arch;
        };
    }
}
