import { computed, type Ref } from 'vue'
import type {
  CompatibilityReport,
  ConnectionProfile,
  DashboardOverview,
  DeploymentArchitecture,
  SchemaSyncTask,
  SyncJob,
  SystemSettings,
  ToolConfig
} from '../types'

export type ManagementSection =
  | 'overview'
  | 'connection-profiles'
  | 'compatibility-reports'
  | 'tool-configs'
  | 'schema-sync'
  | 'data-sync'
  | 'monitor-center'
  | 'csv-import'
  | 'source-guide'
  | 'runtime-logs'

export type StepState = 'ready' | 'completed' | 'attention' | 'locked'

export type WorkflowStep = {
  key: ManagementSection
  step: string
  title: string
  description: string
  state: StepState
  countLabel: string
  blockedReason: string
}

type WorkflowInput = {
  activeSection: Ref<ManagementSection>
  deploymentArchitecture: Ref<DeploymentArchitecture>
  systemSettings: Ref<SystemSettings | null>
  sourceProfiles: Ref<ConnectionProfile[]>
  targetProfiles: Ref<ConnectionProfile[]>
  toolConfigs: Ref<ToolConfig[]>
  schemaTasks: Ref<SchemaSyncTask[]>
  compatibilityReports: Ref<CompatibilityReport[]>
  jobs: Ref<SyncJob[]>
  overview: Ref<DashboardOverview | null>
}

const navigationItems: Omit<WorkflowStep, 'state' | 'countLabel' | 'blockedReason'>[] = [
  { key: 'overview', step: '00', title: '平台总览', description: '先从工作台总览当前接入、结构、同步和运行状态，再进入具体实施步骤' },
  { key: 'connection-profiles', step: '01', title: '数据源与目标', description: '先创建数据源和 TiDB 目标，并保存部署机器架构' },
  { key: 'compatibility-reports', step: '02', title: '兼容性检测报告', description: '先检查源端对象与 TiDB 的兼容性，再决定结构同步和数据同步策略' },
  { key: 'tool-configs', step: '03', title: '工具目录配置', description: '按源端类型配置 sqluldr2、dumpling、lightning 等工具路径' },
  { key: 'schema-sync', step: '04', title: '表结构同步任务', description: '先生成和校验结构，再处理不兼容字段类型' },
  { key: 'data-sync', step: '05', title: '数据同步任务', description: '按全量、增量、全量+增量模式创建并保存任务' },
  { key: 'monitor-center', step: '06', title: '运行监控中心', description: '查看全量阶段、导入进度、位点推进和错误信息' },
  { key: 'csv-import', step: '07', title: 'CSV 导入管理', description: '对现成 CSV 目录做预处理并调用 Lightning 导入' },
  { key: 'source-guide', step: '08', title: '数据源说明中心', description: '查看各数据源工具链、权限要求和 logo 识别' },
  { key: 'runtime-logs', step: '09', title: '运行日志中心', description: '查看系统运行日志和排障日志' }
]

