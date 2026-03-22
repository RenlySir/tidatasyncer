<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import ConnectionProfilePanel from './components/ConnectionProfilePanel.vue'
import CompatibilityReportPanel from './components/CompatibilityReportPanel.vue'
import CsvImportPanel from './components/CsvImportPanel.vue'
import DataSyncTaskPanel from './components/DataSyncTaskPanel.vue'
import RuntimeLogPanel from './components/RuntimeLogPanel.vue'
import SchemaSyncTaskPanel from './components/SchemaSyncTaskPanel.vue'
import SourceGuidePanel from './components/SourceGuidePanel.vue'
import TaskMonitorPanel from './components/TaskMonitorPanel.vue'
import ToolConfigPanel from './components/ToolConfigPanel.vue'
import { useWorkflowState, type ManagementSection } from './composables/useWorkflowState'
import {
  createCompatibilityReport,
  createConnectionProfile,
  createJob,
  createSchemaTask,
  createToolConfig,
  executeCompatibilityReport,
  executeSchemaTask,
  fetchCompatibilityReports,
  fetchConnectionProfiles,
  fetchJobDefinition,
  fetchJobLogs,
  fetchJobs,
  fetchManagedToolPaths,
  fetchOverview,
  fetchSchemaTasks,
  fetchSystemSettings,
  fetchToolConfigs,
  saveSystemSettings,
  startJob,
  stopJob,
  updateCompatibilityReport,
  updateConnectionProfile,
  updateJob,
  updateSchemaTask,
  updateToolConfig
} from './api'
import type {
  CompatibilityReport,
  CompatibilityReportUpsert,
  ConnectionProfile,
  ConnectionProfileUpsert,
  DashboardOverview,
  DeploymentArchitecture,
  ManagedToolPaths,
  SchemaSyncTask,
  SchemaSyncTaskUpsert,
  SyncJob,
  SyncJobDefinition,
  SyncJobLog,
  SystemSettings,
  ToolConfig,
  ToolConfigUpsert
} from './types'

const overview = ref<DashboardOverview | null>(null)
const jobs = ref<SyncJob[]>([])
const activeJob = ref<SyncJob | null>(null)
const activeDefinition = ref<SyncJobDefinition | null>(null)
const logs = ref<SyncJobLog[]>([])
const errorMessage = ref('')
const loading = ref(false)
const activeSection = ref<ManagementSection>('overview')
const deploymentArchitecture = ref<DeploymentArchitecture>('AMD64')
const systemSettings = ref<SystemSettings | null>(null)
const managedToolPaths = ref<ManagedToolPaths>({
  tidbLightningBinary: '',
  dumplingBinary: '',
  sqluldr2Binary: '',
  bcpBinary: '',
  sqlcmdBinary: ''
})
const savingSystemSettings = ref(false)
const settingsMessage = ref('')
const sourceProfiles = ref<ConnectionProfile[]>([])
const targetProfiles = ref<ConnectionProfile[]>([])
const toolConfigs = ref<ToolConfig[]>([])
const schemaTasks = ref<SchemaSyncTask[]>([])
const compatibilityReports = ref<CompatibilityReport[]>([])

let pollTimer: number | null = null
let detailRequestId = 0

const activeJobSummary = computed(() => {
  if (!activeJob.value) {
    return '当前未选中任务'
  }
  return `${activeJob.value.name} · ${activeJob.value.phase} · ${activeJob.value.status}`
})
const {
  navigationItems,
  settingsDirty,
  workflowSteps,
  currentSection,
  nextRecommendedStep,
  workflowHighlights,
  workflowAlerts
} = useWorkflowState({
  activeSection,
  deploymentArchitecture,
  systemSettings,
  sourceProfiles,
  targetProfiles,
  toolConfigs,
  schemaTasks,
  compatibilityReports,
  jobs,
  overview
})

function formatError(error: unknown, fallbackMessage: string): string {
  return error instanceof Error ? error.message : fallbackMessage
}

function openSection(section: ManagementSection | string) {
  const target = workflowSteps.value.find(item => item.key === section)
  if (target?.state === 'locked') {
    errorMessage.value = target.blockedReason || '当前步骤尚未满足前置条件'
    return
  }
  activeSection.value = section as ManagementSection
}

async function loadActiveJobDetails(jobId: number) {
  const requestId = ++detailRequestId
  const [jobLogs, definition] = await Promise.all([fetchJobLogs(jobId), fetchJobDefinition(jobId)])
  if (requestId !== detailRequestId || activeJob.value?.id !== jobId) {
    return
  }
  logs.value = jobLogs
  activeDefinition.value = definition
}

