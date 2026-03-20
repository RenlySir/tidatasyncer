export type SourceDatabaseType = 'MYSQL' | 'ORACLE' | 'SQLSERVER' | 'POSTGRESQL' | 'HANA'
export type SyncMode = 'FULL_ONLY' | 'INCREMENTAL_ONLY' | 'FULL_AND_INCREMENTAL'

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
  source: {
    databaseType: SourceDatabaseType
    host: string
    port: number
    databaseName: string
    schemaName: string
    username: string
    password: string
    jdbcUrl: string
    commandTemplate: string
  }
  target: {
    host: string
    port: number
    databaseName: string
    username: string
    password: string
    jdbcUrl: string
    lightningBinary: string
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
  syncMode: SyncMode
  status: string
  phase: string
  progressPercent: number
  lastMessage: string | null
  lastError: string | null
  lastLagMillis: number | null
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
  recentJobs: SyncJob[]
}
