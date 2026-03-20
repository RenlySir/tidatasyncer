<script setup lang="ts">
import { onMounted, ref } from 'vue'
import JobForm from './components/JobForm.vue'
import JobTable from './components/JobTable.vue'
import JobDetail from './components/JobDetail.vue'
import { createJob, fetchJobDefinition, fetchJobLogs, fetchJobs, fetchOverview, startJob, stopJob, updateJob } from './api'
import type { DashboardOverview, SyncJob, SyncJobDefinition, SyncJobLog } from './types'

const overview = ref<DashboardOverview | null>(null)
const jobs = ref<SyncJob[]>([])
const activeJob = ref<SyncJob | null>(null)
const activeDefinition = ref<SyncJobDefinition | null>(null)
const logs = ref<SyncJobLog[]>([])

async function loadJobs() {
  jobs.value = await fetchJobs()
  overview.value = await fetchOverview()
  if (activeJob.value) {
    activeJob.value = jobs.value.find(job => job.id === activeJob.value?.id) ?? activeJob.value
  }
}

async function selectJob(job: SyncJob) {
  activeJob.value = job
  const [jobLogs, definition] = await Promise.all([fetchJobLogs(job.id), fetchJobDefinition(job.id)])
  logs.value = jobLogs
  activeDefinition.value = definition
}

async function saveJob(name: string, definition: SyncJobDefinition) {
  if (activeJob.value) {
    await updateJob(activeJob.value.id, name, definition)
  } else {
    await createJob(name, definition)
  }
  await loadJobs()
}

async function handleStart(id: number) {
  await startJob(id)
  await loadJobs()
  const job = jobs.value.find(item => item.id === id)
  if (job) {
    await selectJob(job)
  }
}

async function handleStop(id: number) {
  await stopJob(id)
  await loadJobs()
  const job = jobs.value.find(item => item.id === id)
  if (job) {
    await selectJob(job)
  }
}

function resetForm() {
  activeJob.value = null
  activeDefinition.value = null
  logs.value = []
}

onMounted(async () => {
  await loadJobs()
  window.setInterval(loadJobs, 5000)
})
</script>

<template>
  <main class="shell">
    <section class="hero">
      <div>
        <p class="eyebrow">TiDB Sync Platform</p>
        <h1>异构数据库到 TiDB 的全量与增量同步控制台</h1>
        <p class="hero-copy">
          在一个页面里完成任务配置、启动停止、同步进度观察、延迟与错误定位。
        </p>
        <div class="hero-actions">
          <button class="primary" @click="resetForm">新建任务</button>
        </div>
      </div>
      <div class="hero-metrics" v-if="overview">
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
      </div>
    </section>

    <section class="layout">
      <div class="column left">
        <JobForm :job="activeJob" :definition="activeDefinition" @save="saveJob" />
        <JobTable :jobs="jobs" :active-job-id="activeJob?.id ?? null" @select="selectJob" @start="handleStart" @stop="handleStop" />
      </div>
      <div class="column right">
        <JobDetail :job="activeJob" :logs="logs" />
      </div>
    </section>
  </main>
</template>
