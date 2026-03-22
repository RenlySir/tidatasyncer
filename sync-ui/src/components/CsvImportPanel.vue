<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { prepareCsvDirectory, startCsvDirectoryImport } from '../api'
import type { CsvDirectoryImportResponse, CsvDirectoryPrepareResponse, DeploymentArchitecture } from '../types'

const props = defineProps<{
  deploymentArchitecture: DeploymentArchitecture
  defaultLightningBinary: string
}>()

const state = reactive({
  directoryPath: '',
  target: {
    host: '127.0.0.1',
    port: 4000,
    databaseName: '',
    username: 'root',
    password: '',
    jdbcUrl: '',
    jdbcParameters: 'useUnicode=true&characterEncoding=utf8&rewriteBatchedStatements=true',
    lightningBinary: props.defaultLightningBinary,
    statusPort: 10080
  }
})

const preparing = ref(false)
const importing = ref(false)
const prepareResult = ref<CsvDirectoryPrepareResponse | null>(null)
const importResult = ref<CsvDirectoryImportResponse | null>(null)
const errorMessage = ref('')
const targetUrlPreview = computed(() => {
  const parameters = normalizeQueryParameters(state.target.jdbcParameters || '')
  return `jdbc:mysql://${state.target.host || ''}${state.target.port ? `:${state.target.port}` : ''}/${state.target.databaseName || ''}${parameters ? `?${parameters}` : ''}`
})

watch(
  () => props.defaultLightningBinary,
  (next, previous) => {
    const current = state.target.lightningBinary
    if (!current || current === 'tidb-lightning' || (previous && current === previous)) {
      state.target.lightningBinary = next
    }
  }
)

async function handlePrepare() {
  preparing.value = true
  errorMessage.value = ''
  importResult.value = null
  try {
    prepareResult.value = await prepareCsvDirectory(state.directoryPath)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '目录检查失败'
  } finally {
    preparing.value = false
  }
}

async function handleImport() {
  importing.value = true
  errorMessage.value = ''
  try {
    importResult.value = await startCsvDirectoryImport(
      state.directoryPath,
      props.deploymentArchitecture,
      state.target
    )
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'CSV 导入失败'
  } finally {
    importing.value = false
  }
}

function normalizeQueryParameters(parameters: string): string {
  let normalized = parameters.trim()
  while (normalized.startsWith('?') || normalized.startsWith('&')) {
    normalized = normalized.slice(1)
  }
  return normalized
}
</script>

<template>
  <section class="panel">
    <div class="panel-header">
      <h2>CSV 导入 TiDB</h2>
      <span class="muted">先检查字符集和文件大小，再执行导入</span>
    </div>

    <div class="form-grid">
      <label class="wide">
        <span>CSV 目录</span>
        <input v-model="state.directoryPath" placeholder="/data/lightning/orders" />
      </label>
      <label>
        <span>部署机器架构</span>
        <input :value="props.deploymentArchitecture" disabled />
      </label>
      <label class="wide">
        <span>Lightning 二进制</span>
        <input v-model="state.target.lightningBinary" />
      </label>
      <label>
        <span>TiDB 主机</span>
        <input v-model="state.target.host" />
      </label>
      <label>
        <span>TiDB 端口</span>
        <input v-model.number="state.target.port" type="number" />
      </label>
      <label>
        <span>TiDB 用户名</span>
        <input v-model="state.target.username" />
      </label>
      <label>
        <span>TiDB 密码</span>
        <input v-model="state.target.password" type="password" />
      </label>
      <label class="wide">
        <span>TiDB JDBC 参数</span>
        <input v-model="state.target.jdbcParameters" placeholder="useUnicode=true&characterEncoding=utf8&rewriteBatchedStatements=true" />
      </label>
      <label class="wide">
        <span>TiDB JDBC URL 预览</span>
        <input :value="targetUrlPreview" disabled />
      </label>
      <label>
        <span>TiDB Status Port</span>
        <input v-model.number="state.target.statusPort" type="number" />
      </label>
    </div>

    <div class="hero-actions">
      <button class="primary" :disabled="preparing || !state.directoryPath" @click="handlePrepare">
        {{ preparing ? '检查中...' : '检查 / 转码 / 切分' }}
      </button>
      <button
        class="ghost"
        :disabled="importing || !prepareResult"
        @click="handleImport"
      >
        {{ importing ? '导入中...' : '开始导入' }}
      </button>
    </div>

    <div v-if="prepareResult" class="sub-panel">
      <div class="sub-panel-header">
        <h3>检查结果</h3>
      </div>
      <div class="detail-grid">
        <div class="metric-card">
          <span class="metric-label">CSV 文件数</span>
          <strong>{{ prepareResult.totalCsvFiles }}</strong>
        </div>
        <div class="metric-card">
          <span class="metric-label">转码为 UTF-8</span>
          <strong>{{ prepareResult.convertedCharsetFiles }}</strong>
        </div>
        <div class="metric-card">
          <span class="metric-label">切分源文件</span>
          <strong>{{ prepareResult.splitSourceFiles }}</strong>
        </div>
        <div class="metric-card">
          <span class="metric-label">生成分片</span>
          <strong>{{ prepareResult.generatedChunkFiles }}</strong>
        </div>
        <div class="metric-card">
          <span class="metric-label">无需切分</span>
          <strong>{{ prepareResult.unchangedFiles }}</strong>
        </div>
        <div class="wide-card">
          <span class="metric-label">结果</span>
          <p>{{ prepareResult.message }}</p>
        </div>
        <div class="wide-card">
          <span class="metric-label">目录文件</span>
          <div class="tag-list">
            <code v-for="file in prepareResult.csvFiles" :key="file" class="tag">{{ file }}</code>
          </div>
        </div>
      </div>
    </div>

    <div v-if="importResult" class="sub-panel">
      <div class="sub-panel-header">
        <h3>导入结果</h3>
      </div>
      <div class="wide-card">
        <span class="metric-label">结果</span>
        <p>{{ importResult.message }}，共导入 {{ importResult.importedCsvFiles }} 个 CSV 文件。</p>
      </div>
    </div>

    <div v-if="errorMessage" class="wide-card error" style="margin-top: 20px;">
      <span class="metric-label">错误</span>
      <p>{{ errorMessage }}</p>
    </div>
  </section>
</template>
