<script setup lang="ts">
import { computed, ref, watch, type Ref } from 'vue'
import JobDetail from './JobDetail.vue'
import JobForm from './JobForm.vue'
import JobTable from './JobTable.vue'
import StatusChip from './StatusChip.vue'
import { sourceCatalogMap } from '../sourceCatalog'
import type {
  CompatibilityReport,
  ConnectionProfile,
  DeploymentArchitecture,
  ManagedToolPaths,
  SchemaSyncTask,
  SyncJob,
  SyncJobDefinition,
  SyncJobLog,
  TableMapping,
  ToolConfig
} from '../types'

const props = defineProps<{
  sourceProfiles: ConnectionProfile[]
  targetProfiles: ConnectionProfile[]
  toolConfigs: ToolConfig[]
  schemaTasks: SchemaSyncTask[]
  compatibilityReports: CompatibilityReport[]
  jobs: SyncJob[]
  activeJob: SyncJob | null
  activeDefinition: SyncJobDefinition | null
  logs: SyncJobLog[]
  deploymentArchitecture: DeploymentArchitecture
  managedToolPaths: ManagedToolPaths
}>()

const emit = defineEmits<{
  saveJob: [name: string, definition: SyncJobDefinition]
  selectJob: [job: SyncJob]
  startJob: [id: number]
  stopJob: [id: number]
  createNew: []
}>()

const sourceProfileId = ref<number | null>(null)
const targetProfileId = ref<number | null>(null)
const toolConfigId = ref<number | null>(null)
const schemaTaskId = ref<number | null>(null)

function bindDefaultSelection<T extends { id: number }>(items: () => T[], target: Ref<number | null>) {
  watch(items, value => {
    if (!value.length) {
      target.value = null
      return
    }
    if (target.value === null || !value.some(item => item.id === target.value)) {
      target.value = value[0].id
    }
  }, { immediate: true })
}

bindDefaultSelection(() => props.sourceProfiles, sourceProfileId)
bindDefaultSelection(() => props.targetProfiles, targetProfileId)

const selectedSource = computed(() => props.sourceProfiles.find(item => item.id === sourceProfileId.value) ?? null)
const selectedTarget = computed(() => props.targetProfiles.find(item => item.id === targetProfileId.value) ?? null)
const availableToolConfigs = computed(() => {
  const source = selectedSource.value
  if (!source) {
    return props.toolConfigs
  }
  const matched = props.toolConfigs.filter(item =>
    item.databaseType === source.databaseType || item.databaseType === 'TIDB'
  )
  return matched.length ? matched : props.toolConfigs
})
const availableSchemaTasks = computed(() => {
  const source = selectedSource.value
  const target = selectedTarget.value
  if (!source || !target) {
    return props.schemaTasks
  }
  return props.schemaTasks.filter(task => task.sourceProfileId === source.id && task.targetProfileId === target.id)
})
const matchingCompatibilityReports = computed(() => {
  const source = selectedSource.value
  const target = selectedTarget.value
  if (!source || !target) {
    return []
  }
  return props.compatibilityReports.filter(report => report.sourceProfileId === source.id && report.targetProfileId === target.id)
})
const selectedToolConfig = computed(() => availableToolConfigs.value.find(item => item.id === toolConfigId.value) ?? null)
const selectedSchemaTask = computed(() => availableSchemaTasks.value.find(item => item.id === schemaTaskId.value) ?? null)
const selectedSourceMeta = computed(() => {
  const source = selectedSource.value
  return source && source.databaseType !== 'TIDB' ? sourceCatalogMap[source.databaseType] : null
})

bindDefaultSelection(() => availableToolConfigs.value, toolConfigId)
bindDefaultSelection(() => availableSchemaTasks.value, schemaTaskId)
const completedCompatibilityReports = computed(() => matchingCompatibilityReports.value.filter(item => item.status === 'COMPLETED'))
const completedSchemaTasks = computed(() => availableSchemaTasks.value.filter(item => item.status === 'COMPLETED'))
const recommendedSchemaTask = computed(() => completedSchemaTasks.value[0] ?? availableSchemaTasks.value[0] ?? null)
const resourceFitNotes = computed(() => [
  {
    label: '匹配的工具配置',
    value: `${availableToolConfigs.value.length} 套`,
    detail: selectedSource.value ? `已按 ${selectedSource.value.databaseType} 自动过滤。` : '选择数据源后自动过滤。'
  },
  {
    label: '匹配的结构任务',
    value: `${availableSchemaTasks.value.length} 条`,
    detail: selectedSource.value && selectedTarget.value ? '只展示当前源端和目标端组合下的结构任务。' : '选择源端和目标端后自动过滤。'
  },
  {
    label: '匹配的兼容性报告',
    value: `${completedCompatibilityReports.value.length} 份完成`,
    detail: selectedSource.value && selectedTarget.value ? '按当前源端和目标端组合统计完成态兼容性报告。' : '选择源端和目标端后自动统计。'
  }
])

