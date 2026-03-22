export type SourceDatabaseType = 'CSV' | 'MYSQL' | 'MARIADB' | 'ORACLE' | 'SQLSERVER' | 'POSTGRESQL' | 'DB2' | 'HANA' | 'MONGODB'
export type SyncMode = 'FULL_ONLY' | 'INCREMENTAL_ONLY' | 'FULL_AND_INCREMENTAL'
export type DeploymentArchitecture = 'AMD64' | 'ARM64'
export type DatabaseEndpointType = SourceDatabaseType | 'TIDB'
export type ConnectionProfileRole = 'SOURCE' | 'TARGET'
export type SchemaSyncTaskStatus = 'DRAFT' | 'REVIEW_REQUIRED' | 'COMPLETED' | 'FAILED'
export type CompatibilityReportStatus = 'DRAFT' | 'COMPLETED' | 'FAILED'

export interface CsvImportTarget {
  host: string
  port: number
  databaseName: string
  username: string
  password: string
  jdbcUrl: string
  jdbcParameters: string
  lightningBinary: string
  statusPort: number
}

export interface TableMapping {
  sourceCatalog: string
  sourceSchema: string
  sourceTable: string
  targetDatabase: string
  targetTable: string
  primaryKeys: string[]
  incrementalColumn: string
  includedColumns: string[]
  columnMappings: Record<string, string>
}

export interface SyncJobDefinition {
  jobId?: number
  jobName?: string
  syncMode: SyncMode
  deploymentArchitecture: DeploymentArchitecture
  source: {
    databaseType: SourceDatabaseType
    host: string
    port: number
    databaseName: string
    schemaName: string
    username: string
    password: string
    jdbcUrl: string
    jdbcParameters: string
    commandTemplate: string
  }
  target: {
    host: string
    port: number
    databaseName: string
    username: string
    password: string
    jdbcUrl: string
    jdbcParameters: string
    lightningBinary: string
    statusPort: number
  }
  tableMappings: TableMapping[]
  fullLoad: {
    exportToolBinary: string
    exportBaseDir: string
    fetchSize: number
    parallelism: number
    additionalProperties: Record<string, string>
  }
  incremental: {
    serverName: string
    slotName: string
    publicationName: string
    offsetStoragePath: string
    pollingIntervalSeconds: number
    batchSize: number
    additionalProperties: Record<string, string>
  }
}

export interface SyncJob {
  id: number
  name: string
  sourceDatabaseType: SourceDatabaseType
  syncMode: SyncMode
  status: string
  phase: string
  progressPercent: number
  lastMessage: string | null
  lastError: string | null
  lastLagMillis: number | null
  exportedTableCount: number | null
  totalTableCount: number | null
  exportedBytes: number | null
  importedTableCount: number | null
  importedBytes: number | null
  latestLogPosition: string | null
  latestCatalog: string | null
  latestSchema: string | null
  latestTable: string | null
  latestPrimaryKey: string | null
  createdAt: string
  updatedAt: string
  startedAt: string | null
  stoppedAt: string | null
}

export interface SyncJobLog {
  id: number
  jobId: number
  level: string
  message: string
  createdAt: string
}

export interface DashboardOverview {
  totalJobs: number
  runningJobs: number
  failedJobs: number
  completedJobs: number
  sourceProfileCount: number
  targetProfileCount: number
  csvSourceCount: number
  toolConfigCount: number
  compatibilityReportCount: number
  completedCompatibilityReportCount: number
  schemaTaskCount: number
  completedSchemaTaskCount: number
  batchEnabledJobCount: number
  realtimeEnabledJobCount: number
  fullOnlyJobCount: number
  incrementalOnlyJobCount: number
  fullAndIncrementalJobCount: number
  pipelineReadinessScore: number
  recentJobs: SyncJob[]
}

export interface CsvDirectoryPrepareResponse {
  directoryPath: string
  totalCsvFiles: number
  convertedCharsetFiles: number
  splitSourceFiles: number
  generatedChunkFiles: number
  unchangedFiles: number
  csvFiles: string[]
  message: string
}

export interface CsvDirectoryImportResponse {
  directoryPath: string
  importedCsvFiles: number
  message: string
}