async function loadJobs() {
  if (loading.value) {
    return
  }
  loading.value = true
  try {
    const [jobList, overviewPayload] = await Promise.all([fetchJobs(), fetchOverview()])
    jobs.value = jobList
    overview.value = overviewPayload
    errorMessage.value = ''

    if (!activeJob.value) {
      return
    }

    const refreshedJob = jobList.find(job => job.id === activeJob.value?.id) ?? null
    activeJob.value = refreshedJob
    if (refreshedJob) {
      await loadActiveJobDetails(refreshedJob.id)
    } else {
      activeDefinition.value = null
      logs.value = []
    }
  } catch (error) {
    errorMessage.value = formatError(error, '加载任务失败')
  } finally {
    loading.value = false
  }
}

async function loadManagedToolPaths() {
  try {
    managedToolPaths.value = await fetchManagedToolPaths(deploymentArchitecture.value)
  } catch (error) {
    errorMessage.value = formatError(error, '加载工具默认路径失败')
  }
}

async function loadSystemSettings() {
  try {
    const payload = await fetchSystemSettings()
    systemSettings.value = payload
    deploymentArchitecture.value = payload.deploymentArchitecture
    settingsMessage.value = payload.updatedAt ? `已保存，生效时间 ${new Date(payload.updatedAt).toLocaleString()}` : '当前使用默认配置 AMD64'
  } catch (error) {
    errorMessage.value = formatError(error, '加载系统设置失败')
  }
}

async function loadSetupResources() {
  try {
    const [sources, targets, tools, schema, reports] = await Promise.all([
      fetchConnectionProfiles('SOURCE'),
      fetchConnectionProfiles('TARGET'),
      fetchToolConfigs(),
      fetchSchemaTasks(),
      fetchCompatibilityReports()
    ])
    sourceProfiles.value = sources
    targetProfiles.value = targets
    toolConfigs.value = tools
    schemaTasks.value = schema
    compatibilityReports.value = reports
  } catch (error) {
    errorMessage.value = formatError(error, '加载基础配置失败')
  }
}

async function persistSystemSettings() {
  savingSystemSettings.value = true
  try {
    const payload = await saveSystemSettings(deploymentArchitecture.value)
    systemSettings.value = payload
    settingsMessage.value = `部署架构已保存，生效时间 ${payload.updatedAt ? new Date(payload.updatedAt).toLocaleString() : '刚刚'}`
    await loadManagedToolPaths()
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = formatError(error, '保存系统设置失败')
  } finally {
    savingSystemSettings.value = false
  }
}

async function saveSourceProfile(id: number | null, payload: ConnectionProfileUpsert) {
  try {
    if (id) {
      await updateConnectionProfile(id, payload)
    } else {
      await createConnectionProfile(payload)
    }
    await loadSetupResources()
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = formatError(error, '保存数据源失败')
  }
}

async function saveTargetProfile(id: number | null, payload: ConnectionProfileUpsert) {
  try {
    if (id) {
      await updateConnectionProfile(id, payload)
    } else {
      await createConnectionProfile(payload)
    }
    await loadSetupResources()
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = formatError(error, '保存目标连接失败')
  }
}

async function saveToolConfig(id: number | null, payload: ToolConfigUpsert) {
  try {
    if (id) {
      await updateToolConfig(id, payload)
    } else {
      await createToolConfig(payload)
    }
    await loadSetupResources()
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = formatError(error, '保存工具配置失败')
  }
}

async function saveCompatibilityReport(id: number | null, payload: CompatibilityReportUpsert) {
  try {
    if (id) {
      await updateCompatibilityReport(id, payload)
    } else {
      await createCompatibilityReport(payload)
    }
    await loadSetupResources()
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = formatError(error, '保存兼容性检测任务失败')
  }
}

async function runCompatibilityReport(id: number) {
  try {
    await executeCompatibilityReport(id)
    await loadSetupResources()
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = formatError(error, '执行兼容性检测失败')
  }
}

async function saveSchemaTask(id: number | null, payload: SchemaSyncTaskUpsert) {
  try {
    if (id) {
      await updateSchemaTask(id, payload)
    } else {
      await createSchemaTask(payload)
    }
    await loadSetupResources()
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = formatError(error, '保存表结构任务失败')
  }
}

async function runSchemaTask(id: number) {
  try {
    await executeSchemaTask(id)
    await loadSetupResources()
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = formatError(error, '执行表结构任务失败')
  }
}

async function selectJob(job: SyncJob, section: ManagementSection = 'monitor-center') {
  activeJob.value = job
  activeSection.value = section
  try {
    await loadActiveJobDetails(job.id)
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = formatError(error, '加载任务详情失败')
  }
}

