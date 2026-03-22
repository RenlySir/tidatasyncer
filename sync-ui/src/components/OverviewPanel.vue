<script setup lang="ts">
import StatusChip from './StatusChip.vue'
import { sourceCatalog } from '../sourceCatalog'
import type { DashboardOverview, DeploymentArchitecture, SyncJob } from '../types'
import { formatBytes, formatJobPhase, formatJobStatus, formatLag, formatSyncMode } from '../utils/formatters'

defineProps<{
  overview: DashboardOverview | null
  jobs: SyncJob[]
  deploymentArchitecture: DeploymentArchitecture
  savingSettings: boolean
  settingsDirty: boolean
  settingsMessage: string
}>()

const emit = defineEmits<{
  createTask: []
  openSection: [section: string]
  selectJob: [job: SyncJob]
  updateArchitecture: [architecture: DeploymentArchitecture]
  saveSettings: []
}>()

const operationSteps = [
  '第一步完成数据源、TiDB 目标和部署架构设置，建立稳定的数据接入入口。',
  '第二步执行权限检查、兼容性报告和工具目录配置，提前消除实施阻塞点。',
  '第三步完成表结构同步，再创建全量、增量或全量+增量数据同步任务。',
  '第四步进入运行监控，重点关注导出、导入、位点推进、延迟和错误日志。'
]

const permissionWarnings = [
  '源库建议使用独立同步账号，不要直接复用业务超级账号。',
  'MySQL/Oracle 的全量+增量任务会自动记录增量起点，因此账号必须具备读取对应日志位点的权限。',
  'TiDB 目标端账号至少需要建表、DDL 和 DML 权限，供 Lightning 和增量写入使用。'
]

const pipelineStages = [
  {
    title: '采集 Ingest',
    description: '统一承接数据库日志、全量导出文件和目录型 CSV 数据，覆盖批量与实时两种输入形态。'
  },
  {
    title: '转换 Normalize',
    description: '在结构同步、兼容性检测和字段映射阶段完成类型归一、对象识别和语义校正。'
  },
  {
    title: '装载 Deliver',
    description: '全量使用源端工具和 Lightning 装载到 TiDB，增量通过日志 CDC 持续推进。'
  },
  {
    title: '运营 Observe',
    description: '围绕位点、延迟、导出导入进度、错误日志和最近事件构建可运维的运行视图。'
  }
]

const pipelineTraits = [
  '数据全：覆盖全量导出、日志增量和目录型 CSV 接入。',
  '传输快：批量通过源端原生工具与 Lightning，实时通过日志 CDC。',
  '强协同：连接、兼容性、结构、同步、日志在一套工作台里联动。',
  '更敏捷：把前置检测、工具配置和交付路径前移，减少返工。',
  '极稳定：增量位点、错误日志、任务阶段和导入进度都有显式反馈。',
  '易维护：统一工具目录、统一任务模型、统一运行监控。'
]
</script>