export interface ManagedToolPaths {
  tidbLightningBinary: string
  dumplingBinary: string
  sqluldr2Binary: string
  bcpBinary: string
  sqlcmdBinary: string
}

export interface SystemSettings {
  deploymentArchitecture: DeploymentArchitecture
  updatedAt: string | null
}

export interface ConnectionProfile {
  id: number
  name: string
  role: ConnectionProfileRole
  databaseType: DatabaseEndpointType
  host: string | null
  port: number | null
  databaseName: string | null
  schemaName: string | null
  username: string | null
  password: string | null
  jdbcUrl: string | null
  jdbcParameters: string | null
  csvDirectory: string | null
  permissionNote: string | null
  tidbStatusPort: number | null
  createdAt: string
  updatedAt: string
}

export interface ConnectionProfileUpsert {
  name: string
  role: ConnectionProfileRole
  databaseType: DatabaseEndpointType
  host: string | null
  port: number | null
  databaseName: string | null
  schemaName: string | null
  username: string | null
  password: string | null
  jdbcUrl: string | null
  jdbcParameters: string | null
  csvDirectory: string | null
  permissionNote: string | null
  tidbStatusPort: number | null
}

export interface ConnectionProfilePermissionCheckItem {
  key: string
  label: string
  passed: boolean
  detail: string
}

export interface ConnectionProfilePermissionCheck {
  profileId: number
  profileName: string
  role: ConnectionProfileRole
  databaseType: DatabaseEndpointType
  passed: boolean
  summary: string
  missingPermissions: string[]
  suggestedGrantStatements: string[]
  checks: ConnectionProfilePermissionCheckItem[]
  checkedAt: string
}

export interface ToolConfig {
  id: number
  name: string
  databaseType: DatabaseEndpointType
  exportToolBinary: string | null
  lightningBinary: string | null
  notes: string | null
  createdAt: string
  updatedAt: string
}

export interface ToolConfigUpsert {
  name: string
  databaseType: DatabaseEndpointType
  exportToolBinary: string | null
  lightningBinary: string | null
  notes: string | null
}

export interface UnsupportedTypeItem {
  tableName: string
  columnName: string
  sourceType: string
  suggestedTargetType: string
  reason: string
}

export interface SchemaSyncTask {
  id: number
  name: string
  sourceProfileId: number
  targetProfileId: number
  tableSelectionMode: 'DATABASE_ALL' | 'SELECTED_TABLES'
  selectedTables: string[]
  overrideMappings: Record<string, string>
  status: SchemaSyncTaskStatus
  lastMessage: string | null
  generatedDdl: string | null
  generatedDdlPath: string | null
  unsupportedItemsPath: string | null
  unsupportedItems: UnsupportedTypeItem[]
  executedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface SchemaSyncTaskUpsert {
  name: string
  sourceProfileId: number
  targetProfileId: number
  tableSelectionMode: 'DATABASE_ALL' | 'SELECTED_TABLES'
  selectedTables: string[]
  overrideMappings: Record<string, string>
}

export interface CompatibilitySummary {
  totalFindings: number
  incompatibleCount: number
  partialCount: number
  compatibleCount: number
  errorCount: number
  warningCount: number
  infoCount: number
  tableCount: number
  viewCount: number
  triggerCount: number
  procedureCount: number
  functionCount: number
  sequenceCount: number
}

export interface CompatibilityFinding {
  category: string
  objectType: string
  objectName: string
  compatibility: string
  severity: string
  message: string
  suggestion: string
}

export interface CompatibilityReport {
  id: number
  name: string
  sourceProfileId: number
  targetProfileId: number
  status: CompatibilityReportStatus
  lastMessage: string | null
  summary: CompatibilitySummary
  findings: CompatibilityFinding[]
  reportMarkdown: string | null
  reportHtml: string | null
  reportPath: string | null
  reportHtmlPath: string | null
  executedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface CompatibilityReportUpsert {
  name: string
  sourceProfileId: number
  targetProfileId: number
}

export interface RuntimeLogFile {
  key: string
  displayName: string
  absolutePath: string
  exists: boolean
  sizeBytes: number
  lastModifiedAt: string | null
}

export interface RuntimeLogTail {
  key: string
  displayName: string
  absolutePath: string
  exists: boolean
  sizeBytes: number
  lastModifiedAt: string | null
  lineCount: number
  lines: string[]
}
