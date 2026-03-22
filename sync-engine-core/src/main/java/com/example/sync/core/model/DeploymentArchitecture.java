package com.example.sync.core.model;

public enum DeploymentArchitecture {
    AMD64,
    ARM64;

    public String toolFamily() {
        return switch (this) {
            case AMD64 -> "x86";
            case ARM64 -> "arm";
        };
    }

    public String linuxArch() {
        return switch (this) {
            case AMD64 -> "amd64";
            case ARM64 -> "arm64";
        };
    }
}
