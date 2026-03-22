<script setup lang="ts">
import { computed, ref } from 'vue'
import StatusChip from './StatusChip.vue'
import { sourceCatalogMap } from '../sourceCatalog'
import type { SyncJob, SyncJobLog } from '../types'
import {
  formatBytes,
  formatDateTime,
  formatJobPhase,
  formatJobStatus,
  formatLag,
  formatSyncMode,
  statusTone,
  toDisplayPercent
} from '../utils/formatters'

const props = defineProps<{
  job: SyncJob | null
  logs: SyncJobLog[]
}>()

const sourceMeta = computed(() => (props.job ? sourceCatalogMap[props.job.sourceDatabaseType] : null))
const logLevelFilter = ref<'ALL' | string>('ALL')

const filteredLogs = computed(() => {
  if (logLevelFilter.value === 'ALL') {
    return props.logs
  }
  return props.logs.filter(log => log.level === logLevelFilter.value)
})

const logLevelOptions = computed(() => {
  const counts = new Map<string, number>()
  for (const log of props.logs) {
    counts.set(log.level, (counts.get(log.level) || 0) + 1)
  }
  return [
    { value: 'ALL', label: `全部 (${props.logs.length})` },
    ...Array.from(counts.entries()).map(([value, count]) => ({ value, label: `${value} (${count})` }))
  ]
})

const progressStyle = computed(() => ({
  width: props.job ? toDisplayPercent(props.job.progressPercent) : '0%'
}))
</script>

<template>
  <section class="panel detail-panel">
    <div class="panel-header">
      <div>
        <p class="eyebrow">监控详情</p>
        <h2>任务运行明细</h2>
      </div>
      <span class="muted" v-if="job">ID #{{ job.id }}</span>
    </div>

    <div v-if="job" class="detail-stack">
      <div class="wide-card source-detail-card detail-hero-card">
        <div class="source-card-header">
          <div v-if="sourceMeta" class="source-logo" :style="{ '--logo-accent': sourceMeta.accent, '--logo-surface': sourceMeta.surface }">
            <span>{{ sourceMeta.logoText }}</span>
          </div>
          <div class="detail-hero-main">
            <span class="metric-label">当前任务</span>
            <strong>{{ job.name }}</strong>
            <p class="muted">{{ sourceMeta?.label }} 到 TiDB · {{ formatSyncMode(job.syncMode) }}</p>
            <div class="inline-chip-row">
              <StatusChip :label="formatJobStatus(job.status)" :tone="statusTone(job.status)" />
              <StatusChip :label="formatJobPhase(job.phase)" :tone="statusTone(job.phase)" />
            </div>
          </div>
        </div>
        <div class="progress-block">
          <div class="progress-meta">
            <span>整体进度</span>
            <strong>{{ toDisplayPercent(job.progressPercent) }}</strong>
          </div>
          <div class="progress-track">
            <div class="progress-fill" :style="progressStyle"></div>
          </div>
        </div>
      </div>

      <div class="detail-grid">
        <div class="metric-card">
          <span class="metric-label">全量导出</span>
          <strong>{{ job.exportedTableCount ?? 0 }}/{{ job.totalTableCount ?? 0 }} 表</strong>
          <small>{{ formatBytes(job.exportedBytes) }}</small>
        </div>
        <div class="metric-card">
          <span class="metric-label">Lightning 导入</span>
          <strong>{{ job.importedTableCount ?? 0 }}/{{ job.totalTableCount ?? 0 }} 表</strong>
          <small>{{ formatBytes(job.importedBytes) }}</small>
        </div>
        <div class="metric-card">
          <span class="metric-label">增量延迟</span>
          <strong>{{ formatLag(job.lastLagMillis) }}</strong>
          <small>按 connector 上报口径展示</small>
        </div>
        <div class="metric-card">
          <span class="metric-label">最近位点</span>
          <strong>{{ job.latestLogPosition || '-' }}</strong>
          <small>{{ formatDateTime(job.updatedAt) }}</small>
        </div>
      </div>

      <div class="detail-grid">
        <div class="wide-card">
          <span class="metric-label">最近对象</span>
          <p class="detail-value-text">{{ job.latestCatalog || '-' }} / {{ job.latestSchema || '-' }} / {{ job.latestTable || '-' }}</p>
        </div>
        <div class="wide-card">
          <span class="metric-label">最新主键</span>
          <code>{{ job.latestPrimaryKey || '-' }}</code>
        </div>
        <div class="wide-card">
          <span class="metric-label">最近运行消息</span>
          <p class="detail-value-text">{{ job.lastMessage || '暂无运行消息' }}</p>
        </div>
        <div class="wide-card error" v-if="job.lastError">
          <span class="metric-label">最近错误</span>
          <p class="detail-value-text">{{ job.lastError }}</p>
        </div>
      </div>
    </div>
    <div v-else class="empty-state">
      <strong>尚未选中任务</strong>
      <p>在左侧列表选择一条任务后，这里会显示全量导表、Lightning 导入、增量位点和最近日志。</p>
    </div>

    <div class="sub-panel">
      <div class="sub-panel-header">
        <h3>最近日志</h3>
        <label class="toolbar-field compact-field">
          <span>级别</span>
          <select v-model="logLevelFilter">
            <option v-for="option in logLevelOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
      </div>
      <div class="log-list" v-if="filteredLogs.length">
        <div v-for="log in filteredLogs" :key="log.id" class="log-item">
          <span class="log-time">{{ formatDateTime(log.createdAt) }}</span>
          <StatusChip :label="log.level" :tone="log.level === 'ERROR' ? 'error' : log.level === 'WARN' ? 'warn' : 'info'" />
          <span>{{ log.message }}</span>
        </div>
      </div>
      <div v-else class="empty-state compact">
        <strong>暂无日志</strong>
        <p>{{ logs.length ? '当前筛选条件下没有日志。' : '任务启动后，这里会持续显示最近的运行日志。' }}</p>
      </div>
    </div>
  </section>
</template>
