import type {
  CompatibilityReport,
  CompatibilityReportUpsert,
  ConnectionProfilePermissionCheck,
  ConnectionProfile,
  ConnectionProfileRole,
  ConnectionProfileUpsert,
  CsvDirectoryImportResponse,
  CsvDirectoryPrepareResponse,
  CsvImportTarget,
  DashboardOverview,
  DeploymentArchitecture,
  ManagedToolPaths,
  SchemaSyncTask,
  SchemaSyncTaskUpsert,
  RuntimeLogFile,
  RuntimeLogTail,
  SystemSettings,
  SyncJob,
  SyncJobDefinition,
  SyncJobLog,
  ToolConfig,
  ToolConfigUpsert
} from './types'

const headers = {
  'Content-Type': 'application/json'
}

async function extractErrorMessage(response: Response, fallbackMessage: string): Promise<string> {
  const body = await response.text()
  if (!body) {
    return fallbackMessage
  }
  try {
    const parsed = JSON.parse(body) as { message?: string; error?: string }
    return parsed.message || parsed.error || body
  } catch {
    return body
  }
}

async function requestJson<T>(input: RequestInfo | URL, init: RequestInit | undefined, fallbackMessage: string): Promise<T> {
  const response = await fetch(input, init)
  if (!response.ok) {
    throw new Error(await extractErrorMessage(response, fallbackMessage))
  }
  return await response.json()
}

async function requestVoid(input: RequestInfo | URL, init: RequestInit | undefined, fallbackMessage: string): Promise<void> {
  const response = await fetch(input, init)
  if (!response.ok) {
    throw new Error(await extractErrorMessage(response, fallbackMessage))
  }
}

export async function fetchOverview(): Promise<DashboardOverview> {
  return await requestJson('/api/dashboard/overview', undefined, '加载概览失败')
}

export async function fetchManagedToolPaths(architecture: DeploymentArchitecture): Promise<ManagedToolPaths> {
  return await requestJson(`/api/tools/managed-paths?architecture=${architecture}`, undefined, '加载工具默认路径失败')
}

export async function fetchConnectionProfiles(role?: ConnectionProfileRole): Promise<ConnectionProfile[]> {
  const suffix = role ? `?role=${role}` : ''
  return await requestJson(`/api/connection-profiles${suffix}`, undefined, '加载数据源/目标失败')
}

export async function createConnectionProfile(payload: ConnectionProfileUpsert): Promise<ConnectionProfile> {
  return await requestJson('/api/connection-profiles', {
    method: 'POST',
    headers,
    body: JSON.stringify(payload)
  }, '创建数据源/目标失败')
}

export async function updateConnectionProfile(id: number, payload: ConnectionProfileUpsert): Promise<ConnectionProfile> {
  return await requestJson(`/api/connection-profiles/${id}`, {
    method: 'PUT',
    headers,
    body: JSON.stringify(payload)
  }, '更新数据源/目标失败')
}

export async function checkConnectionProfilePermissions(id: number): Promise<ConnectionProfilePermissionCheck> {
  return await requestJson(`/api/connection-profiles/${id}/permission-check`, {
    method: 'POST'
  }, '执行权限检测失败')
}

export async function fetchToolConfigs(): Promise<ToolConfig[]> {
  return await requestJson('/api/tool-configs', undefined, '加载工具配置失败')
}

export async function createToolConfig(payload: ToolConfigUpsert): Promise<ToolConfig> {
  return await requestJson('/api/tool-configs', {
    method: 'POST',
    headers,
    body: JSON.stringify(payload)
  }, '创建工具配置失败')
}

export async function updateToolConfig(id: number, payload: ToolConfigUpsert): Promise<ToolConfig> {
  return await requestJson(`/api/tool-configs/${id}`, {
    method: 'PUT',
    headers,
    body: JSON.stringify(payload)
  }, '更新工具配置失败')
}

export async function fetchSchemaTasks(): Promise<SchemaSyncTask[]> {
  return await requestJson('/api/schema-tasks', undefined, '加载表结构同步任务失败')
}

export async function fetchCompatibilityReports(): Promise<CompatibilityReport[]> {
  return await requestJson('/api/compatibility-reports', undefined, '加载兼容性检测报告失败')
}

export async function createCompatibilityReport(payload: CompatibilityReportUpsert): Promise<CompatibilityReport> {
  return await requestJson('/api/compatibility-reports', {
    method: 'POST',
    headers,
    body: JSON.stringify(payload)
  }, '创建兼容性检测任务失败')
}