async function saveJob(name: string, definition: SyncJobDefinition) {
  try {
    const definitionWithArchitecture: SyncJobDefinition = {
      ...definition,
      deploymentArchitecture: deploymentArchitecture.value
    }
    const savedJob = activeJob.value
      ? await updateJob(activeJob.value.id, name, definitionWithArchitecture)
      : await createJob(name, definitionWithArchitecture)
    await loadJobs()
    const refreshedJob = jobs.value.find(job => job.id === savedJob.id) ?? savedJob
    activeJob.value = refreshedJob
    await loadActiveJobDetails(savedJob.id)
    activeSection.value = 'monitor-center'
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = formatError(error, '保存任务失败')
  }
}

async function handleStart(id: number) {
  try {
    await startJob(id)
    await loadJobs()
    const job = jobs.value.find(item => item.id === id)
    if (job) {
      await selectJob(job, 'monitor-center')
    }
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = formatError(error, '启动任务失败')
  }
}

async function handleStop(id: number) {
  try {
    await stopJob(id)
    await loadJobs()
    const job = jobs.value.find(item => item.id === id)
    if (job) {
      await selectJob(job, 'monitor-center')
    }
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = formatError(error, '停止任务失败')
  }
}

function resetForm() {
  activeJob.value = null
  activeDefinition.value = null
  logs.value = []
  detailRequestId++
  activeSection.value = 'data-sync'
}

function updateDeploymentArchitecture(architecture: DeploymentArchitecture) {
  deploymentArchitecture.value = architecture
  void loadManagedToolPaths()
}

onMounted(async () => {
  await loadSystemSettings()
  await Promise.all([loadManagedToolPaths(), loadSetupResources(), loadJobs()])
  pollTimer = window.setInterval(() => {
    void loadJobs()
  }, 5000)
})

onUnmounted(() => {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
  }
})
</script>

