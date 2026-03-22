<script setup lang="ts">
import { computed, ref } from 'vue'
import StatusChip from './StatusChip.vue'
import { sourceCatalogMap } from '../sourceCatalog'
import type { SyncJob } from '../types'
import {
  formatBytes,
  formatDateTime,
  formatJobPhase,
  formatJobStatus,
  formatLag,
  formatSyncMode,
  statusTone
} from '../utils/formatters'

const props = defineProps<{
  jobs: SyncJob[]
  activeJobId: number | null
}>()

const emit = defineEmits<{
  select: [job: SyncJob]
  start: [id: number]
  stop: [id: number]
}>()

const keyword = ref('')
const statusFilter = ref<'ALL' | string>('ALL')

const filteredJobs = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase()
  return props.jobs.filter(job => {
    const matchesKeyword = !normalizedKeyword
      || job.name.toLowerCase().includes(normalizedKeyword)
      || sourceCatalogMap[job.sourceDatabaseType].label.toLowerCase().includes(normalizedKeyword)
      || (job.latestTable || '').toLowerCase().includes(normalizedKeyword)
    const matchesStatus = statusFilter.value === 'ALL' || job.status === statusFilter.value
    return matchesKeyword && matchesStatus
  })
})

const jobStatusOptions = computed(() => {
  const counts = new Map<string, number>()
  for (const job of props.jobs) {
    counts.set(job.status, (counts.get(job.status) || 0) + 1)
  }
  return [
    { value: 'ALL', label: `全部 (${props.jobs.length})` },
    ...Array.from(counts.entries()).map(([value, count]) => ({
      value,
      label: `${formatJobStatus(value)} (${count})`
    }))
  ]
})
</script>

<template>
  <section class="panel">
    <div class="panel-header">
      <div>
        <p class="eyebrow">Step 06</p>
        <h2>任务监控与运行</h2>
      </div>
      <span class="muted">点击任务查看明细，支持直接启动和停止</span>
    </div>

    <div class="table-toolbar">
      <label class="toolbar-field">
        <span>按任务/源端/表名筛选</span>
        <input v-model="keyword" placeholder="例如：oracle、order、finance_job" />
      </label>
      <label class="toolbar-field compact-field">
        <span>状态</span>
        <select v-model="statusFilter">
          <option v-for="option in jobStatusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
      </label>
    </div>

    <div class="table-summary-bar" v-if="jobs.length">
      <span>当前共 {{ jobs.length }} 条任务，筛选后 {{ filteredJobs.length }} 条</span>
      <span>运行中 {{ jobs.filter(job => job.status === 'RUNNING').length }} 条</span>
    </div>

    <div class="table" v-if="filteredJobs.length">
      <div class="table-head monitor-table enhanced-monitor-table">
        <span>任务与来源</span>
        <span>同步策略</span>
        <span>全量进度</span>
        <span>增量状态</span>
        <span>最近更新时间</span>
        <span>操作</span>
      </div>
      <div
        v-for="job in filteredJobs"
        :key="job.id"
        class="table-row monitor-table enhanced-monitor-table"
        :class="{ active: activeJobId === job.id }"
        @click="emit('select', job)"
      >
        <span class="stack-cell">
          <div class="source-card-header compact-source-header">
            <div
              class="source-logo mini-logo"
              :style="{ '--logo-accent': sourceCatalogMap[job.sourceDatabaseType].accent, '--logo-surface': sourceCatalogMap[job.sourceDatabaseType].surface }"
            >
              <span>{{ sourceCatalogMap[job.sourceDatabaseType].logoText }}</span>
            </div>
            <div class="stack-cell">
              <strong>{{ job.name }}</strong>
              <small>{{ sourceCatalogMap[job.sourceDatabaseType].label }}</small>
              <div class="inline-chip-row">
                <StatusChip :label="formatJobStatus(job.status)" :tone="statusTone(job.status)" />
                <StatusChip :label="formatJobPhase(job.phase)" :tone="statusTone(job.phase)" />
              </div>
            </div>
          </div>
        </span>
        <span class="stack-cell">
          <strong>{{ formatSyncMode(job.syncMode) }}</strong>
          <small>进度 {{ job.progressPercent }}%</small>
        </span>
        <span class="stack-cell">
          <strong>{{ job.exportedTableCount ?? 0 }}/{{ job.totalTableCount ?? 0 }} 表已导出</strong>
          <small>导出 {{ formatBytes(job.exportedBytes) }}</small>
          <small>导入 {{ formatBytes(job.importedBytes) }}</small>
        </span>
        <span class="stack-cell">
          <strong>{{ job.latestLogPosition || '尚未产生位点' }}</strong>
          <small>延迟 {{ formatLag(job.lastLagMillis) }}</small>
          <small>{{ job.latestTable ? `最新表 ${job.latestTable}` : '尚无最新表信息' }}</small>
        </span>
        <span class="stack-cell">
          <strong>{{ formatDateTime(job.updatedAt) }}</strong>
          <small>{{ job.lastMessage || '暂无运行消息' }}</small>
        </span>
        <span class="actions">
          <button class="primary" :disabled="job.status === 'RUNNING'" @click.stop="emit('start', job.id)">启动</button>
          <button class="ghost" :disabled="job.status !== 'RUNNING'" @click.stop="emit('stop', job.id)">停止</button>
        </span>
      </div>
    </div>
    <div v-else class="empty-state">
      <strong>{{ jobs.length ? '没有匹配的任务' : '暂无任务' }}</strong>
      <p>{{ jobs.length ? '可以调整关键字或状态筛选条件。' : '请先完成首页设置，再进入“创建同步任务”新增一条任务。' }}</p>
    </div>
  </section>
</template>
