<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchRuntimeLogFiles, fetchRuntimeLogTail } from '../api'
import type { RuntimeLogFile, RuntimeLogTail } from '../types'

const files = ref<RuntimeLogFile[]>([])
const activeKey = ref('app')
const lineCount = ref(200)
const loading = ref(false)
const tail = ref<RuntimeLogTail | null>(null)
const errorMessage = ref('')

const activeFile = computed(() => files.value.find(file => file.key === activeKey.value) ?? null)

function formatBytes(value: number): string {
  if (value < 1024) {
    return `${value} B`
  }
  const units = ['KiB', 'MiB', 'GiB', 'TiB']
  let size = value
  let unit = -1
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024
    unit++
  }
  return `${size.toFixed(size >= 10 || unit <= 0 ? 0 : 1)} ${units[unit]}`
}

async function loadFiles() {
  files.value = await fetchRuntimeLogFiles()
  if (!files.value.some(file => file.key === activeKey.value) && files.value.length) {
    activeKey.value = files.value[0].key
  }
}

async function loadTail() {
  loading.value = true
  try {
    tail.value = await fetchRuntimeLogTail(activeKey.value, lineCount.value)
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载运行日志失败'
  } finally {
    loading.value = false
  }
}

async function refresh() {
  await loadFiles()
  await loadTail()
}

onMounted(async () => {
  await refresh()
})
</script>

<template>
  <section class="content-stack">
    <section class="panel">
      <div class="panel-header">
        <div>
          <p class="eyebrow">Step 06</p>
          <h2>运行日志中心</h2>
        </div>
        <span class="muted">查看应用运行日志、错误日志和文件路径</span>
      </div>

      <div class="guide-panel subtle-panel">
        <p class="guide-text">
          这里展示的是平台自身运行日志，不是单个任务的业务日志。遇到启动失败、接口报错、工具调用异常时，优先看这里的 `app.log` 和 `error.log`。
        </p>
      </div>

      <div class="form-grid">
        <label>
          <span>日志文件</span>
          <select v-model="activeKey" @change="loadTail">
            <option v-for="file in files" :key="file.key" :value="file.key">{{ file.displayName }}</option>
          </select>
        </label>
        <label>
          <span>尾部行数</span>
          <select v-model.number="lineCount" @change="loadTail">
            <option :value="100">100</option>
            <option :value="200">200</option>
            <option :value="500">500</option>
            <option :value="1000">1000</option>
          </select>
        </label>
        <label class="wide">
          <span>文件绝对路径</span>
          <input :value="activeFile?.absolutePath || tail?.absolutePath || '-'" disabled />
        </label>
      </div>

      <div class="hero-actions">
        <button class="primary" :disabled="loading" @click="refresh">{{ loading ? '刷新中...' : '刷新日志' }}</button>
      </div>

      <div class="dashboard-grid">
        <div class="metric-card">
          <span class="metric-label">是否存在</span>
          <strong>{{ tail?.exists ? 'YES' : 'NO' }}</strong>
        </div>
        <div class="metric-card">
          <span class="metric-label">当前文件大小</span>
          <strong>{{ tail ? formatBytes(tail.sizeBytes) : '-' }}</strong>
        </div>
        <div class="metric-card">
          <span class="metric-label">已加载行数</span>
          <strong>{{ tail?.lineCount ?? 0 }}</strong>
        </div>
        <div class="metric-card">
          <span class="metric-label">最后修改时间</span>
          <strong>{{ tail?.lastModifiedAt ? new Date(tail.lastModifiedAt).toLocaleString() : '-' }}</strong>
        </div>
      </div>

      <div v-if="errorMessage" class="wide-card error">
        <span class="metric-label">加载失败</span>
        <p>{{ errorMessage }}</p>
      </div>

      <div class="runtime-log-viewer">
        <pre v-if="tail?.lines.length"><code>{{ tail.lines.join('\n') }}</code></pre>
        <div v-else class="empty-state compact">
          <strong>当前没有可显示的日志内容</strong>
          <p>如果文件刚创建或应用还没有产生日志，这里会暂时为空。</p>
        </div>
      </div>
    </section>
  </section>
</template>
