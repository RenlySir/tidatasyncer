<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { sourceCatalog, sourceCatalogMap } from '../sourceCatalog'
import type { DeploymentArchitecture, SourceDatabaseType, SyncJob, SyncJobDefinition, SyncMode, TableMapping } from '../types'

const props = defineProps<{
  job: SyncJob | null
  definition: SyncJobDefinition | null
  defaultLightningBinary: string
  deploymentArchitecture: DeploymentArchitecture
}>()

const emit = defineEmits<{
  save: [name: string, definition: SyncJobDefinition]
}>()

const modeCards: Array<{ mode: SyncMode; title: string; description: string }> = [
  { mode: 'FULL_ONLY', title: '全量任务', description: '调用源端工具导出，再用 TiDB Lightning 完成一次性导入' },
  { mode: 'INCREMENTAL_ONLY', title: '增量任务', description: '只读取数据库日志或变更流，需要手工填写增量起点' },
  { mode: 'FULL_AND_INCREMENTAL', title: '全量 + 增量任务', description: '先导出与导入，再从自动记录的日志位点继续增量同步' }
]
const tableSelectionCards = [
  { key: 'DATABASE_ALL', title: '整库同步', description: '默认同步当前所选数据库 / schema 下的全部表' },
  { key: 'SELECTED_TABLES', title: '指定表同步', description: '只同步你手工维护的表映射，可配置单表或多表' }
] as const
const sqlServerExportToolCards = [
  { key: 'bcp', title: 'bcp（默认）', description: '微软批量复制工具，适合大表高速导出和长期运行任务。' },
  { key: 'sqlcmd', title: 'sqlcmd（可选）', description: '查询结果导出工具，适合受限环境兜底，但不是首选大批量导出方案。' }
] as const
const postgreSqlExportMethodCards = [
  { key: 'psql_copy', title: 'psql \\copy（默认）', description: '客户端导出到部署机本地文件，最适合本平台的 Lightning 装载链路。' },
  { key: 'server_copy', title: 'COPY（可选）', description: '服务器端导出，需要数据库服务器文件写权限，且文件路径从数据库服务器视角解释。' },
  { key: 'psql_csv', title: 'psql --csv（可选）', description: '通过标准查询结果导出 CSV，适合作为补充方案，但不是大批量场景首选。' }
] as const
const requiredConnectionFields = ['host / ip', 'port', 'username', 'password', 'databaseName', 'schemaName']

const sourceDefaultPorts: Record<SourceDatabaseType, number> = {
  CSV: 0,
  MYSQL: 3306,
  MARIADB: 3306,
  ORACLE: 1521,
  SQLSERVER: 1433,
  POSTGRESQL: 5432,
  DB2: 50000,
  HANA: 30015,
  MONGODB: 27017
}

type PermissionGuide = {
  summary: string
  privileges: string[]
  example: string
}

type ToolGuide = {
  full: string
  fullNote: string
  incremental: string
  incrementalNote: string
}

