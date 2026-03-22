<script setup lang="ts">
import { computed, ref } from 'vue'
import JobDetail from './JobDetail.vue'
import JobTable from './JobTable.vue'
import StatusChip from './StatusChip.vue'
import { sourceCatalogMap } from '../sourceCatalog'
import type { CompatibilityReport, ConnectionProfile, SchemaSyncTask, SyncJob, SyncJobDefinition, SyncJobLog } from '../types'
import { formatDateTime, statusTone } from '../utils/formatters'

const props = defineProps<{
  jobs: SyncJob[]
  activeJob: SyncJob | null
  activeDefinition: SyncJobDefinition | null
  logs: SyncJobLog[]
  schemaTasks: SchemaSyncTask[]
  compatibilityReports: CompatibilityReport[]
  sourceProfiles: ConnectionProfile[]
}>()

const emit = defineEmits<{
  selectJob: [job: SyncJob]
  startJob: [id: number]
  stopJob: [id: number]
}>()

const monitorCategory = ref<'data-sync' | 'schema-sync' | 'compatibility'>('data-sync')
const selectedSchemaTaskId = ref<number | null>(null)
const selectedCompatibilityReportId = ref<number | null>(null)

const activeSchemaTask = computed(() => props.schemaTasks.find(item => item.id === selectedSchemaTaskId.value) ?? null)
const activeCompatibilityReport = computed(() => props.compatibilityReports.find(item => item.id === selectedCompatibilityReportId.value) ?? null)

function compatibilitySourceMeta(profileId: number) {
  return props.sourceProfiles.find(item => item.id === profileId) ?? null
}

const monitorStats = computed(() => [
  { key: 'data-sync', label: '数据同步任务', count: props.jobs.length },
  { key: 'schema-sync', label: '表结构迁移任务', count: props.schemaTasks.length },
  { key: 'compatibility', label: '兼容性对比任务', count: props.compatibilityReports.length }
])
</script>

