package com.example.sync.connectors.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sync.core.model.DeploymentArchitecture;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProjectManagedToolResolverTest {

    @Test
    void shouldBuildOfficialTidbToolkitDownloadUrlForLinux() {
        String url = ProjectManagedToolResolver.tidbToolkitDownloadUrl(
                DeploymentArchitecture.ARM64,
                "v8.5.5"
        );

        assertEquals("https://download.pingcap.com/tidb-community-toolkit-v8.5.5-linux-arm64.tar.gz", url);
    }

    @Test
    void shouldBuildOfficialTidbToolkitDownloadUrlForAmd64() {
        String url = ProjectManagedToolResolver.tidbToolkitDownloadUrl(
                DeploymentArchitecture.AMD64,
                "v8.5.5"
        );

        assertEquals("https://download.pingcap.com/tidb-community-toolkit-v8.5.5-linux-amd64.tar.gz", url);
    }

    @Test
    void shouldPlaceManagedToolsUnderArchitectureFamilyDirectory() {
        Path x86Dir = ProjectManagedToolResolver.toolInstallDir("tidb-lightning", DeploymentArchitecture.AMD64);
        Path armDir = ProjectManagedToolResolver.toolInstallDir("sqluldr2", DeploymentArchitecture.ARM64);
        Path dumplingDir = ProjectManagedToolResolver.toolInstallDir("dumpling", DeploymentArchitecture.AMD64);

        assertTrue(x86Dir.endsWith(Path.of("vendor", "tools", "x86", "tidb-lightning")));
        assertTrue(armDir.endsWith(Path.of("vendor", "tools", "arm", "sqluldr2")));
        assertTrue(dumplingDir.endsWith(Path.of("vendor", "tools", "x86", "dumpling")));
    }
}