<template>
  <section class="content-stack">
    <section class="panel overview-hero">
      <div class="panel-header">
        <div>
          <p class="eyebrow">Step 01</p>
          <h2>数据管道工作台与部署设置</h2>
          <p class="hero-copy">首页不再只看任务数量，而是按数据管道生命周期组织项目：先完成接入与权限，再做兼容性和结构，再进入批量装载与实时 CDC，最后统一在监控中心持续运营。</p>
        </div>
      </div>

      <div class="guide-grid">
        <article class="guide-card">
          <h3>软件操作顺序</h3>
          <div class="guide-list">
            <div v-for="item in operationSteps" :key="item" class="log-item">
              <span>{{ item }}</span>
            </div>
          </div>
        </article>

        <article class="guide-card">
          <h3>权限与稳定性注意事项</h3>
          <div class="guide-list">
            <div v-for="item in permissionWarnings" :key="item" class="log-item">
              <span>{{ item }}</span>
            </div>
          </div>
        </article>
      </div>

      <div class="pipeline-stage-grid">
        <article v-for="stage in pipelineStages" :key="stage.title" class="guide-card pipeline-stage-card">
          <div class="pipeline-stage-head">
            <h3>{{ stage.title }}</h3>
          </div>
          <p class="guide-text">{{ stage.description }}</p>
        </article>
      </div>

      <div class="settings-strip">
        <label>
          <span>部署机器架构</span>
          <select :value="deploymentArchitecture" @change="emit('updateArchitecture', ($event.target as HTMLSelectElement).value as DeploymentArchitecture)">
            <option value="AMD64">AMD64 / x86_64</option>
            <option value="ARM64">ARM64 / aarch64</option>
          </select>
        </label>
        <div class="settings-actions">
          <button class="primary" :disabled="savingSettings" @click="emit('saveSettings')">
            {{ savingSettings ? '保存中...' : '保存部署设置' }}
          </button>
          <span class="muted">{{ settingsDirty ? '当前修改尚未保存' : settingsMessage }}</span>
        </div>
      </div>

      <div class="hero-actions">
        <button class="primary" @click="emit('createTask')">进入任务创建</button>
        <button class="ghost" @click="emit('openSection', 'monitor-center')">查看任务监控</button>
        <button class="ghost" @click="emit('openSection', 'csv-import')">进入文件导入</button>
      </div>
    </section>

    <section class="dashboard-grid" v-if="overview">
      <div class="metric-card">
        <span class="metric-label">管道就绪度</span>
        <strong>{{ overview.pipelineReadinessScore }}%</strong>
      </div>
      <div class="metric-card">
        <span class="metric-label">接入资源</span>
        <strong>{{ overview.sourceProfileCount }} 源 / {{ overview.targetProfileCount }} 目标</strong>
      </div>
      <div class="metric-card">
        <span class="metric-label">批流覆盖</span>
        <strong>{{ overview.batchEnabledJobCount }} 批量 / {{ overview.realtimeEnabledJobCount }} 实时</strong>
      </div>
      <div class="metric-card">
        <span class="metric-label">交付质量门</span>
        <strong>{{ overview.completedCompatibilityReportCount }} 报告 / {{ overview.completedSchemaTaskCount }} 结构完成</strong>
      </div>
    </section>

    <section class="guide-grid" v-if="overview">
      <article class="guide-card">
        <h3>当前管道 readiness</h3>
        <div class="readiness-list">
          <div class="readiness-item">
            <div>
              <strong>数据接入</strong>
              <p class="guide-text">源端与 TiDB 目标已经沉淀为可复用连接画像。</p>
            </div>
            <StatusChip
              :label="`${overview.sourceProfileCount} 源 / ${overview.targetProfileCount} 目标`"
              :tone="overview.sourceProfileCount > 0 && overview.targetProfileCount > 0 ? 'success' : 'warn'"
            />
          </div>
          <div class="readiness-item">
            <div>
              <strong>对象检测</strong>
              <p class="guide-text">兼容性报告用于提前识别对象差异和不兼容类型。</p>
            </div>
            <StatusChip
              :label="`${overview.completedCompatibilityReportCount}/${overview.compatibilityReportCount} 已完成`"
              :tone="overview.completedCompatibilityReportCount > 0 ? 'success' : 'warn'"
            />
          </div>
          <div class="readiness-item">
            <div>
              <strong>结构准备</strong>
              <p class="guide-text">结构同步先处理类型映射，再为后续数据装载扫清障碍。</p>
            </div>
            <StatusChip
              :label="`${overview.completedSchemaTaskCount}/${overview.schemaTaskCount} 已完成`"
              :tone="overview.completedSchemaTaskCount > 0 ? 'success' : 'warn'"
            />
          </div>
          <div class="readiness-item">
            <div>
              <strong>工具交付</strong>
              <p class="guide-text">工具目录与架构绑定后，批量导出和 Lightning 执行路径更加稳定。</p>
            </div>
            <StatusChip
              :label="`${overview.toolConfigCount} 套工具`"
              :tone="overview.toolConfigCount > 0 ? 'success' : 'warn'"
            />
          </div>
        </div>
      </article>

      <article class="guide-card">
        <h3>批流一体与数据交付</h3>
        <div class="guide-list">
          <div class="log-item">
            <span>批量任务：{{ overview.batchEnabledJobCount }} 条，适合初始装载、历史回填和目录型 CSV 导入。</span>
          </div>
          <div class="log-item">
            <span>实时任务：{{ overview.realtimeEnabledJobCount }} 条，适合日志 CDC 常态同步和低延迟交付。</span>
          </div>
          <div class="log-item">
            <span>混合同步：{{ overview.fullAndIncrementalJobCount }} 条，先全量导入，再自动衔接增量位点。</span>
          </div>
          <div class="log-item">
            <span>目录型接入：{{ overview.csvSourceCount }} 个 CSV 源，可作为离线数据管道入口。</span>
          </div>
        </div>
        <div class="tag-list">
          <span v-for="trait in pipelineTraits" :key="trait" class="tag">{{ trait }}</span>
        </div>
      </article>
    </section>

    <section class="dashboard-grid" v-if="overview">
      <div class="metric-card">
        <span class="metric-label">任务总数</span>
        <strong>{{ overview.totalJobs }}</strong>
      </div>
      <div class="metric-card">
        <span class="metric-label">运行中</span>
        <strong>{{ overview.runningJobs }}</strong>
      </div>
      <div class="metric-card">
        <span class="metric-label">失败任务</span>
        <strong>{{ overview.failedJobs }}</strong>
      </div>
      <div class="metric-card">
        <span class="metric-label">已完成</span>
        <strong>{{ overview.completedJobs }}</strong>
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2>平台支持的数据源</h2>
        <span class="muted">优先通过 logo 和工具链快速识别</span>
      </div>
      <div class="source-grid">
        <article v-for="item in sourceCatalog" :key="item.type" class="guide-card source-guide-card">
          <div class="source-card-header">
            <div class="source-logo" :style="{ '--logo-accent': item.accent, '--logo-surface': item.surface }">
              <span>{{ item.logoText }}</span>
            </div>
            <div>
              <h3>{{ item.label }}</h3>
              <small>{{ item.vendor }}</small>
            </div>
          </div>
          <p class="guide-text">{{ item.summary }}</p>
          <div class="tag-list compact-tags">
            <span class="tag">全量：{{ item.fullTool }}</span>
            <span class="tag">增量：{{ item.incrementalTool }}</span>
          </div>
        </article>
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2>最近任务</h2>
        <span class="muted">优先展示最新活跃任务，帮助快速判断数据管道是否健康</span>
      </div>
      <div class="table" v-if="overview?.recentJobs?.length">
        <div class="table-head overview-table">
          <span>任务</span>
          <span>模式</span>
          <span>当前阶段</span>
          <span>批量进度</span>
          <span>实时状态</span>
          <span>操作</span>
        </div>
        <div
          v-for="job in overview.recentJobs.slice(0, 6)"
          :key="job.id"
          class="table-row overview-table"
          @click="emit('selectJob', job)"
        >
          <span class="stack-cell">
            <strong>{{ job.name }}</strong>
            <small>{{ formatJobStatus(job.status) }}</small>
          </span>
          <span>{{ formatSyncMode(job.syncMode) }}</span>
          <span>{{ formatJobPhase(job.phase) }}</span>
          <span class="stack-cell">
            <strong>{{ job.exportedTableCount ?? 0 }}/{{ job.totalTableCount ?? 0 }} 表</strong>
            <small>{{ formatBytes(job.exportedBytes) }} 导出 / {{ formatBytes(job.importedBytes) }} 导入</small>
          </span>
          <span class="stack-cell">
            <strong>{{ job.latestLogPosition || '-' }}</strong>
            <small>延迟 {{ formatLag(job.lastLagMillis) }}</small>
          </span>
          <span class="actions">
            <button class="ghost" @click.stop="emit('selectJob', job)">查看详情</button>
          </span>
        </div>
      </div>
      <div v-else class="empty-state">
        <strong>当前还没有同步任务</strong>
        <p>完成首页设置后，可以进入“创建同步任务”开始配置第一条任务。</p>
      </div>
    </section>
  </section>
</template>
