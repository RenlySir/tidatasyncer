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
@Table(name = "connection_profile")
public class ConnectionProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionProfileRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DatabaseEndpointType databaseType;

    private String host;

    private Integer port;

    private String databaseName;

    private String schemaName;

    private String username;

    private String password;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String jdbcUrl;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String jdbcParameters;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String csvDirectory;

    @Column(length = 1000)
    private String permissionNote;

    private Integer tidbStatusPort;

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

    public ConnectionProfileRole getRole() {
        return role;
    }

    public void setRole(ConnectionProfileRole role) {
        this.role = role;
    }

    public DatabaseEndpointType getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(DatabaseEndpointType databaseType) {
        this.databaseType = databaseType;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getJdbcParameters() {
        return jdbcParameters;
    }

    public void setJdbcParameters(String jdbcParameters) {
        this.jdbcParameters = jdbcParameters;
    }

    public String getCsvDirectory() {
        return csvDirectory;
    }

    public void setCsvDirectory(String csvDirectory) {
        this.csvDirectory = csvDirectory;
    }

    public String getPermissionNote() {
        return permissionNote;
    }

    public void setPermissionNote(String permissionNote) {
        this.permissionNote = permissionNote;
    }

    public Integer getTidbStatusPort() {
        return tidbStatusPort;
    }

    public void setTidbStatusPort(Integer tidbStatusPort) {
        this.tidbStatusPort = tidbStatusPort;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