export async function updateCompatibilityReport(id: number, payload: CompatibilityReportUpsert): Promise<CompatibilityReport> {
  return await requestJson(`/api/compatibility-reports/${id}`, {
    method: 'PUT',
    headers,
    body: JSON.stringify(payload)
  }, '更新兼容性检测任务失败')
}

export async function executeCompatibilityReport(id: number): Promise<CompatibilityReport> {
  return await requestJson(`/api/compatibility-reports/${id}/execute`, {
    method: 'POST'
  }, '执行兼容性检测失败')
}

export async function createSchemaTask(payload: SchemaSyncTaskUpsert): Promise<SchemaSyncTask> {
  return await requestJson('/api/schema-tasks', {
    method: 'POST',
    headers,
    body: JSON.stringify(payload)
  }, '创建表结构同步任务失败')
}

export async function updateSchemaTask(id: number, payload: SchemaSyncTaskUpsert): Promise<SchemaSyncTask> {
  return await requestJson(`/api/schema-tasks/${id}`, {
    method: 'PUT',
    headers,
    body: JSON.stringify(payload)
  }, '更新表结构同步任务失败')
}

export async function executeSchemaTask(id: number): Promise<SchemaSyncTask> {
  return await requestJson(`/api/schema-tasks/${id}/execute`, {
    method: 'POST'
  }, '执行表结构同步任务失败')
}

export async function fetchSystemSettings(): Promise<SystemSettings> {
  return await requestJson('/api/settings/system', undefined, '加载系统设置失败')
}

export async function saveSystemSettings(deploymentArchitecture: DeploymentArchitecture): Promise<SystemSettings> {
  return await requestJson('/api/settings/system', {
    method: 'PUT',
    headers,
    body: JSON.stringify({ deploymentArchitecture })
  }, '保存系统设置失败')
}

export async function fetchJobs(): Promise<SyncJob[]> {
  return await requestJson('/api/jobs', undefined, '加载任务列表失败')
}

export async function fetchJobLogs(id: number): Promise<SyncJobLog[]> {
  return await requestJson(`/api/jobs/${id}/logs`, undefined, '加载任务日志失败')
}

export async function fetchJobDefinition(id: number): Promise<SyncJobDefinition> {
  const payload = await requestJson<{ definition: SyncJobDefinition }>(`/api/jobs/${id}/definition`, undefined, '加载任务定义失败')
  return payload.definition
}

export async function createJob(name: string, definition: SyncJobDefinition): Promise<SyncJob> {
  return await requestJson('/api/jobs', {
    method: 'POST',
    headers,
    body: JSON.stringify({ name, definition })
  }, '创建任务失败')
}

export async function updateJob(id: number, name: string, definition: SyncJobDefinition): Promise<SyncJob> {
  return await requestJson(`/api/jobs/${id}`, {
    method: 'PUT',
    headers,
    body: JSON.stringify({ name, definition })
  }, '更新任务失败')
}

export async function startJob(id: number): Promise<void> {
  await requestVoid(`/api/jobs/${id}/start`, { method: 'POST' }, '启动任务失败')
}

export async function stopJob(id: number): Promise<void> {
  await requestVoid(`/api/jobs/${id}/stop`, { method: 'POST' }, '停止任务失败')
}

export async function prepareCsvDirectory(directoryPath: string): Promise<CsvDirectoryPrepareResponse> {
  return await requestJson('/api/csv-import/prepare', {
    method: 'POST',
    headers,
    body: JSON.stringify({ directoryPath })
  }, '目录检查失败')
}

export async function startCsvDirectoryImport(
  directoryPath: string,
  deploymentArchitecture: DeploymentArchitecture,
  target: CsvImportTarget
): Promise<CsvDirectoryImportResponse> {
  return await requestJson('/api/csv-import/start', {
    method: 'POST',
    headers,
    body: JSON.stringify({ directoryPath, deploymentArchitecture, target })
  }, 'CSV 导入失败')
}

export async function fetchRuntimeLogFiles(): Promise<RuntimeLogFile[]> {
  return await requestJson('/api/runtime-logs/files', undefined, '加载运行日志文件失败')
}

export async function fetchRuntimeLogTail(key: string, lines = 200): Promise<RuntimeLogTail> {
  return await requestJson(`/api/runtime-logs/tail?key=${encodeURIComponent(key)}&lines=${lines}`, undefined, '加载运行日志内容失败')
}