export function useWorkflowState(input: WorkflowInput) {
  const settingsDirty = computed(() => input.deploymentArchitecture.value !== input.systemSettings.value?.deploymentArchitecture)
  const hasSavedArchitecture = computed(() => !settingsDirty.value)
  const hasSourceProfiles = computed(() => input.sourceProfiles.value.length > 0)
  const hasTargetProfiles = computed(() => input.targetProfiles.value.length > 0)
  const hasConnectionSetup = computed(() => hasSavedArchitecture.value && hasSourceProfiles.value && hasTargetProfiles.value)
  const hasCompatibilityReport = computed(() => input.compatibilityReports.value.some(report => report.status === 'COMPLETED'))
  const hasToolConfigs = computed(() => input.toolConfigs.value.length > 0)
  const hasSchemaTask = computed(() => input.schemaTasks.value.length > 0)
  const hasCompletedSchemaTask = computed(() => input.schemaTasks.value.some(task => task.status === 'COMPLETED'))
  const hasSyncJobs = computed(() => input.jobs.value.length > 0)
  const hasAnyOperationalTask = computed(() => hasSyncJobs.value || hasSchemaTask.value || input.compatibilityReports.value.length > 0)

  const workflowSteps = computed<WorkflowStep[]>(() => navigationItems.map(item => {
    switch (item.key) {
      case 'overview':
        return {
          ...item,
          state: 'ready',
          countLabel: `${input.overview.value?.pipelineReadinessScore ?? 0}% 就绪`,
          blockedReason: ''
        }
      case 'connection-profiles':
        return {
          ...item,
          state: hasConnectionSetup.value ? 'completed' : 'ready',
          countLabel: `${input.sourceProfiles.value.length} 源 / ${input.targetProfiles.value.length} 目标`,
          blockedReason: ''
        }
      case 'compatibility-reports':
        return {
          ...item,
          state: !hasConnectionSetup.value ? 'locked' : hasCompatibilityReport.value ? 'completed' : 'attention',
          countLabel: `${input.compatibilityReports.value.length} 份报告`,
          blockedReason: !hasConnectionSetup.value ? '请先完成数据源、目标和部署架构配置' : ''
        }
      case 'tool-configs':
        return {
          ...item,
          state: !hasSavedArchitecture.value ? 'locked' : hasToolConfigs.value ? 'completed' : 'ready',
          countLabel: `${input.toolConfigs.value.length} 套工具配置`,
          blockedReason: !hasSavedArchitecture.value ? '请先在第 1 步保存部署架构' : ''
        }
      case 'schema-sync':
        return {
          ...item,
          state: !hasConnectionSetup.value ? 'locked' : hasCompletedSchemaTask.value ? 'completed' : hasSchemaTask.value ? 'attention' : 'ready',
          countLabel: `${input.schemaTasks.value.length} 条结构任务`,
          blockedReason: !hasConnectionSetup.value ? '请先完成数据源和目标连接配置' : ''
        }
      case 'data-sync':
        return {
          ...item,
          state: !hasConnectionSetup.value || !hasToolConfigs.value
            ? 'locked'
            : hasSyncJobs.value
              ? 'completed'
              : !hasCompatibilityReport.value || !hasCompletedSchemaTask.value
                ? 'attention'
                : 'ready',
          countLabel: `${input.jobs.value.length} 条同步任务`,
          blockedReason: !hasConnectionSetup.value
            ? '请先完成数据源和目标连接配置'
            : !hasToolConfigs.value
              ? '请先完成工具目录配置'
              : ''
        }
      case 'monitor-center':
        return {
          ...item,
          state: hasAnyOperationalTask.value ? 'ready' : 'locked',
          countLabel: `${input.jobs.value.length} 同步 / ${input.schemaTasks.value.length} 结构 / ${input.compatibilityReports.value.length} 检测`,
          blockedReason: hasAnyOperationalTask.value ? '' : '请先创建兼容性、结构或同步任务'
        }
      case 'csv-import':
        return {
          ...item,
          state: hasSavedArchitecture.value ? 'ready' : 'locked',
          countLabel: '独立导入',
          blockedReason: hasSavedArchitecture.value ? '' : '请先保存部署架构'
        }
      case 'source-guide':
        return { ...item, state: 'ready', countLabel: '能力说明', blockedReason: '' }
      case 'runtime-logs':
        return { ...item, state: 'ready', countLabel: '排障日志', blockedReason: '' }
    }
  }))

  const currentSection = computed(() => workflowSteps.value.find(item => item.key === input.activeSection.value) ?? workflowSteps.value[0])
  const completedWorkflowCount = computed(() => workflowSteps.value.filter(item => item.state === 'completed').length)
  const nextRecommendedStep = computed(() => workflowSteps.value.find(item => item.state === 'ready' || item.state === 'attention') ?? workflowSteps.value[0])
  const workflowHighlights = computed(() => [
    { label: '流程完成度', value: `${completedWorkflowCount.value}/${navigationItems.length}` },
    { label: '管道就绪度', value: `${input.overview.value?.pipelineReadinessScore ?? 0}%` },
    { label: '推荐下一步', value: `${nextRecommendedStep.value.step} ${nextRecommendedStep.value.title}` },
    {
      label: '批流覆盖',
      value: `${input.overview.value?.batchEnabledJobCount ?? 0} 批量 / ${input.overview.value?.realtimeEnabledJobCount ?? 0} 实时`
    }
  ])

  const workflowAlerts = computed(() => {
    const alerts: string[] = []
    if (!hasSavedArchitecture.value) {
      alerts.push('部署机器架构还没有保存，工具路径和运行环境选择可能不准确。')
    }
    if (!hasSourceProfiles.value || !hasTargetProfiles.value) {
      alerts.push('请先至少创建 1 个数据源和 1 个 TiDB 目标。')
    }
    if (hasConnectionSetup.value && !hasCompatibilityReport.value) {
      alerts.push('建议先执行兼容性检测报告，再进入结构同步和数据同步配置。')
    }
    if (hasConnectionSetup.value && !hasToolConfigs.value) {
      alerts.push('还没有工具目录配置，后续全量导出和 Lightning 导入无法按部署机路径执行。')
    }
    if (hasToolConfigs.value && !hasCompletedSchemaTask.value) {
      alerts.push('还没有完成态表结构同步任务，如目标端未建表，建议先完成结构同步。')
    }
    return alerts
  })

  return {
    navigationItems,
    settingsDirty,
    workflowSteps,
    currentSection,
    nextRecommendedStep,
    workflowHighlights,
    workflowAlerts
  }
}
