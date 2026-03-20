<script setup lang="ts">
import type { SyncJob, SyncJobLog } from '../types'

defineProps<{
  job: SyncJob | null
  logs: SyncJobLog[]
}>()
</script>

<template>
  <section class="panel detail-panel">
    <div class="panel-header">
      <h2>任务详情</h2>
      <span class="muted" v-if="job">ID #{{ job.id }}</span>
    </div>

    <div v-if="job" class="detail-grid">
      <div class="metric-card">
        <span class="metric-label">当前状态</span>
        <strong>{{ job.status }}</strong>
      </div>
      <div class="metric-card">
        <span class="metric-label">当前阶段</span>
        <strong>{{ job.phase }}</strong>
      </div>
      <div class="metric-card">
        <span class="metric-label">同步延迟</span>
        <strong>{{ job.lastLagMillis ?? '-' }}</strong>
      </div>
      <div class="metric-card">
        <span class="metric-label">最新事件</span>
        <strong>{{ job.latestSchema }}.{{ job.latestTable }}</strong>
      </div>
      <div class="wide-card">
        <span class="metric-label">最新主键信息</span>
        <code>{{ job.latestPrimaryKey || '-' }}</code>
      </div>
      <div class="wide-card">
        <span class="metric-label">最新消息</span>
        <p>{{ job.lastMessage || '-' }}</p>
      </div>
      <div class="wide-card error" v-if="job.lastError">
        <span class="metric-label">最近错误</span>
        <p>{{ job.lastError }}</p>
      </div>
    </div>

    <div class="sub-panel">
      <div class="sub-panel-header">
        <h3>最近日志</h3>
      </div>
      <div class="log-list">
        <div v-for="log in logs" :key="log.id" class="log-item">
          <span class="log-time">{{ new Date(log.createdAt).toLocaleString() }}</span>
          <strong>[{{ log.level }}]</strong>
          <span>{{ log.message }}</span>
        </div>
      </div>
    </div>
  </section>
</template>
