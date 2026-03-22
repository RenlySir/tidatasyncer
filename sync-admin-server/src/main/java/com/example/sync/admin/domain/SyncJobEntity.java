package com.example.sync.admin.domain;

import com.example.sync.core.model.JobPhase;
import com.example.sync.core.model.SyncMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "sync_job")
public class SyncJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncMode syncMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncJobStatus status = SyncJobStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobPhase phase = JobPhase.CREATED;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String definitionJson;

    private Integer progressPercent = 0;

    @Column(length = 2000)
    private String lastMessage;

    @Column(length = 4000)
    private String lastError;

    private Long lastLagMillis;

    private Integer exportedTableCount;

    private Integer totalTableCount;

    private Long exportedBytes;

    private Integer importedTableCount;

    private Long importedBytes;

    @Column(length = 1000)
    private String latestLogPosition;

    private String latestCatalog;

    private String latestSchema;

    private String latestTable;

    private String latestPrimaryKey;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant startedAt;

    private Instant stoppedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SyncMode getSyncMode() {
        return syncMode;
    }

    public void setSyncMode(SyncMode syncMode) {
        this.syncMode = syncMode;
    }

    public SyncJobStatus getStatus() {
        return status;
    }

    public void setStatus(SyncJobStatus status) {
        this.status = status;
    }

    public JobPhase getPhase() {
        return phase;
    }

    public void setPhase(JobPhase phase) {
        this.phase = phase;
    }

    public String getDefinitionJson() {
        return definitionJson;
    }

    public void setDefinitionJson(String definitionJson) {
        this.definitionJson = definitionJson;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Long getLastLagMillis() {
        return lastLagMillis;
    }

    public void setLastLagMillis(Long lastLagMillis) {
        this.lastLagMillis = lastLagMillis;
    }

    public Integer getExportedTableCount() {
        return exportedTableCount;
    }

    public void setExportedTableCount(Integer exportedTableCount) {
        this.exportedTableCount = exportedTableCount;
    }

    public Integer getTotalTableCount() {
        return totalTableCount;
    }

    public void setTotalTableCount(Integer totalTableCount) {
        this.totalTableCount = totalTableCount;
    }

    public Long getExportedBytes() {
        return exportedBytes;
    }

    public void setExportedBytes(Long exportedBytes) {
        this.exportedBytes = exportedBytes;
    }

    public Integer getImportedTableCount() {
        return importedTableCount;
    }

    public void setImportedTableCount(Integer importedTableCount) {
        this.importedTableCount = importedTableCount;
    }

    public Long getImportedBytes() {
        return importedBytes;
    }

    public void setImportedBytes(Long importedBytes) {
        this.importedBytes = importedBytes;
    }

    public String getLatestLogPosition() {
        return latestLogPosition;
    }

    public void setLatestLogPosition(String latestLogPosition) {
        this.latestLogPosition = latestLogPosition;
    }

    public String getLatestCatalog() {
        return latestCatalog;
    }

    public void setLatestCatalog(String latestCatalog) {
        this.latestCatalog = latestCatalog;
    }

    public String getLatestSchema() {
        return latestSchema;
    }

    public void setLatestSchema(String latestSchema) {
        this.latestSchema = latestSchema;
    }

    public String getLatestTable() {
        return latestTable;
    }

    public void setLatestTable(String latestTable) {
        this.latestTable = latestTable;
    }

    public String getLatestPrimaryKey() {
        return latestPrimaryKey;
    }

    public void setLatestPrimaryKey(String latestPrimaryKey) {
        this.latestPrimaryKey = latestPrimaryKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(Instant stoppedAt) {
        this.stoppedAt = stoppedAt;
    }
}
