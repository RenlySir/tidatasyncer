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
@Table(name = "tool_config")
public class ToolConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DatabaseEndpointType databaseType;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String exportToolBinary;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String lightningBinary;

    @Column(length = 1000)
    private String notes;

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

    public DatabaseEndpointType getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(DatabaseEndpointType databaseType) {
        this.databaseType = databaseType;
    }

    public String getExportToolBinary() {
        return exportToolBinary;
    }

    public void setExportToolBinary(String exportToolBinary) {
        this.exportToolBinary = exportToolBinary;
    }

    public String getLightningBinary() {
        return lightningBinary;
    }

    public void setLightningBinary(String lightningBinary) {
        this.lightningBinary = lightningBinary;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
