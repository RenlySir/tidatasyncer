<script setup lang="ts">
import type { SyncJob } from '../types'

defineProps<{
  jobs: SyncJob[]
  activeJobId: number | null
}>()

const emit = defineEmits<{
  select: [job: SyncJob]
  start: [id: number]
  stop: [id: number]
}>()
</script>

<template>
  <section class="panel">
    <div class="panel-header">
      <h2>任务列表</h2>
      <span class="muted">点击行查看详情</span>
    </div>

    <div class="table">
      <div class="table-head">
        <span>任务</span>
        <span>模式</span>
        <span>状态</span>
        <span>阶段</span>
        <span>延迟</span>
        <span>操作</span>
      </div>
      <div
        v-for="job in jobs"
        :key="job.id"
        class="table-row"
        :class="{ active: activeJobId === job.id }"
        @click="emit('select', job)"
      >
        <span>
          <strong>{{ job.name }}</strong>
          <small>{{ job.progressPercent }}%</small>
        </span>
        <span>{{ job.syncMode }}</span>
        <span>{{ job.status }}</span>
        <span>{{ job.phase }}</span>
        <span>{{ job.lastLagMillis ?? '-' }}</span>
        <span class="actions">
          <button class="primary" @click.stop="emit('start', job.id)">启动</button>
          <button class="ghost" @click.stop="emit('stop', job.id)">停止</button>
        </span>
      </div>
    </div>
  </section>
</template>