<template>
  <section class="content-stack">
    <section class="panel">
      <div class="panel-header">
        <div>
          <p class="eyebrow">Step 06</p>
          <h2>统一任务监控中心</h2>
          <p class="guide-text">按任务类型统一查看兼容性对比、表结构迁移和数据同步任务的运行情况。点击不同分类，可快速切换到对应任务视图。</p>
        </div>
      </div>

      <div class="monitor-switch-grid">
        <button
          v-for="item in monitorStats"
          :key="item.key"
          class="mode-card"
          :class="{ active: monitorCategory === item.key }"
          @click="monitorCategory = item.key as typeof monitorCategory.value"
        >
          <strong>{{ item.label }}</strong>
          <span>{{ item.count }} 条</span>
        </button>
      </div>
    </section>

    <section v-if="monitorCategory === 'data-sync'" class="section-grid narrow-side">
      <JobTable
        :jobs="jobs"
        :active-job-id="activeJob?.id ?? null"
        @select="emit('selectJob', $event)"
        @start="emit('startJob', $event)"
        @stop="emit('stopJob', $event)"
      />
      <JobDetail :job="activeJob" :logs="logs" />
    </section>

    <section v-else-if="monitorCategory === 'schema-sync'" class="section-grid narrow-side">
      <section class="panel">
        <div class="panel-header">
          <h2>表结构迁移任务</h2>
          <span class="muted">{{ schemaTasks.length }} 条</span>
        </div>
        <div class="table" v-if="schemaTasks.length">
          <div class="table-head schema-task-monitor-table">
            <span>任务</span>
            <span>范围</span>
            <span>状态</span>
            <span>执行时间</span>
            <span>产物</span>
          </div>
          <div
            v-for="task in schemaTasks"
            :key="task.id"
            class="table-row schema-task-monitor-table"
            :class="{ active: activeSchemaTask?.id === task.id }"
            @click="selectedSchemaTaskId = task.id"
          >
            <span class="stack-cell">
              <strong>{{ task.name }}</strong>
              <small>{{ task.lastMessage || '暂无执行消息' }}</small>
            </span>
            <span>{{ task.tableSelectionMode === 'DATABASE_ALL' ? '整库' : `指定表 ${task.selectedTables.length} 张` }}</span>
            <span>
              <StatusChip :label="task.status" :tone="statusTone(task.status)" />
            </span>
            <span>{{ formatDateTime(task.executedAt || task.updatedAt) }}</span>
            <span class="stack-cell">
              <small>{{ task.generatedDdlPath || '未生成 DDL' }}</small>
              <small>{{ task.unsupportedItemsPath || '无不兼容文件' }}</small>
            </span>
          </div>
        </div>
        <div v-else class="empty-state">
          <strong>暂无表结构迁移任务</strong>
          <p>先在“表结构同步任务”中创建并执行任务。</p>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <h2>结构任务详情</h2>
          <span class="muted">{{ activeSchemaTask ? activeSchemaTask.status : '未选择任务' }}</span>
        </div>
        <div v-if="activeSchemaTask" class="detail-stack">
          <div class="guide-card">
            <strong>{{ activeSchemaTask.name }}</strong>
            <p class="guide-text">{{ activeSchemaTask.lastMessage || '暂无执行说明' }}</p>
            <div class="tag-list">
              <span class="tag">DDL 文件：{{ activeSchemaTask.generatedDdlPath || '-' }}</span>
              <span class="tag">不兼容项：{{ activeSchemaTask.unsupportedItems.length }}</span>
            </div>
          </div>
          <div class="runtime-log-viewer">
            <pre><code>{{ activeSchemaTask.generatedDdl || '尚未生成 DDL' }}</code></pre>
          </div>
        </div>
        <div v-else class="empty-state">
          <strong>未选择结构任务</strong>
          <p>点击左侧任务查看生成 DDL 和不兼容项情况。</p>
        </div>
      </section>
    </section>

    <section v-else class="section-grid narrow-side">
      <section class="panel">
        <div class="panel-header">
          <h2>兼容性对比任务</h2>
          <span class="muted">{{ compatibilityReports.length }} 条</span>
        </div>
        <div class="table" v-if="compatibilityReports.length">
          <div class="table-head compatibility-monitor-table">
            <span>任务</span>
            <span>源端</span>
            <span>状态</span>
            <span>执行时间</span>
            <span>问题摘要</span>
          </div>
          <div
            v-for="report in compatibilityReports"
            :key="report.id"
            class="table-row compatibility-monitor-table"
            :class="{ active: activeCompatibilityReport?.id === report.id }"
            @click="selectedCompatibilityReportId = report.id"
          >
            <span class="stack-cell">
              <strong>{{ report.name }}</strong>
              <small>{{ report.lastMessage || '暂无执行说明' }}</small>
            </span>
            <span class="source-card-header compact-source-header">
              <div
                class="source-logo mini-logo"
                :style="{
                  '--logo-accent': sourceCatalogMap[(compatibilitySourceMeta(report.sourceProfileId)?.databaseType || 'MYSQL') as keyof typeof sourceCatalogMap]?.accent || '#0fa76f',
                  '--logo-surface': sourceCatalogMap[(compatibilitySourceMeta(report.sourceProfileId)?.databaseType || 'MYSQL') as keyof typeof sourceCatalogMap]?.surface || 'rgba(15,167,111,0.14)'
                }"
              >
                <span>{{ sourceCatalogMap[(compatibilitySourceMeta(report.sourceProfileId)?.databaseType || 'MYSQL') as keyof typeof sourceCatalogMap]?.logoText || 'DB' }}</span>
              </div>
              <span>{{ compatibilitySourceMeta(report.sourceProfileId)?.name || `#${report.sourceProfileId}` }}</span>
            </span>
            <span><StatusChip :label="report.status" :tone="statusTone(report.status)" /></span>
            <span>{{ formatDateTime(report.executedAt || report.updatedAt) }}</span>
            <span class="stack-cell">
              <small>总问题 {{ report.summary.totalFindings }}</small>
              <small>不兼容 {{ report.summary.incompatibleCount }} / 需评估 {{ report.summary.partialCount }}</small>
            </span>
          </div>
        </div>
        <div v-else class="empty-state">
          <strong>暂无兼容性对比任务</strong>
          <p>先在“兼容性检测报告”中创建并执行任务。</p>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <h2>兼容性报告详情</h2>
          <span class="muted">{{ activeCompatibilityReport ? activeCompatibilityReport.status : '未选择任务' }}</span>
        </div>
        <div v-if="activeCompatibilityReport" class="detail-stack">
          <div class="guide-card">
            <strong>{{ activeCompatibilityReport.name }}</strong>
            <p class="guide-text">{{ activeCompatibilityReport.lastMessage || '暂无执行说明' }}</p>
            <div class="tag-list">
              <span class="tag">Markdown：{{ activeCompatibilityReport.reportPath || '-' }}</span>
              <span class="tag">HTML：{{ activeCompatibilityReport.reportHtmlPath || '-' }}</span>
            </div>
          </div>
          <iframe
            v-if="activeCompatibilityReport.reportHtml"
            class="html-report-preview-frame"
            :srcdoc="activeCompatibilityReport.reportHtml"
          ></iframe>
          <div v-else class="empty-state compact">
            <strong>尚未生成 HTML 报告</strong>
            <p>执行兼容性任务后，这里会展示 HTML 报告预览。</p>
          </div>
        </div>
        <div v-else class="empty-state">
          <strong>未选择兼容性任务</strong>
          <p>点击左侧任务查看问题摘要和 HTML 报告。</p>
        </div>
      </section>
    </section>
  </section>
</template>
