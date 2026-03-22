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
@Table(name = "compatibility_report")
public class CompatibilityReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private Long sourceProfileId;

    @Column(nullable = false)
    private Long targetProfileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompatibilityReportStatus status = CompatibilityReportStatus.DRAFT;

    @Column(length = 2000)
    private String lastMessage;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String summaryJson;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String findingsJson;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String reportMarkdown;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String reportHtml;

    private String reportPath;

    private String reportHtmlPath;

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

    public CompatibilityReportStatus getStatus() {
        return status;
    }

    public void setStatus(CompatibilityReportStatus status) {
        this.status = status;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }

    public String getFindingsJson() {
        return findingsJson;
    }

    public void setFindingsJson(String findingsJson) {
        this.findingsJson = findingsJson;
    }

    public String getReportMarkdown() {
        return reportMarkdown;
    }

    public void setReportMarkdown(String reportMarkdown) {
        this.reportMarkdown = reportMarkdown;
    }

    public String getReportPath() {
        return reportPath;
    }

    public void setReportPath(String reportPath) {
        this.reportPath = reportPath;
    }

    public String getReportHtml() {
        return reportHtml;
    }

    public void setReportHtml(String reportHtml) {
        this.reportHtml = reportHtml;
    }

    public String getReportHtmlPath() {
        return reportHtmlPath;
    }

    public void setReportHtmlPath(String reportHtmlPath) {
        this.reportHtmlPath = reportHtmlPath;
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
