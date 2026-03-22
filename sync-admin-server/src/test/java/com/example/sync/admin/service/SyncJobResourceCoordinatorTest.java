package com.example.sync.admin.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.sync.admin.support.SyncJobFixtures;
import com.example.sync.core.config.SyncJobDefinition;
import org.junit.jupiter.api.Test;

class SyncJobResourceCoordinatorTest {

    private final SyncJobResourceCoordinator coordinator = new SyncJobResourceCoordinator();

    @Test
    void shouldRejectClaimForSameSourceAndTarget() {
        SyncJobDefinition definition = SyncJobFixtures.jobDefinition();
        try (SyncJobResourceCoordinator.ResourceLease ignored = coordinator.claim(1L, definition)) {
            assertThatThrownBy(() -> coordinator.claim(2L, definition))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Resource is already used");
        }
    }

    @Test
    void shouldReleaseResourcesAfterLeaseClosed() {
        SyncJobDefinition definition = SyncJobFixtures.jobDefinition();
        SyncJobResourceCoordinator.ResourceLease lease = coordinator.claim(1L, definition);
        lease.close();

        assertThatCode(() -> coordinator.claim(2L, definition)).doesNotThrowAnyException();
    }
}