const sourcePermissionGuides: Record<SourceDatabaseType, PermissionGuide> = {
  CSV: {
    summary: 'CSV 数据源不需要数据库账号，但需要部署机上的文件系统读取权限，并确保目录内文件名满足 db.table.00000001.csv 命名规则。',
    privileges: ['CSV 目录可读', '目录中文件可改名/可写入', '允许字符集转换和切分临时文件'],
    example: `# 示例
mkdir -p /data/csv-load
chown syncuser:syncuser /data/csv-load
chmod 750 /data/csv-load`
  },
  MYSQL: {
    summary: 'MySQL 源端账号建议使用独立同步用户。全量导出和增量 CDC 共用时，需要同时具备快照读取和 binlog 读取权限。',
    privileges: ['SELECT', 'RELOAD', 'SHOW DATABASES', 'REPLICATION SLAVE', 'REPLICATION CLIENT', 'LOCK TABLES'],
    example: `CREATE USER 'sync_user'@'%' IDENTIFIED BY 'StrongPassword';
GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'sync_user'@'%';
GRANT LOCK TABLES ON *.* TO 'sync_user'@'%';
FLUSH PRIVILEGES;`
  },
  MARIADB: {
    summary: 'MariaDB 源端建议单独创建同步账号。若使用 mariadb-dump tab/outfile 导出方案，全量账号通常还需要 FILE 权限以及 secure_file_priv 目录写权限。',
    privileges: ['SELECT', 'RELOAD', 'SHOW DATABASES', 'REPLICATION SLAVE', 'REPLICATION CLIENT', 'FILE'],
    example: `CREATE USER 'sync_user'@'%' IDENTIFIED BY 'StrongPassword';
GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT, FILE ON *.* TO 'sync_user'@'%';
FLUSH PRIVILEGES;`
  },
  ORACLE: {
    summary: 'Oracle 源端账号需要同时满足 sqluldr2 全量导出和 LogMiner 增量解析。生产环境建议单独创建同步用户与日志挖掘表空间。',
    privileges: ['CREATE SESSION', 'SET CONTAINER', 'FLASHBACK ANY TABLE', 'SELECT ANY TABLE', 'SELECT ANY TRANSACTION', 'LOGMINING', 'EXECUTE ON DBMS_LOGMNR', 'EXECUTE ON DBMS_LOGMNR_D'],
    example: `CREATE USER c##dbzuser IDENTIFIED BY "StrongPassword"
  DEFAULT TABLESPACE logminer_tbs
  QUOTA UNLIMITED ON logminer_tbs
  CONTAINER=ALL;
GRANT CREATE SESSION TO c##dbzuser CONTAINER=ALL;
GRANT SET CONTAINER TO c##dbzuser CONTAINER=ALL;
GRANT FLASHBACK ANY TABLE TO c##dbzuser CONTAINER=ALL;
GRANT SELECT ANY TABLE TO c##dbzuser CONTAINER=ALL;
GRANT SELECT ANY TRANSACTION TO c##dbzuser CONTAINER=ALL;
GRANT LOGMINING TO c##dbzuser CONTAINER=ALL;
GRANT EXECUTE ON DBMS_LOGMNR TO c##dbzuser CONTAINER=ALL;
GRANT EXECUTE ON DBMS_LOGMNR_D TO c##dbzuser CONTAINER=ALL;`
  },
  SQLSERVER: {
    summary: 'SQL Server 需要先由管理员开启数据库和表级 CDC，然后同步用户拥有源表 SELECT 和 CDC gating role 即可。',
    privileges: ['SELECT', 'cdc_reader role', '数据库已启用 CDC', '目标表已启用 CDC'],
    example: `USE MyDB;
GO
EXEC sys.sp_cdc_enable_db;
GO
EXEC sys.sp_cdc_enable_table
  @source_schema = N'dbo',
  @source_name   = N'Orders',
  @role_name     = N'cdc_reader',
  @supports_net_changes = 0;
GO
CREATE LOGIN sync_login WITH PASSWORD = 'StrongPassword';
CREATE USER sync_user FOR LOGIN sync_login;
GRANT SELECT ON dbo.Orders TO sync_user;
EXEC sp_addrolemember N'cdc_reader', N'sync_user';`
  },
  POSTGRESQL: {
    summary: 'PostgreSQL 源端账号需要具备逻辑复制能力，并确保 pg_hba.conf 放通来源地址。若平台自动建 publication，还需要对应建对象权限。',
    privileges: ['LOGIN', 'REPLICATION', 'CONNECT', 'USAGE ON SCHEMA', 'SELECT ON TABLES', 'CREATE PUBLICATION'],
    example: `CREATE ROLE sync_user REPLICATION LOGIN PASSWORD 'StrongPassword';
GRANT CONNECT ON DATABASE appdb TO sync_user;
GRANT USAGE ON SCHEMA public TO sync_user;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO sync_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO sync_user;
CREATE PUBLICATION sync_pub FOR TABLE public.orders;

# pg_hba.conf
host    replication    sync_user    10.0.0.0/24    md5
host    appdb          sync_user    10.0.0.0/24    md5`
  },
  DB2: {
    summary: 'Db2 源端账号需要具备连接、读取源表以及 CDC/日志读取能力。全量导出通常建议在 UTF-8 codepage 环境下执行。',
    privileges: ['CONNECT', 'SELECT ON SOURCE TABLES', 'DATAACCESS 或等价表权限', 'CDC/日志读取权限'],
    example: `db2 connect to SAMPLE user sync_user using StrongPassword
db2 grant connect on database to user sync_user
db2 grant dataaccess on database to user sync_user`
  },
  HANA: {
    summary: 'SAP HANA 当前链路采用全量导出加高水位增量轮询，同步用户通常只需要读数据和读元数据权限。',
    privileges: ['SELECT ON SCHEMA', 'CATALOG READ'],
    example: `CREATE USER SYNC_USER PASSWORD "StrongPassword";
GRANT CATALOG READ TO SYNC_USER;
GRANT SELECT ON SCHEMA APP TO SYNC_USER;`
  },
  MONGODB: {
    summary: 'MongoDB 源端全量依赖 mongoexport，增量依赖 change streams。同步账号至少要能读取源库，并能 watch 对应集合。',
    privileges: ['read', 'watch(change streams)'],
    example: `use source_db
db.createUser({
  user: "sync_user",
  pwd: "StrongPassword",
  roles: [
    { role: "read", db: "source_db" }
  ]
})`
  }
}

const sourceToolGuides: Record<SourceDatabaseType, ToolGuide> = {
  CSV: {
    full: 'CSV 目录预处理 + TiDB Lightning',
    fullNote: '先检查目录下 CSV 文件字符集并统一转换为 UTF-8；若单文件大于 200MiB，则按 128MiB 切分，并保持 db.table.00000001.csv 命名规则。',
    incremental: '不支持',
    incrementalNote: 'CSV 数据源仅支持 FULL_ONLY，全量导入完成后任务结束。'
  },
  MYSQL: {
    full: 'dumpling',
    fullNote: 'MySQL 全量导出使用 dumpling SQL dump，单文件大小默认 128MiB，随后由 TiDB Lightning 导入。',
    incremental: 'Debezium MySQL Connector',
    incrementalNote: '增量仅读取 binlog；纯增量任务需要填写 binlog 文件名和位置，全量+增量任务会自动记录起点。'
  },
  MARIADB: {
    full: 'mariadb-dump / 自定义导出命令',
    fullNote: 'MariaDB 默认走原生命令模板导出，可按现场环境覆盖为更合适的 CSV 导出方式。',
    incremental: 'Debezium MariaDB Connector',
    incrementalNote: '增量读取 MariaDB binlog，建议独立配置 server id 和日志保留策略。'
  },
  ORACLE: {
    full: 'sqluldr2',
    fullNote: 'Oracle 全量导出为 CSV，并按 128MiB 切分后重命名为 Lightning 可识别格式。',
    incremental: 'Debezium Oracle Connector (LogMiner)',
    incrementalNote: '纯增量任务支持手工填写 SCN；全量+增量任务会在导出前自动记录 SCN。'
  },
  SQLSERVER: {
    full: 'bcp（默认） / sqlcmd（可选）',
    fullNote: '默认使用 bcp 做批量 CSV 导出；sqlcmd 仅作为可选查询结果导出工具，适合现场权限或工具安装受限时兜底。Linux 上建议同时安装 Microsoft ODBC 运行库。',
    incremental: 'Debezium SQL Server Connector',
    incrementalNote: '增量依赖 SQL Server CDC，建议先由 DBA 开启数据库与表级 CDC。'
  },
  POSTGRESQL: {
    full: 'psql \\copy（默认） / COPY / psql --csv',
    fullNote: '默认通过 psql 客户端执行 \\copy 生成 UTF-8 CSV 文件；可选服务器端 COPY 或 psql --csv 查询导出。其中 COPY 需要数据库服务器文件写权限。',
    incremental: 'Debezium PostgreSQL Connector',
    incrementalNote: '增量依赖 logical decoding、slot 和 publication。'
  },
  DB2: {
    full: 'db2 EXPORT',
    fullNote: 'Db2 全量导出使用 db2 export 生成 DEL/CSV 兼容文件，再由 TiDB Lightning 导入。',
    incremental: 'Debezium Db2 Connector',
    incrementalNote: '增量读取 Db2 CDC 变更日志，适合传统核心库迁移场景。'
  },
  HANA: {
    full: 'hdbsql',
    fullNote: 'SAP HANA 全量导出使用 hdbsql 客户端导出为 CSV。',
    incremental: '高水位轮询',
    incrementalNote: '增量使用更新时间列轮询，需在表映射中明确 incrementalColumn。'
  },
  MONGODB: {
    full: 'mongoexport',
    fullNote: 'MongoDB 全量导出使用 mongoexport 输出 CSV，必须显式指定 includedColumns。',
    incremental: 'MongoDB change streams',
    incrementalNote: '增量依赖 change streams，页面会显示最新 resume token。'
  }
}

