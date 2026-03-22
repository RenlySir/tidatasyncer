package com.example.sync.admin.domain;

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
@Table(name = "schema_sync_task")
public class SchemaSyncTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private Long sourceProfileId;

    @Column(nullable = false)
    private Long targetProfileId;

    @Column(nullable = false)
    private String tableSelectionMode;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String selectedTablesJson;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String overrideMappingsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SchemaSyncTaskStatus status = SchemaSyncTaskStatus.DRAFT;

    @Column(length = 2000)
    private String lastMessage;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String generatedDdl;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String unsupportedItemsJson;

    private String generatedDdlPath;

    private String unsupportedItemsPath;

    private Instant executedAt;

    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
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

    public Long getSourceProfileId() {
        return sourceProfileId;
    }

    public void setSourceProfileId(Long sourceProfileId) {
        this.sourceProfileId = sourceProfileId;
    }

    public Long getTargetProfileId() {
        return targetProfileId;
    }

    public void setTargetProfileId(Long targetProfileId) {
        this.targetProfileId = targetProfileId;
    }

    public String getTableSelectionMode() {
        return tableSelectionMode;
    }

    public void setTableSelectionMode(String tableSelectionMode) {
        this.tableSelectionMode = tableSelectionMode;
    }

    public String getSelectedTablesJson() {
        return selectedTablesJson;
    }

    public void setSelectedTablesJson(String selectedTablesJson) {
        this.selectedTablesJson = selectedTablesJson;
    }

    public String getOverrideMappingsJson() {
        return overrideMappingsJson;
    }

    public void setOverrideMappingsJson(String overrideMappingsJson) {
        this.overrideMappingsJson = overrideMappingsJson;
    }

    public SchemaSyncTaskStatus getStatus() {
        return status;
    }

    public void setStatus(SchemaSyncTaskStatus status) {
        this.status = status;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getGeneratedDdl() {
        return generatedDdl;
    }

    public void setGeneratedDdl(String generatedDdl) {
        this.generatedDdl = generatedDdl;
    }

    public String getUnsupportedItemsJson() {
        return unsupportedItemsJson;
    }

    public void setUnsupportedItemsJson(String unsupportedItemsJson) {
        this.unsupportedItemsJson = unsupportedItemsJson;
    }

    public String getGeneratedDdlPath() {
        return generatedDdlPath;
    }

    public void setGeneratedDdlPath(String generatedDdlPath) {
        this.generatedDdlPath = generatedDdlPath;
    }

    public String getUnsupportedItemsPath() {
        return unsupportedItemsPath;
    }

    public void setUnsupportedItemsPath(String unsupportedItemsPath) {
        this.unsupportedItemsPath = unsupportedItemsPath;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