<template>
  <main class="shell admin-shell">
    <div v-if="errorMessage" class="wide-card error banner-card">
      <span class="metric-label">运行提示</span>
      <p>{{ errorMessage }}</p>
    </div>

    <section class="panel admin-header">
      <div>
        <p class="eyebrow">TiDB Sync Platform</p>
        <h1>异构数据库到 TiDB 的分步式同步平台</h1>
        <p class="hero-copy">先建连接，再做兼容性检查，再配工具、做结构同步，最后创建数据同步任务。把实施顺序和运行监控放在同一套管理后台里，减少误配和返工。</p>
        <div class="workflow-highlight-grid">
          <div v-for="item in workflowHighlights" :key="item.label" class="workflow-highlight-card">
            <span class="metric-label">{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </div>
      <div class="admin-header-meta">
        <div class="metric-card compact-card">
          <span class="metric-label">当前步骤</span>
          <strong>{{ currentSection.step }} · {{ currentSection.title }}</strong>
          <p class="muted">{{ currentSection.description }}</p>
        </div>
        <div class="metric-card compact-card">
          <span class="metric-label">当前架构</span>
          <strong>{{ deploymentArchitecture }}</strong>
          <p class="muted">{{ settingsDirty ? '尚未保存到系统设置' : settingsMessage || '已加载默认配置' }}</p>
        </div>
        <div class="metric-card compact-card">
          <span class="metric-label">任务焦点</span>
          <strong>{{ activeJob ? activeJob.name : '未选中任务' }}</strong>
          <p class="muted">{{ activeJobSummary }}</p>
        </div>
        <div class="metric-card compact-card">
          <span class="metric-label">运行状态</span>
          <strong>{{ overview?.runningJobs ?? 0 }} 个运行中</strong>
          <p class="muted">失败 {{ overview?.failedJobs ?? 0 }} · 已完成 {{ overview?.completedJobs ?? 0 }}</p>
        </div>
      </div>
    </section>

    <section v-if="workflowAlerts.length" class="panel workflow-alert-panel">
      <div class="panel-header">
        <div>
          <p class="eyebrow">Workflow Alerts</p>
          <h2>当前需要关注的事项</h2>
        </div>
      </div>
      <div class="guide-list">
        <div v-for="alert in workflowAlerts" :key="alert" class="log-item">
          <span>{{ alert }}</span>
        </div>
      </div>
    </section>

    <section class="admin-layout">
      <aside class="admin-sidebar">
        <section class="panel nav-card">
          <div class="panel-header">
            <h2>实施步骤</h2>
            <span class="muted">{{ navigationItems.length }} 个入口</span>
          </div>
          <div class="nav-list">
            <button
              v-for="item in workflowSteps"
              :key="item.key"
              class="nav-item step-item"
              :class="{ active: activeSection === item.key, locked: item.state === 'locked', attention: item.state === 'attention', completed: item.state === 'completed' }"
              :disabled="item.state === 'locked'"
              @click="openSection(item.key)"
            >
              <div class="step-row">
                <em>{{ item.step }}</em>
                <span class="step-state" :class="item.state">{{ item.state === 'completed' ? '已完成' : item.state === 'attention' ? '建议处理' : item.state === 'locked' ? '待解锁' : '可执行' }}</span>
              </div>
              <strong>{{ item.title }}</strong>
              <span>{{ item.description }}</span>
              <small class="step-meta">{{ item.countLabel }}</small>
              <small v-if="item.state === 'locked' && item.blockedReason" class="step-blocked">{{ item.blockedReason }}</small>
            </button>
          </div>
          <div class="sub-panel">
            <div class="step-next-card">
              <span class="metric-label">推荐下一步</span>
              <strong>{{ nextRecommendedStep.step }} · {{ nextRecommendedStep.title }}</strong>
              <p class="muted">{{ nextRecommendedStep.description }}</p>
            </div>
            <button class="primary wide-action" @click="openSection(nextRecommendedStep.key)">进入推荐步骤</button>
          </div>
        </section>
      </aside>

      <section class="admin-main">
        <OverviewPanel
          v-if="activeSection === 'overview'"
          :overview="overview"
          :jobs="jobs"
          :deployment-architecture="deploymentArchitecture"
          :saving-settings="savingSystemSettings"
          :settings-dirty="settingsDirty"
          :settings-message="settingsMessage"
          @create-task="openSection('data-sync')"
          @open-section="openSection"
          @select-job="job => selectJob(job, 'monitor-center')"
          @update-architecture="updateDeploymentArchitecture"
          @save-settings="persistSystemSettings"
        />

        <ConnectionProfilePanel
          v-else-if="activeSection === 'connection-profiles'"
          :overview="overview"
          :source-profiles="sourceProfiles"
          :target-profiles="targetProfiles"
          :deployment-architecture="deploymentArchitecture"
          :saving-settings="savingSystemSettings"
          :settings-dirty="settingsDirty"
          :settings-message="settingsMessage"
          @update-architecture="updateDeploymentArchitecture"
          @save-settings="persistSystemSettings"
          @save-source-profile="saveSourceProfile"
          @save-target-profile="saveTargetProfile"
        />

        <CompatibilityReportPanel
          v-else-if="activeSection === 'compatibility-reports'"
          :source-profiles="sourceProfiles"
          :target-profiles="targetProfiles"
          :reports="compatibilityReports"
          @save-report="saveCompatibilityReport"
          @execute-report="runCompatibilityReport"
        />

        <ToolConfigPanel
          v-else-if="activeSection === 'tool-configs'"
          :tool-configs="toolConfigs"
          :managed-tool-paths="managedToolPaths"
          @save-tool-config="saveToolConfig"
        />

        <SchemaSyncTaskPanel
          v-else-if="activeSection === 'schema-sync'"
          :source-profiles="sourceProfiles"
          :target-profiles="targetProfiles"
          :schema-tasks="schemaTasks"
          @save-schema-task="saveSchemaTask"
          @execute-schema-task="runSchemaTask"
        />

        <DataSyncTaskPanel
          v-else-if="activeSection === 'data-sync'"
          :source-profiles="sourceProfiles"
          :target-profiles="targetProfiles"
          :tool-configs="toolConfigs"
          :schema-tasks="schemaTasks"
          :compatibility-reports="compatibilityReports"
          :jobs="jobs"
          :active-job="activeJob"
          :active-definition="activeDefinition"
          :logs="logs"
          :deployment-architecture="deploymentArchitecture"
          :managed-tool-paths="managedToolPaths"
          @save-job="saveJob"
          @select-job="job => selectJob(job, 'monitor-center')"
          @start-job="handleStart"
          @stop-job="handleStop"
          @create-new="resetForm"
        />

        <TaskMonitorPanel
          v-else-if="activeSection === 'monitor-center'"
          :jobs="jobs"
          :active-job="activeJob"
          :active-definition="activeDefinition"
          :logs="logs"
          :schema-tasks="schemaTasks"
          :compatibility-reports="compatibilityReports"
          :source-profiles="sourceProfiles"
          @select-job="job => selectJob(job, 'monitor-center')"
          @start-job="handleStart"
          @stop-job="handleStop"
        />

        <CsvImportPanel
          v-else-if="activeSection === 'csv-import'"
          :deployment-architecture="deploymentArchitecture"
          :default-lightning-binary="managedToolPaths.tidbLightningBinary"
        />

        <SourceGuidePanel v-else-if="activeSection === 'source-guide'" />

        <RuntimeLogPanel v-else-if="activeSection === 'runtime-logs'" />
      </section>
    </section>
  </main>
</template>