const targetPermissionGuide = {
  summary: 'TiDB 目标端建议单独使用导入/写入账号，负责 Lightning 导入和增量写入，不要直接复用业务超级账号。',
  privileges: ['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'CREATE', 'ALTER', 'INDEX'],
  example: `CREATE USER 'sync_user'@'%' IDENTIFIED BY 'StrongPassword';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX ON target_db.* TO 'sync_user'@'%';`
}

function emptyMapping(): TableMapping {
  return {
    sourceCatalog: '',
    sourceSchema: '',
    sourceTable: '',
    targetDatabase: '',
    targetTable: '',
    primaryKeys: [],
    incrementalColumn: '',
    includedColumns: [],
    columnMappings: {}
  }
}

function emptyDefinition(): SyncJobDefinition {
  return {
    syncMode: 'FULL_AND_INCREMENTAL',
    deploymentArchitecture: props.deploymentArchitecture,
    source: {
      databaseType: 'MYSQL',
      host: '127.0.0.1',
      port: 3306,
      databaseName: '',
      schemaName: '',
      username: '',
      password: '',
      jdbcUrl: '',
      jdbcParameters: '',
      commandTemplate: ''
    },
    target: {
      host: '127.0.0.1',
      port: 4000,
      databaseName: '',
      username: 'root',
      password: '',
      jdbcUrl: '',
      jdbcParameters: 'useUnicode=true&characterEncoding=utf8&rewriteBatchedStatements=true',
      lightningBinary: props.defaultLightningBinary,
      statusPort: 10080
    },
    tableMappings: [],
    fullLoad: {
      exportToolBinary: '',
      exportBaseDir: './work/export',
      fetchSize: 1000,
      parallelism: 1,
      additionalProperties: {}
    },
    incremental: {
      serverName: 'sync_server',
      slotName: 'sync_slot',
      publicationName: 'sync_pub',
      offsetStoragePath: './work/offsets/offset.dat',
      pollingIntervalSeconds: 5,
      batchSize: 500,
      additionalProperties: {
        mysqlSnapshotMode: 'no_data',
        oracleAdapter: 'logminer',
        sqlServerExportTool: 'bcp',
        postgresExportMethod: 'psql_copy',
        postgresPluginName: 'pgoutput',
        postgresPublicationAutoCreateMode: 'all_tables'
      }
    }
  }
}

function normalizeDefinition(definition: SyncJobDefinition | null): SyncJobDefinition {
  if (!definition) {
    return emptyDefinition()
  }
  const defaults = emptyDefinition()
  return {
    ...defaults,
    ...definition,
    deploymentArchitecture: props.deploymentArchitecture,
    source: {
      ...defaults.source,
      ...definition.source
    },
    target: {
      ...defaults.target,
      ...definition.target
    },
    fullLoad: {
      ...defaults.fullLoad,
      ...definition.fullLoad,
      additionalProperties: {
        ...defaults.fullLoad.additionalProperties,
        ...(definition.fullLoad?.additionalProperties ?? {})
      }
    },
    incremental: {
      ...defaults.incremental,
      ...definition.incremental,
      additionalProperties: {
        ...defaults.incremental.additionalProperties,
        ...(definition.incremental?.additionalProperties ?? {})
      }
    },
    tableMappings: definition.tableMappings?.length
      ? definition.tableMappings.map(mapping => ({
          ...emptyMapping(),
          ...mapping,
          primaryKeys: [...(mapping.primaryKeys ?? [])],
          includedColumns: [...(mapping.includedColumns ?? [])],
          columnMappings: { ...(mapping.columnMappings ?? {}) }
        }))
      : []
  }
}

const state = reactive({
  name: '',
  definition: emptyDefinition(),
  tableSelectionMode: 'DATABASE_ALL' as 'DATABASE_ALL' | 'SELECTED_TABLES'
})

watch(
  () => [props.job, props.definition],
  () => {
    state.name = props.job?.name ?? ''
    state.definition = normalizeDefinition(props.definition)
    state.tableSelectionMode = state.definition.tableMappings.length ? 'SELECTED_TABLES' : 'DATABASE_ALL'
  },
  { immediate: true }
)

watch(
  () => props.defaultLightningBinary,
  (next, previous) => {
    const current = state.definition.target.lightningBinary
    if (!current || current === 'tidb-lightning' || (previous && current === previous)) {
      state.definition.target.lightningBinary = next
    }
  }
)

watch(
  () => props.deploymentArchitecture,
  architecture => {
    state.definition.deploymentArchitecture = architecture
  }
)

const showsFullLoad = computed(() => state.definition.syncMode !== 'INCREMENTAL_ONLY')
const isCsvSource = computed(() => state.definition.source.databaseType === 'CSV')
const showsIncrementalManual = computed(() => state.definition.syncMode === 'INCREMENTAL_ONLY')
const showsIncrementalRuntime = computed(() => state.definition.syncMode !== 'FULL_ONLY')
const showsAutoIncremental = computed(() => state.definition.syncMode === 'FULL_AND_INCREMENTAL')
const isMongoDb = computed(() => state.definition.source.databaseType === 'MONGODB')
const isMySql = computed(() => state.definition.source.databaseType === 'MYSQL')
const isMariaDb = computed(() => state.definition.source.databaseType === 'MARIADB')
const isOracle = computed(() => state.definition.source.databaseType === 'ORACLE')
const isHana = computed(() => state.definition.source.databaseType === 'HANA')
const isPostgreSql = computed(() => state.definition.source.databaseType === 'POSTGRESQL')
const isSqlServer = computed(() => state.definition.source.databaseType === 'SQLSERVER')
const isDb2 = computed(() => state.definition.source.databaseType === 'DB2')
const activeSourceMeta = computed(() => sourceCatalogMap[state.definition.source.databaseType])
const activeSourcePermissionGuide = computed(() => sourcePermissionGuides[state.definition.source.databaseType])
const activeToolGuide = computed(() => sourceToolGuides[state.definition.source.databaseType])
const sourceSchemaLabel = computed(() => (isMongoDb.value ? '源端 Schema / Collection 所属库' : '源端 Schema'))
const sourceParametersPlaceholder = computed(() => {
  switch (state.definition.source.databaseType) {
    case 'MYSQL':
      return 'useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai'
    case 'MARIADB':
      return 'useUnicode=true&characterEncoding=utf8&useServerPrepStmts=true'
    case 'POSTGRESQL':
      return 'stringtype=unspecified&sslmode=disable'
    case 'ORACLE':
      return 'oracle.jdbc.timezoneAsRegion=false'
    case 'SQLSERVER':
      return 'encrypt=false;trustServerCertificate=true'
    case 'DB2':
      return 'currentSchema=APP;retrieveMessagesFromServerOnGetMessage=true;'
    case 'HANA':
      return 'reconnect=true&autocommit=false'
    case 'MONGODB':
      return 'authSource=admin&replicaSet=rs0'
    case 'CSV':
      return ''
  }
})
const sourceUrlPreview = computed(() => buildSourcePreview(state.definition))
const targetUrlPreview = computed(() => buildTargetPreview(state.definition.target))
const usesSelectedTables = computed(() => state.tableSelectionMode === 'SELECTED_TABLES' && !isCsvSource.value)
const sqlServerExportTool = computed({
  get: () => state.definition.fullLoad.additionalProperties.sqlServerExportTool || 'bcp',
  set: value => {
    state.definition.fullLoad.additionalProperties.sqlServerExportTool = value
  }
})
const postgreSqlExportMethod = computed({
  get: () => state.definition.fullLoad.additionalProperties.postgresExportMethod || 'psql_copy',
  set: value => {
    state.definition.fullLoad.additionalProperties.postgresExportMethod = value
  }
})

watch(
  () => state.definition.source.databaseType,
  (databaseType, previousType) => {
    const previousDefaultPort = previousType ? sourceDefaultPorts[previousType] : undefined
    const nextDefaultPort = sourceDefaultPorts[databaseType]
    if ((state.definition.source.port === previousDefaultPort || !state.definition.source.port) && nextDefaultPort > 0) {
      state.definition.source.port = nextDefaultPort
    }
    if (databaseType === 'CSV') {
      state.definition.syncMode = 'FULL_ONLY'
      state.tableSelectionMode = 'DATABASE_ALL'
      state.definition.tableMappings = []
    }
  }
)

function addMapping() {
  state.definition.tableMappings.push(emptyMapping())
}

function removeMapping(index: number) {
  state.definition.tableMappings.splice(index, 1)
  if (!state.definition.tableMappings.length) {
    addMapping()
  }
}

function selectMode(mode: SyncMode) {
  state.definition.syncMode = mode
}

function selectSourceType(type: SourceDatabaseType) {
  state.definition.source.databaseType = type
}

function selectTableSelectionMode(mode: 'DATABASE_ALL' | 'SELECTED_TABLES') {
  state.tableSelectionMode = mode
  if (mode === 'SELECTED_TABLES' && state.definition.tableMappings.length === 0) {
    addMapping()
  }
}

function submit() {
  const payload = structuredClone(state.definition)
  payload.tableMappings = state.tableSelectionMode === 'DATABASE_ALL'
    ? []
    : payload.tableMappings.filter(mapping => mapping.sourceTable || mapping.targetTable)
  emit('save', state.name, payload)
}

function normalizeQueryParameters(parameters: string): string {
  let normalized = parameters.trim()
  while (normalized.startsWith('?') || normalized.startsWith('&')) {
    normalized = normalized.slice(1)
  }
  return normalized
}

function normalizeSemicolonParameters(parameters: string): string {
  let normalized = parameters.trim()
  while (normalized.startsWith(';')) {
    normalized = normalized.slice(1)
  }
  return normalized
}

function buildSourcePreview(definition: SyncJobDefinition): string {
  const { source } = definition
  if (source.jdbcUrl?.trim()) {
    return source.jdbcUrl
  }
  if (source.databaseType === 'MONGODB') {
    const credentials = source.username
      ? `${encodeURIComponent(source.username)}${source.password ? `:${encodeURIComponent(source.password)}` : ''}@`
      : ''
    const host = `${source.host || ''}${source.port ? `:${source.port}` : ''}`
    const database = source.databaseName || ''
    const parameters = normalizeQueryParameters(source.jdbcParameters || '')
    return `mongodb://${credentials}${host}/${database}${parameters ? `?${parameters}` : ''}`
  }
  if (source.databaseType === 'CSV') {
    return definition.fullLoad.exportBaseDir || ''
  }
  if (source.databaseType === 'MARIADB') {
    const parameters = normalizeQueryParameters(source.jdbcParameters || '')
    return `jdbc:mariadb://${source.host || ''}${source.port ? `:${source.port}` : ''}/${source.databaseName || ''}${parameters ? `?${parameters}` : ''}`
  }
  if (source.databaseType === 'SQLSERVER') {
    const parameters = normalizeSemicolonParameters(source.jdbcParameters || '')
    return `jdbc:sqlserver://${source.host || ''}${source.port ? `:${source.port}` : ''}${source.databaseName ? `;databaseName=${source.databaseName}` : ''}${parameters ? `;${parameters}` : ''}`
  }
  if (source.databaseType === 'ORACLE') {
    const parameters = normalizeQueryParameters(source.jdbcParameters || '')
    return `jdbc:oracle:thin:@${source.host || ''}${source.port ? `:${source.port}` : ''}${source.databaseName ? `/${source.databaseName}` : ''}${parameters ? `?${parameters}` : ''}`
  }
  if (source.databaseType === 'HANA') {
    const parameters = normalizeQueryParameters(source.jdbcParameters || '')
    const base = `jdbc:sap://${source.host || ''}${source.port ? `:${source.port}` : ''}`
    if (source.databaseName && parameters) {
      return `${base}/?databaseName=${source.databaseName}&${parameters}`
    }
    if (source.databaseName) {
      return `${base}/?databaseName=${source.databaseName}`
    }
    return `${base}${parameters ? `?${parameters}` : ''}`
  }
  if (source.databaseType === 'DB2') {
    const parameters = normalizeSemicolonParameters(source.jdbcParameters || '')
    return `jdbc:db2://${source.host || ''}${source.port ? `:${source.port}` : ''}/${source.databaseName || ''}${parameters ? `:${parameters}` : ''}`
  }
  const prefix = source.databaseType === 'POSTGRESQL' ? 'jdbc:postgresql' : 'jdbc:mysql'
  const parameters = normalizeQueryParameters(source.jdbcParameters || '')
  return `${prefix}://${source.host || ''}${source.port ? `:${source.port}` : ''}/${source.databaseName || ''}${parameters ? `?${parameters}` : ''}`
}

function buildTargetPreview(target: SyncJobDefinition['target']): string {
  if (target.jdbcUrl?.trim()) {
    return target.jdbcUrl
  }
  const parameters = normalizeQueryParameters(target.jdbcParameters || '')
  return `jdbc:mysql://${target.host || ''}${target.port ? `:${target.port}` : ''}/${target.databaseName || ''}${parameters ? `?${parameters}` : ''}`
}
</script>

<template>
  <section class="panel form-panel">
    <div class="panel-header">
      <div>
        <p class="eyebrow">任务明细</p>
        <h2>同步任务参数</h2>
      </div>
      <button class="primary" @click="submit">保存任务</button>
    </div>

    <div class="sub-panel guide-panel">
      <div class="sub-panel-header">
        <h3>任务创建逻辑</h3>
      </div>
      <p class="guide-text">
        先选择任务模式，再填写源端和 TiDB 连接信息。全量任务负责导出并导入，增量任务负责从日志位点继续同步，
        全量 + 增量任务则会在导出前自动记录起点，导入完成后无缝切到增量同步。
      </p>
      <div class="tag-list">
        <span v-for="field in requiredConnectionFields" :key="field" class="tag">{{ field }}</span>
      </div>
      <div class="guide-grid">
        <article class="guide-card">
          <h4>当前部署架构</h4>
          <p class="guide-text">来自首页“软件说明与部署设置”的全局配置。</p>
          <div class="tag-list">
            <span class="tag">{{ props.deploymentArchitecture }}</span>
          </div>
        </article>
        <article class="guide-card">
          <h4>当前源端工具链</h4>
          <div class="source-card-header inline-source-header">
            <div class="source-logo" :style="{ '--logo-accent': activeSourceMeta.accent, '--logo-surface': activeSourceMeta.surface }">
              <span>{{ activeSourceMeta.logoText }}</span>
            </div>
            <div>
              <strong>{{ activeSourceMeta.label }}</strong>
              <p class="guide-text">{{ activeSourceMeta.summary }}</p>
            </div>
          </div>
          <p class="guide-text"><strong>全量：</strong>{{ activeToolGuide.full }}</p>
          <p class="guide-text">{{ activeToolGuide.fullNote }}</p>
          <p class="guide-text"><strong>增量：</strong>{{ activeToolGuide.incremental }}</p>
          <p class="guide-text">{{ activeToolGuide.incrementalNote }}</p>
        </article>
      </div>
    </div>

    <div class="mode-grid">
        <button
          v-for="item in modeCards"
          :key="item.mode"
          class="mode-card"
          :class="{ active: state.definition.syncMode === item.mode }"
          :disabled="isCsvSource && item.mode !== 'FULL_ONLY'"
          @click="selectMode(item.mode)"
        >
        <strong>{{ item.title }}</strong>
        <span>{{ item.description }}</span>
      </button>
    </div>

    <div class="sub-panel guide-panel subtle-panel">
      <div class="sub-panel-header">
        <h3>当前模式说明</h3>
      </div>
      <p v-if="state.definition.syncMode === 'FULL_ONLY'" class="guide-text">
        当前任务只执行全量导出和 TiDB Lightning 导入，不启动 CDC，也不要求填写日志位点。
      </p>
      <p v-else-if="state.definition.syncMode === 'INCREMENTAL_ONLY'" class="guide-text">
        当前任务只执行增量同步，需要你在页面填写起始日志位点，例如 Oracle 的 SCN、MySQL 的 binlog 文件名与位置。
      </p>
      <p v-else class="guide-text">
        当前任务会先执行全量导出与导入，再自动从导出前捕获的日志位点继续增量同步，所以这里不需要手工填写起点。
      </p>
    </div>

    <div class="sub-panel">
      <div class="sub-panel-header">
        <h3>源端目录</h3>
      </div>
      <div class="source-picker-grid">
        <button
          v-for="item in sourceCatalog"
          :key="item.type"
          class="source-picker-card"
          :class="{ active: state.definition.source.databaseType === item.type }"
          @click="selectSourceType(item.type)"
        >
          <div class="source-card-header">
            <div class="source-logo" :style="{ '--logo-accent': item.accent, '--logo-surface': item.surface }">
              <span>{{ item.logoText }}</span>
            </div>
            <div>
              <strong>{{ item.label }}</strong>
              <small>{{ item.vendor }}</small>
            </div>
          </div>
          <p>{{ item.summary }}</p>
          <div class="tag-list compact-tags">
            <span class="tag">全量：{{ item.fullTool }}</span>
            <span class="tag">增量：{{ item.incrementalTool }}</span>
          </div>
        </button>
      </div>
    </div>

    <div class="sub-panel">
      <div class="sub-panel-header">
        <h3>表选择范围</h3>
      </div>
      <div v-if="isCsvSource" class="sub-panel guide-panel subtle-panel">
        <div class="sub-panel-header">
          <h3>CSV 数据源说明</h3>
        </div>
        <p class="guide-text">
          CSV 数据源按目录中的文件名自动识别目标库表，不需要再配置数据库连接、表范围或增量参数，因此这里固定按目录文件执行全量导入。
        </p>
      </div>
      <div class="mode-grid">
        <button
          v-for="item in tableSelectionCards"
          :key="item.key"
          class="mode-card"
          :class="{ active: state.tableSelectionMode === item.key }"
          :disabled="isCsvSource"
          @click="selectTableSelectionMode(item.key)"
        >
          <strong>{{ item.title }}</strong>
          <span>{{ item.description }}</span>
        </button>
      </div>
      <div class="sub-panel guide-panel subtle-panel" v-if="state.tableSelectionMode === 'DATABASE_ALL'">
        <div class="sub-panel-header">
          <h3>整库同步说明</h3>
        </div>
        <p class="guide-text">
          当前任务会在启动时自动读取你填写的数据库 / schema 元数据，发现该范围下的所有表，并按“源表名 -> TiDB 同名目标表”的默认规则执行同步。
          如果只想同步某张表，请切换到“指定表同步”。
        </p>
      </div>
    </div>

    <div class="sub-panel" v-if="!isCsvSource">
      <div class="sub-panel-header">
        <h3>基础信息与源端连接</h3>
      </div>
      <div class="form-grid">
        <label>
          <span>任务名称</span>
          <input v-model="state.name" placeholder="例如：mysql-order-to-tidb" />
        </label>
        <label>
          <span>源端主机 / IP</span>
          <input v-model="state.definition.source.host" />
        </label>
        <label>
          <span>源端端口</span>
          <input v-model.number="state.definition.source.port" type="number" />
        </label>
        <label>
          <span>源端数据库</span>
          <input v-model="state.definition.source.databaseName" />
        </label>
        <label>
          <span>{{ sourceSchemaLabel }}</span>
          <input v-model="state.definition.source.schemaName" />
        </label>
        <label>
          <span>源端用户名</span>
          <input v-model="state.definition.source.username" />
        </label>
        <label>
          <span>源端密码</span>
          <input v-model="state.definition.source.password" type="password" />
        </label>
        <label class="wide">
          <span>{{ isMongoDb ? '连接参数' : 'JDBC 参数' }}</span>
          <input v-model="state.definition.source.jdbcParameters" :placeholder="sourceParametersPlaceholder" />
        </label>
        <label class="wide">
          <span>{{ isMongoDb ? '连接串预览' : 'JDBC URL 预览' }}</span>
          <input :value="sourceUrlPreview" disabled />
        </label>
      </div>
    </div>

    <div class="sub-panel" v-else>
      <div class="sub-panel-header">
        <h3>CSV 数据源</h3>
      </div>
      <div class="form-grid">
        <label>
          <span>任务名称</span>
          <input v-model="state.name" placeholder="例如：csv-directory-to-tidb" />
        </label>
        <label class="wide">
          <span>CSV 目录</span>
          <input v-model="state.definition.fullLoad.exportBaseDir" placeholder="/data/csv-load" />
        </label>
        <label class="wide">
          <span>目录说明</span>
          <input value="目录下文件名需满足 db.table.00000001.csv。系统会先做字符集检查与 UTF-8 转换，再对大于 200MiB 的文件切分为 128MiB。" disabled />
        </label>
      </div>
    </div>

    <div class="sub-panel">
      <div class="sub-panel-header">
        <h3>权限与授权示例</h3>
      </div>
      <div class="guide-grid">
        <article class="guide-card">
          <h4>源端账号权限</h4>
          <p class="guide-text">{{ activeSourcePermissionGuide.summary }}</p>
          <div class="tag-list">
            <span v-for="item in activeSourcePermissionGuide.privileges" :key="item" class="tag">{{ item }}</span>
          </div>
          <pre><code>{{ activeSourcePermissionGuide.example }}</code></pre>
        </article>

        <article class="guide-card">
          <h4>TiDB 目标端权限</h4>
          <p class="guide-text">{{ targetPermissionGuide.summary }}</p>
          <div class="tag-list">
            <span v-for="item in targetPermissionGuide.privileges" :key="item" class="tag">{{ item }}</span>
          </div>
          <pre><code>{{ targetPermissionGuide.example }}</code></pre>
        </article>
      </div>
    </div>

    <div class="sub-panel">
      <div class="sub-panel-header">
        <h3>TiDB 目标端</h3>
      </div>
      <div class="form-grid">
        <label>
          <span>TiDB 主机</span>
          <input v-model="state.definition.target.host" />
        </label>
        <label>
          <span>TiDB 端口</span>
          <input v-model.number="state.definition.target.port" type="number" />
        </label>
        <label>
          <span>TiDB 目标库</span>
          <input v-model="state.definition.target.databaseName" />
        </label>
        <label>
          <span>TiDB 用户名</span>
          <input v-model="state.definition.target.username" />
        </label>
        <label>
          <span>TiDB 密码</span>
          <input v-model="state.definition.target.password" type="password" />
        </label>
        <label class="wide">
          <span>TiDB JDBC 参数</span>
          <input v-model="state.definition.target.jdbcParameters" placeholder="useUnicode=true&characterEncoding=utf8&rewriteBatchedStatements=true" />
        </label>
        <label class="wide">
          <span>TiDB JDBC URL 预览</span>
          <input :value="targetUrlPreview" disabled />
        </label>
        <label>
          <span>TiDB Status Port</span>
          <input v-model.number="state.definition.target.statusPort" type="number" />
        </label>
        <label class="wide" v-if="showsFullLoad">
          <span>Lightning 二进制绝对路径</span>
          <input v-model="state.definition.target.lightningBinary" />
        </label>
      </div>
    </div>

    <div class="sub-panel" v-if="showsFullLoad">
      <div class="sub-panel-header">
        <h3>全量同步配置</h3>
      </div>
      <div class="form-grid">
        <label v-if="!isCsvSource">
          <span>全量工具路径 / 命令</span>
          <input v-model="state.definition.fullLoad.exportToolBinary" :placeholder="activeToolGuide.full" />
        </label>
        <label v-if="isSqlServer">
          <span>SQL Server 全量工具</span>
          <select v-model="sqlServerExportTool">
            <option v-for="item in sqlServerExportToolCards" :key="item.key" :value="item.key">
              {{ item.title }}
            </option>
          </select>
        </label>
        <label v-if="isPostgreSql">
          <span>PostgreSQL 导出方式</span>
          <select v-model="postgreSqlExportMethod">
            <option v-for="item in postgreSqlExportMethodCards" :key="item.key" :value="item.key">
              {{ item.title }}
            </option>
          </select>
        </label>
        <label>
          <span>{{ isCsvSource ? 'CSV 源目录' : '全量导出目录' }}</span>
          <input v-model="state.definition.fullLoad.exportBaseDir" />
        </label>
        <label v-if="!isCsvSource">
          <span>并行度</span>
          <input v-model.number="state.definition.fullLoad.parallelism" type="number" />
        </label>
        <label v-if="!isCsvSource">
          <span>Fetch Size</span>
          <input v-model.number="state.definition.fullLoad.fetchSize" type="number" />
        </label>
        <label class="wide" v-if="!isCsvSource">
          <span>全量导出命令模板</span>
          <textarea
            v-model="state.definition.source.commandTemplate"
            rows="3"
            placeholder="可覆盖默认命令，支持 ${host} ${port} ${database} ${schema} ${table} ${username} ${password} ${file}"
          />
        </label>
        <label class="wide">
          <span>导出行为说明</span>
          <input :value="activeToolGuide.fullNote" disabled />
        </label>
        <div class="wide guide-grid" v-if="isSqlServer">
          <article
            v-for="item in sqlServerExportToolCards"
            :key="item.key"
            class="guide-card"
            :class="{ active: sqlServerExportTool === item.key }"
          >
            <h4>{{ item.title }}</h4>
            <p class="guide-text">{{ item.description }}</p>
          </article>
        </div>
        <div class="wide guide-grid" v-if="isPostgreSql">
          <article
            v-for="item in postgreSqlExportMethodCards"
            :key="item.key"
            class="guide-card"
            :class="{ active: postgreSqlExportMethod === item.key }"
          >
            <h4>{{ item.title }}</h4>
            <p class="guide-text">{{ item.description }}</p>
          </article>
        </div>
      </div>
    </div>

    <div class="sub-panel" v-if="showsIncrementalRuntime">
      <div class="sub-panel-header">
        <h3>增量同步配置</h3>
      </div>
      <div class="form-grid">
        <label>
          <span>Server Name</span>
          <input v-model="state.definition.incremental.serverName" />
        </label>
        <label>
          <span>Offset 存储路径</span>
          <input v-model="state.definition.incremental.offsetStoragePath" />
        </label>
        <label>
          <span>轮询间隔(秒)</span>
          <input v-model.number="state.definition.incremental.pollingIntervalSeconds" type="number" />
        </label>
        <label>
          <span>批大小</span>
          <input v-model.number="state.definition.incremental.batchSize" type="number" />
        </label>

        <template v-if="showsIncrementalManual && isOracle">
          <label>
            <span>Oracle 增量模式</span>
            <select v-model="state.definition.incremental.additionalProperties.oracleAdapter">
              <option value="logminer">logminer</option>
              <option value="logminer_unbuffered">logminer_unbuffered</option>
              <option value="xstream">xstream</option>
            </select>
          </label>
          <label>
            <span>Oracle 起始 SCN</span>
            <input v-model="state.definition.incremental.additionalProperties.oracleStartScn" placeholder="例如 123456789" />
          </label>
          <label>
            <span>Oracle PDB 名称</span>
            <input v-model="state.definition.incremental.additionalProperties.oraclePdbName" placeholder="例如 ORCLPDB1，可选" />
          </label>
          <label v-if="state.definition.incremental.additionalProperties.oracleAdapter === 'xstream'">
            <span>XStream Outbound Server</span>
            <input v-model="state.definition.incremental.additionalProperties.oracleOutServerName" placeholder="例如 dbzxout" />
          </label>
          <label class="wide">
            <span>说明</span>
            <input value="Oracle 增量链路参考 Debezium Oracle Connector，支持 LogMiner 和 XStream。纯增量任务可填写 SCN；全量 + 增量模式下系统会自动记录起点。" disabled />
          </label>
        </template>

        <template v-if="showsIncrementalManual && isMySql">
          <label>
            <span>MySQL Server ID</span>
            <input v-model="state.definition.incremental.additionalProperties.mysqlServerId" placeholder="例如 6601，需在复制集群中唯一" />
          </label>
          <label>
            <span>Snapshot Mode</span>
            <select v-model="state.definition.incremental.additionalProperties.mysqlSnapshotMode">
              <option value="no_data">no_data</option>
              <option value="when_needed">when_needed</option>
              <option value="initial">initial</option>
            </select>
          </label>
          <label>
            <span>Binlog 文件名</span>
            <input v-model="state.definition.incremental.additionalProperties.mysqlBinlogFilename" placeholder="例如 mysql-bin.000123" />
          </label>
          <label>
            <span>Binlog 位置</span>
            <input v-model="state.definition.incremental.additionalProperties.mysqlBinlogPosition" placeholder="例如 456789" />
          </label>
          <label class="wide">
            <span>说明</span>
            <input value="纯增量 MySQL 任务需要填写 binlog 文件名和位置。全量 + 增量模式下，系统会在 dumpling 导出前自动记录起点。" disabled />
          </label>
        </template>

        <template v-if="showsIncrementalManual && isMariaDb">
          <label>
            <span>MariaDB Server ID</span>
            <input v-model="state.definition.incremental.additionalProperties.mariaDbServerId" placeholder="例如 7601，需在复制集群中唯一" />
          </label>
          <label>
            <span>Snapshot Mode</span>
            <select v-model="state.definition.incremental.additionalProperties.mariaDbSnapshotMode">
              <option value="no_data">no_data</option>
              <option value="when_needed">when_needed</option>
              <option value="initial">initial</option>
            </select>
          </label>
          <label class="wide">
            <span>说明</span>
            <input value="MariaDB 增量链路参考 Debezium MariaDB Connector。当前平台默认从 connector offset 接续，若现场需要特殊起点，建议通过额外 Debezium 参数或自定义 offset 文件接入。" disabled />
          </label>
        </template>

        <template v-if="showsAutoIncremental">
          <label class="wide">
            <span>全量 + 增量切换规则</span>
            <input value="当前模式无需手工填写日志起点。系统会先自动记录导出前的起点，待 Lightning 导入完成后再启动增量同步。" disabled />
          </label>
        </template>

        <label v-if="!isMySql && !isMongoDb" class="wide">
          <span>Slot / Publication 说明</span>
          <input value="PostgreSQL 使用 slot 和 publication；其他非 MySQL 数据源可按 connector 要求填写。无特殊需要可保留默认值。" disabled />
        </label>
        <label v-if="isPostgreSql">
          <span>PostgreSQL Plugin</span>
          <select v-model="state.definition.incremental.additionalProperties.postgresPluginName">
            <option value="pgoutput">pgoutput</option>
            <option value="decoderbufs">decoderbufs</option>
            <option value="wal2json">wal2json</option>
          </select>
        </label>
        <label v-if="isPostgreSql">
          <span>Publication Auto Create Mode</span>
          <select v-model="state.definition.incremental.additionalProperties.postgresPublicationAutoCreateMode">
            <option value="all_tables">all_tables</option>
            <option value="filtered">filtered</option>
            <option value="disabled">disabled</option>
          </select>
        </label>
        <label v-if="!isMySql && !isMongoDb">
          <span>Slot Name</span>
          <input v-model="state.definition.incremental.slotName" />
        </label>
        <label v-if="!isMySql && !isMongoDb">
          <span>Publication Name</span>
          <input v-model="state.definition.incremental.publicationName" />
        </label>
        <label v-if="isSqlServer" class="wide">
          <span>说明</span>
          <input value="SQL Server 增量链路参考 Debezium SQL Server Connector，使用 CDC 变更表读取增量。请确保数据库和目标表已开启 CDC。" disabled />
        </label>
        <label v-if="isPostgreSql" class="wide">
          <span>说明</span>
          <input value="PostgreSQL 增量链路参考 Debezium PostgreSQL Connector，建议显式配置 plugin.name、slot.name、publication.name 与 publication.autocreate.mode。" disabled />
        </label>
        <label v-if="isDb2" class="wide">
          <span>说明</span>
          <input value="Db2 增量链路参考 Debezium Db2 Connector。建议先确认 CDC 变更表/日志采集配置，再接入本平台。" disabled />
        </label>
        <label v-if="isHana" class="wide">
          <span>说明</span>
          <input value="HANA 增量依赖高水位轮询，请在表映射中填写 incrementalColumn，例如 update_time。" disabled />
        </label>
      </div>
    </div>

    <div class="sub-panel" v-if="usesSelectedTables">
      <div class="sub-panel-header">
        <h3>表映射</h3>
        <button class="ghost" @click="addMapping">新增表</button>
      </div>

      <div v-for="(mapping, index) in state.definition.tableMappings" :key="index" class="mapping-card">
        <div class="mapping-grid">
          <label>
            <span>源目录 / Catalog</span>
            <input v-model="mapping.sourceCatalog" />
          </label>
          <label>
            <span>源 Schema</span>
            <input v-model="mapping.sourceSchema" />
          </label>
          <label>
            <span>源表 / Collection</span>
            <input v-model="mapping.sourceTable" />
          </label>
          <label>
            <span>目标库</span>
            <input v-model="mapping.targetDatabase" />
          </label>
          <label>
            <span>目标表</span>
            <input v-model="mapping.targetTable" />
          </label>
          <label>
            <span>主键</span>
            <input
              :value="mapping.primaryKeys.join(',')"
              @input="mapping.primaryKeys = String(($event.target as HTMLInputElement).value).split(',').map(v => v.trim()).filter(Boolean)"
              placeholder="id,tenant_id"
            />
          </label>
          <label v-if="showsIncrementalRuntime">
            <span>增量列</span>
            <input v-model="mapping.incrementalColumn" :placeholder="isMongoDb ? 'MongoDB 可留空' : 'HANA 建议填写更新时间列'" />
          </label>
          <label class="wide">
            <span>导出字段</span>
            <input
              :value="mapping.includedColumns.join(',')"
              @input="mapping.includedColumns = String(($event.target as HTMLInputElement).value).split(',').map(v => v.trim()).filter(Boolean)"
              :placeholder="isMongoDb ? '_id,name,address.city,updatedAt' : '可选：限制导出列或控制顺序'"
            />
          </label>
          <label class="wide">
            <span>字段映射</span>
            <input
              :value="Object.entries(mapping.columnMappings).map(([k, v]) => `${k}:${v}`).join(',')"
              @input="mapping.columnMappings = Object.fromEntries(String(($event.target as HTMLInputElement).value).split(',').map(v => v.trim()).filter(Boolean).map(item => { const [from, to] = item.split(':').map(part => part.trim()); return [from, to ?? from] }))"
              :placeholder="isMongoDb ? '_id:id,address.city:city' : 'source_col:target_col'"
            />
          </label>
        </div>
        <button class="ghost danger" @click="removeMapping(index)">删除表映射</button>
      </div>
    </div>
  </section>
</template>
