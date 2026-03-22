package com.example.sync.admin.service;

import com.example.sync.core.config.SyncJobDefinition;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class SyncJobResourceCoordinator {

    private final Map<String, Long> activeResources = new ConcurrentHashMap<>();

    public ResourceLease claim(Long jobId, SyncJobDefinition definition) {
        List<String> resourceKeys = buildResourceKeys(definition);
        synchronized (activeResources) {
            for (String key : resourceKeys) {
                Long owner = activeResources.get(key);
                if (owner != null && !owner.equals(jobId)) {
                    throw new IllegalStateException("Resource is already used by running job " + owner + ": " + key);
                }
            }
            for (String key : resourceKeys) {
                activeResources.put(key, jobId);
            }
        }
        return new ResourceLease(jobId, resourceKeys);
    }

    public final class ResourceLease implements AutoCloseable {

        private final Long jobId;
        private final List<String> keys;
        private volatile boolean released;

        private ResourceLease(Long jobId, List<String> keys) {
            this.jobId = jobId;
            this.keys = keys;
        }

        public List<String> keys() {
            return keys;
        }

        @Override
        public void close() {
            if (released) {
                return;
            }
            synchronized (activeResources) {
                for (String key : keys) {
                    Long owner = activeResources.get(key);
                    if (owner != null && owner.equals(jobId)) {
                        activeResources.remove(key);
                    }
                }
            }
            released = true;
        }
    }

    private List<String> buildResourceKeys(SyncJobDefinition definition) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add("source:" + normalize(definition.source().databaseType().name())
                + "|" + normalize(definition.source().host())
                + "|" + definition.source().port()
                + "|" + normalize(definition.source().databaseName())
                + "|" + normalize(definition.source().schemaName())
                + "|" + normalize(definition.fullLoad().exportBaseDir()));
        keys.add("target:" + normalize(definition.target().host())
                + "|" + definition.target().port()
                + "|" + normalize(definition.target().databaseName()));
        if (definition.incremental() != null) {
            keys.add("offset:" + normalize(definition.incremental().offsetStoragePath()));
        }
        return new ArrayList<>(keys);
    }

    private String normalize(String value) {
        return value == null ? "-" : value.trim().toLowerCase(Locale.ROOT);
    }
}
