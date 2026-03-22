import type { SourceDatabaseType } from './types'

export type SourceCatalogItem = {
  type: SourceDatabaseType
  label: string
  vendor: string
  logoText: string
  accent: string
  surface: string
  fullTool: string
  incrementalTool: string
  fullNote: string
  incrementalNote: string
  summary: string
}

export const sourceCatalog: SourceCatalogItem[] = [
  {
    type: 'CSV',
    label: 'CSV',
    vendor: 'File Directory',
    logoText: 'CSV',
    accent: '#0f766e',
    surface: 'rgba(15, 118, 110, 0.14)',
    fullTool: '目录预处理 + TiDB Lightning',
    incrementalTool: '不支持',
    fullNote: '先检查字符集并统一转换为 UTF-8；若单文件大于 200 MiB，则按 128 MiB 切分并保持 db.table.00000001.csv 命名。',
    incrementalNote: 'CSV 数据源仅支持 FULL_ONLY，全量导入完成后任务结束。',
    summary: '适合已有标准化 CSV 目录的离线导入场景。'
  },
  {
    type: 'MYSQL',
    label: 'MySQL',
    vendor: 'Oracle',
    logoText: 'My',
    accent: '#0f5d88',
    surface: 'rgba(15, 93, 136, 0.14)',
    fullTool: 'dumpling',
    incrementalTool: 'Debezium MySQL Connector',
    fullNote: '当前平台默认使用 dumpling 导出，再由 Lightning 导入；保留自定义命令模板能力。',
    incrementalNote: '增量读取 binlog；纯增量任务可填写 binlog 文件名和位置。',
    summary: '适合标准 MySQL 主从或单实例日志增量采集。'
  },
  {
    type: 'MARIADB',
    label: 'MariaDB',
    vendor: 'MariaDB plc',
    logoText: 'Ma',
    accent: '#1f6b75',
    surface: 'rgba(31, 107, 117, 0.14)',
    fullTool: 'mariadb-dump / 自定义导出命令',
    incrementalTool: 'Debezium MariaDB Connector',
    fullNote: '默认按 MariaDB 原生命令模板导出，支持通过命令模板覆盖为现场最合适的 CSV 导出方式。',
    incrementalNote: '增量读取 MariaDB binlog，配置方式与 MySQL 类似但使用独立 connector。',
    summary: '适合 MySQL 兼容栈但明确采用 MariaDB 日志语义的场景。'
  },
  {
    type: 'ORACLE',
    label: 'Oracle',
    vendor: 'Oracle',
    logoText: 'Or',
    accent: '#c74634',
    surface: 'rgba(199, 70, 52, 0.14)',
    fullTool: 'sqluldr2',
    incrementalTool: 'Debezium Oracle Connector',
    fullNote: '全量导出为 CSV，并按 128 MiB 切分后重命名为 Lightning 可识别格式。',
    incrementalNote: '支持 LogMiner 和 XStream；纯增量任务可填写 SCN。',
    summary: '适合核心业务库迁移和全量+增量连续同步。'
  },
  {
    type: 'SQLSERVER',
    label: 'SQL Server',
    vendor: 'Microsoft',
    logoText: 'MS',
    accent: '#0a6bdc',
    surface: 'rgba(10, 107, 220, 0.14)',
    fullTool: 'bcp (default) / sqlcmd (optional)',
    incrementalTool: 'Debezium SQL Server Connector',
    fullNote: '默认使用 bcp 做大表批量导出；sqlcmd 作为可选查询结果导出工具，适合权限或现场工具受限时兜底。Linux 上两者均建议配套安装 Microsoft ODBC 运行库。',
    incrementalNote: '增量依赖 SQL Server CDC，建议先由 DBA 开启数据库和表级 CDC。',
    summary: '适合企业内微软技术栈数据库到 TiDB 的迁移。'
  },
  {
    type: 'POSTGRESQL',
    label: 'PostgreSQL',
    vendor: 'PostgreSQL Global Development Group',
    logoText: 'Pg',
    accent: '#336791',
    surface: 'rgba(51, 103, 145, 0.14)',
    fullTool: 'psql \\copy (default) / COPY / psql --csv',
    incrementalTool: 'Debezium PostgreSQL Connector',
    fullNote: '默认通过 psql 客户端执行 \\copy 生成 UTF-8 CSV 文件；可选服务器端 COPY 或 psql --csv 查询导出。其中 COPY 需要数据库服务器文件写权限，通常不是默认首选。',
    incrementalNote: '增量依赖 logical replication、slot 和 publication。',
    summary: '适合标准 PG、云数据库 PG 以及逻辑复制场景。'
  },
  {
    type: 'DB2',
    label: 'Db2',
    vendor: 'IBM',
    logoText: 'Db2',
    accent: '#0f62fe',
    surface: 'rgba(15, 98, 254, 0.14)',
    fullTool: 'db2 EXPORT',
    incrementalTool: 'Debezium Db2 Connector',
    fullNote: '全量导出使用 Db2 原生命令 export 生成 DEL/CSV 兼容文件。',
    incrementalNote: '增量读取 Db2 CDC 变更流，适合大型传统企业数据库迁移。',
    summary: '适合 IBM 传统核心库接入到 TiDB。'
  },
  {
    type: 'HANA',
    label: 'SAP HANA',
    vendor: 'SAP',
    logoText: 'Ha',
    accent: '#0faaff',
    surface: 'rgba(15, 170, 255, 0.14)',
    fullTool: 'hdbsql',
    incrementalTool: '高水位轮询',
    fullNote: '全量导出使用 hdbsql 客户端导出为 CSV。',
    incrementalNote: '增量使用更新时间列轮询，建议在表映射中明确 incrementalColumn。',
    summary: '适合 SAP 周边数据下沉和准实时同步。'
  },
  {
    type: 'MONGODB',
    label: 'MongoDB',
    vendor: 'MongoDB Inc.',
    logoText: 'Mo',
    accent: '#00a35c',
    surface: 'rgba(0, 163, 92, 0.14)',
    fullTool: 'mongoexport',
    incrementalTool: 'MongoDB change streams',
    fullNote: '全量导出使用 mongoexport 输出 CSV，必须显式指定 includedColumns。',
    incrementalNote: '增量依赖 change streams，页面会显示最新 resume token / 位点信息。',
    summary: '适合文档型数据向 TiDB 做结构化下沉。'
  }
]

export const sourceCatalogMap = Object.fromEntries(
  sourceCatalog.map(item => [item.type, item])
) as Record<SourceDatabaseType, SourceCatalogItem>