const preflightChecklist = computed(() => [
  {
    label: '数据源与目标连接',
    ready: Boolean(selectedSource.value && selectedTarget.value),
    detail: selectedSource.value && selectedTarget.value ? '已选择数据源和目标端。' : '请先选择一套源端和 TiDB 目标。'
  },
  {
    label: '工具目录配置',
    ready: Boolean(selectedToolConfig.value) || selectedSource.value?.databaseType === 'CSV',
    detail: selectedToolConfig.value
      ? '全量导出工具和 Lightning 路径已按当前数据源自动带入。'
      : selectedSource.value?.databaseType === 'CSV'
        ? 'CSV 源可直接复用平台默认 Lightning 路径。'
        : '缺少与当前数据源匹配的工具配置，无法执行全量任务。'
  },
  {
    label: '兼容性检测报告',
    ready: completedCompatibilityReports.value.length > 0,
    detail: completedCompatibilityReports.value.length > 0
      ? `当前源端和目标端组合已有 ${completedCompatibilityReports.value.length} 份完成态兼容性报告。`
      : '建议先针对当前源端和目标端组合完成兼容性检测，降低运行中报错概率。'
  },
  {
    label: '表结构准备',
    ready: completedSchemaTasks.value.length > 0,
    detail: completedSchemaTasks.value.length > 0
      ? `当前源端和目标端组合已有 ${completedSchemaTasks.value.length} 条完成态结构任务。`
      : '若目标端未建表，建议先针对当前源端和目标端组合完成结构同步。'
  }
])

const selectionSummary = computed(() => [
  { label: '数据源', value: selectedSource.value ? `${selectedSource.value.name} / ${selectedSource.value.databaseType}` : '未选择' },
  { label: '目标端', value: selectedTarget.value ? `${selectedTarget.value.name} / ${selectedTarget.value.databaseName || 'TiDB'}` : '未选择' },
  { label: '工具配置', value: selectedToolConfig.value ? `${selectedToolConfig.value.name} / ${selectedToolConfig.value.databaseType}` : '未选择' },
  { label: '结构任务', value: selectedSchemaTask.value ? `${selectedSchemaTask.value.name} / ${selectedSchemaTask.value.status}` : recommendedSchemaTask.value ? `推荐 ${recommendedSchemaTask.value.name}` : '未绑定结构任务' },
  { label: '兼容性报告', value: completedCompatibilityReports.value.length ? `${completedCompatibilityReports.value.length} 份已完成` : '当前组合暂无完成态报告' }
])

function buildMappings(task: SchemaSyncTask | null, source: ConnectionProfile | null, target: ConnectionProfile | null): TableMapping[] {
  if (!task || task.tableSelectionMode === 'DATABASE_ALL') {
    return []
  }
  return task.selectedTables.map(tableName => {
    const segments = tableName.split('.')
    const sourceTable = segments[segments.length - 1]
    return {
      sourceCatalog: source?.databaseName || '',
      sourceSchema: source?.schemaName || source?.databaseName || '',
      sourceTable,
      targetDatabase: target?.databaseName || '',
      targetTable: sourceTable,
      primaryKeys: [],
      incrementalColumn: '',
      includedColumns: [],
      columnMappings: {}
    }
  })
}

const templateDefinition = computed<SyncJobDefinition>(() => {
  const source = selectedSource.value
  const target = selectedTarget.value
  const tool = selectedToolConfig.value
  const schemaTask = selectedSchemaTask.value
  const isCsv = source?.databaseType === 'CSV'

  return {
    jobId: props.activeDefinition?.jobId,
    jobName: props.activeDefinition?.jobName,
    syncMode: isCsv ? 'FULL_ONLY' : 'FULL_AND_INCREMENTAL',
    deploymentArchitecture: props.deploymentArchitecture,
    source: {
      databaseType: (source?.databaseType === 'TIDB' ? 'MYSQL' : (source?.databaseType || 'MYSQL')) as SyncJobDefinition['source']['databaseType'],
      host: source?.host || '',
      port: source?.port || 0,
      databaseName: source?.databaseName || '',
      schemaName: source?.schemaName || '',
      username: source?.username || '',
      password: source?.password || '',
      jdbcUrl: source?.jdbcUrl || '',
      jdbcParameters: source?.jdbcParameters || '',
      commandTemplate: ''
    },
    target: {
      host: target?.host || '127.0.0.1',
      port: target?.port || 4000,
      databaseName: target?.databaseName || '',
      username: target?.username || 'root',
      password: target?.password || '',
      jdbcUrl: target?.jdbcUrl || '',
      jdbcParameters: target?.jdbcParameters || 'useUnicode=true&characterEncoding=utf8&rewriteBatchedStatements=true',
      lightningBinary: tool?.lightningBinary || props.managedToolPaths.tidbLightningBinary,
      statusPort: target?.tidbStatusPort || 10080
    },
    tableMappings: buildMappings(schemaTask, source, target),
    fullLoad: {
      exportToolBinary: tool?.exportToolBinary || '',
      exportBaseDir: source?.databaseType === 'CSV' ? (source.csvDirectory || './work/import') : './work/export',
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
        postgresPluginName: 'pgoutput',
        postgresPublicationAutoCreateMode: 'all_tables'
      }
    }
  }
})

const effectiveDefinition = computed(() => props.activeJob ? props.activeDefinition : templateDefinition.value)

function handleSave(name: string, definition: SyncJobDefinition) {
  emit('saveJob', name, definition)
}

function handleSchemaTaskChange(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  schemaTaskId.value = value ? Number(value) : null
}
</script>

<template>
  <section class="content-stack">
    <section class="panel">
      <div class="panel-header">
        <div>
          <p class="eyebrow">Step 05</p>
          <h2>创建数据同步任务</h2>
          <p class="guide-text">先从前面几个步骤里选择现成的资源，再配置任务模式。这样可以把连接参数、工具路径、结构准备和增量策略统一带入，避免重复填写。</p>
        </div>
        <button class="ghost" @click="emit('createNew')">新建数据任务</button>
      </div>

      <div class="selection-hero-grid">
        <article class="guide-card selection-hero-card">
          <div class="source-card-header">
            <div
              v-if="selectedSourceMeta"
              class="source-logo"
              :style="{ '--logo-accent': selectedSourceMeta.accent, '--logo-surface': selectedSourceMeta.surface }"
            >
              <span>{{ selectedSourceMeta.logoText }}</span>
            </div>
            <div>
              <span class="metric-label">当前源端</span>
              <strong>{{ selectedSource?.name || '未选择数据源' }}</strong>
              <p class="muted">{{ selectedSourceMeta?.summary || '请选择一个已创建的数据源。' }}</p>
            </div>
          </div>
        </article>
        <article class="guide-card selection-hero-card">
          <span class="metric-label">任务前置检查</span>
          <div class="readiness-list">
            <div v-for="item in preflightChecklist" :key="item.label" class="readiness-item">
              <div>
                <strong>{{ item.label }}</strong>
                <p class="muted">{{ item.detail }}</p>
              </div>
              <StatusChip :label="item.ready ? '已就绪' : '待完善'" :tone="item.ready ? 'success' : 'warn'" />
            </div>
          </div>
        </article>
      </div>

      <div class="form-grid profile-form">
        <label>
          <span>数据源</span>
          <select v-model.number="sourceProfileId">
            <option v-for="profile in sourceProfiles" :key="profile.id" :value="profile.id">{{ profile.name }} / {{ profile.databaseType }}</option>
          </select>
        </label>
        <label>
          <span>目标端</span>
          <select v-model.number="targetProfileId">
            <option v-for="profile in targetProfiles" :key="profile.id" :value="profile.id">{{ profile.name }} / {{ profile.databaseName }}</option>
          </select>
        </label>
        <label>
          <span>工具配置</span>
          <select v-model.number="toolConfigId">
            <option v-for="config in availableToolConfigs" :key="config.id" :value="config.id">{{ config.name }} / {{ config.databaseType }}</option>
          </select>
        </label>
        <label>
          <span>结构任务（可选）</span>
          <select :value="schemaTaskId ?? ''" @change="handleSchemaTaskChange">
            <option value="">不绑定结构任务</option>
            <option v-for="task in availableSchemaTasks" :key="task.id" :value="task.id">{{ task.name }} / {{ task.status }}</option>
          </select>
        </label>
      </div>

      <div class="guide-grid">
        <article class="guide-card">
          <h3>资源带入结果</h3>
          <div class="guide-list">
            <div v-for="item in selectionSummary" :key="item.label" class="log-item">
              <strong>{{ item.label }}</strong>
              <span>{{ item.value }}</span>
            </div>
          </div>
        </article>
        <article class="guide-card">
          <h3>平台自动处理内容</h3>
          <div class="guide-list">
            <div class="log-item"><span>连接参数来自第 1 步创建的数据源和目标端。</span></div>
            <div class="log-item"><span>全量工具路径与 Lightning 路径来自第 2 步工具配置。</span></div>
            <div class="log-item"><span>若绑定结构任务，指定表范围会自动转成表映射草稿。</span></div>
            <div class="log-item"><span>CSV 数据源会自动切换为仅全量模式。</span></div>
          </div>
        </article>
        <article class="guide-card">
          <h3>当前上下文匹配结果</h3>
          <div class="guide-list">
            <div v-for="item in resourceFitNotes" :key="item.label" class="log-item">
              <strong>{{ item.label }}</strong>
              <span>{{ item.value }} · {{ item.detail }}</span>
            </div>
          </div>
        </article>
      </div>
    </section>

    <JobForm
      :job="activeJob"
      :definition="effectiveDefinition || null"
      :default-lightning-binary="managedToolPaths.tidbLightningBinary"
      :deployment-architecture="deploymentArchitecture"
      @save="handleSave"
    />

    <section class="section-grid narrow-side">
      <JobTable
        :jobs="jobs"
        :active-job-id="activeJob?.id || null"
        @select="emit('selectJob', $event)"
        @start="emit('startJob', $event)"
        @stop="emit('stopJob', $event)"
      />
      <JobDetail
        :job="activeJob"
        :logs="logs"
      />
    </section>
  </section>
</template>
