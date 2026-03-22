<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import StatusChip from './StatusChip.vue'
import { checkConnectionProfilePermissions } from '../api'
import { sourceCatalog, sourceCatalogMap } from '../sourceCatalog'
import type {
  ConnectionProfilePermissionCheck,
  ConnectionProfile,
  ConnectionProfileUpsert,
  DashboardOverview,
  DatabaseEndpointType,
  DeploymentArchitecture
} from '../types'
import { formatDateTime } from '../utils/formatters'

const props = defineProps<{
  overview: DashboardOverview | null
  sourceProfiles: ConnectionProfile[]
  targetProfiles: ConnectionProfile[]
  deploymentArchitecture: DeploymentArchitecture
  savingSettings: boolean
  settingsDirty: boolean
  settingsMessage: string
}>()

const emit = defineEmits<{
  updateArchitecture: [architecture: DeploymentArchitecture]
  saveSettings: []
  saveSourceProfile: [id: number | null, payload: ConnectionProfileUpsert]
  saveTargetProfile: [id: number | null, payload: ConnectionProfileUpsert]
}>()

const sourceTypes = sourceCatalog.map(item => item.type)

const sourceForm = reactive({
  editingId: null as number | null,
  name: '',
  databaseType: 'MYSQL' as DatabaseEndpointType,
  host: '127.0.0.1',
  port: 3306,
  databaseName: '',
  schemaName: '',
  username: '',
  password: '',
  jdbcParameters: '',
  csvDirectory: '',
  permissionNote: ''
})

const targetForm = reactive({
  editingId: null as number | null,
  name: '',
  host: '127.0.0.1',
  port: 4000,
  databaseName: '',
  schemaName: '',
  username: 'root',
  password: '',
  jdbcParameters: 'useUnicode=true&characterEncoding=utf8&rewriteBatchedStatements=true',
  permissionNote: '',
  tidbStatusPort: 10080
})

const sourceDefaultPorts: Record<string, number> = {
  CSV: 0,
  MYSQL: 3306,
  MARIADB: 3306,
  ORACLE: 1521,
  SQLSERVER: 1433,
  POSTGRESQL: 5432,
  DB2: 50000,
  HANA: 30015,
  MONGODB: 27017
}

const sourceMeta = computed(() => sourceCatalogMap[sourceForm.databaseType as keyof typeof sourceCatalogMap] ?? sourceCatalogMap.MYSQL)
const permissionCheckLoadingId = ref<number | null>(null)
const permissionCheckError = ref('')
const activePermissionCheck = ref<ConnectionProfilePermissionCheck | null>(null)
const profileKeyword = ref('')
const profileViewMode = ref<'table' | 'cards' | 'topology'>('table')

const filteredSourceProfiles = computed(() => filterProfiles(props.sourceProfiles))
const filteredTargetProfiles = computed(() => filterProfiles(props.targetProfiles))
const sourceTypeSummary = computed(() => {
  const counts = new Map<string, number>()
  for (const profile of props.sourceProfiles) {
    counts.set(profile.databaseType, (counts.get(profile.databaseType) || 0) + 1)
  }
  return Array.from(counts.entries()).map(([type, count]) => ({ type, count }))
})

function filterProfiles(profiles: ConnectionProfile[]) {
  const keyword = profileKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return profiles
  }
  return profiles.filter(profile =>
    profile.name.toLowerCase().includes(keyword)
      || (profile.databaseType || '').toLowerCase().includes(keyword)
      || (profile.host || '').toLowerCase().includes(keyword)
      || (profile.databaseName || '').toLowerCase().includes(keyword)
      || (profile.csvDirectory || '').toLowerCase().includes(keyword)
  )
}

watch(
  () => sourceForm.databaseType,
  databaseType => {
    if (databaseType === 'CSV') {
      sourceForm.port = 0
      sourceForm.host = ''
      sourceForm.username = ''
      sourceForm.password = ''
      sourceForm.databaseName = ''
      sourceForm.schemaName = ''
      return
    }
    sourceForm.port = sourceDefaultPorts[databaseType] ?? sourceForm.port
    if (!sourceForm.host) {
      sourceForm.host = '127.0.0.1'
    }
  }
)

function resetSourceForm() {
  sourceForm.editingId = null
  sourceForm.name = ''
  sourceForm.databaseType = 'MYSQL'
  sourceForm.host = '127.0.0.1'
  sourceForm.port = 3306
  sourceForm.databaseName = ''
  sourceForm.schemaName = ''
  sourceForm.username = ''
  sourceForm.password = ''
  sourceForm.jdbcParameters = ''
  sourceForm.csvDirectory = ''
  sourceForm.permissionNote = ''
}

function resetTargetForm() {
  targetForm.editingId = null
  targetForm.name = ''
  targetForm.host = '127.0.0.1'
  targetForm.port = 4000
  targetForm.databaseName = ''
  targetForm.schemaName = ''
  targetForm.username = 'root'
  targetForm.password = ''
  targetForm.jdbcParameters = 'useUnicode=true&characterEncoding=utf8&rewriteBatchedStatements=true'
  targetForm.permissionNote = ''
  targetForm.tidbStatusPort = 10080
}

function editSourceProfile(profile: ConnectionProfile) {
  sourceForm.editingId = profile.id
  sourceForm.name = profile.name
  sourceForm.databaseType = profile.databaseType as DatabaseEndpointType
  sourceForm.host = profile.host || ''
  sourceForm.port = profile.port || 0
  sourceForm.databaseName = profile.databaseName || ''
  sourceForm.schemaName = profile.schemaName || ''
  sourceForm.username = profile.username || ''
  sourceForm.password = profile.password || ''
  sourceForm.jdbcParameters = profile.jdbcParameters || ''
  sourceForm.csvDirectory = profile.csvDirectory || ''
  sourceForm.permissionNote = profile.permissionNote || ''
}

function editTargetProfile(profile: ConnectionProfile) {
  targetForm.editingId = profile.id
  targetForm.name = profile.name
  targetForm.host = profile.host || ''
  targetForm.port = profile.port || 4000
  targetForm.databaseName = profile.databaseName || ''
  targetForm.schemaName = profile.schemaName || ''
  targetForm.username = profile.username || 'root'
  targetForm.password = profile.password || ''
  targetForm.jdbcParameters = profile.jdbcParameters || ''
  targetForm.permissionNote = profile.permissionNote || ''
  targetForm.tidbStatusPort = profile.tidbStatusPort || 10080
}

function saveSource() {
  emit('saveSourceProfile', sourceForm.editingId, {
    name: sourceForm.name,
    role: 'SOURCE',
    databaseType: sourceForm.databaseType,
    host: sourceForm.databaseType === 'CSV' ? null : sourceForm.host,
    port: sourceForm.databaseType === 'CSV' ? null : sourceForm.port,
    databaseName: sourceForm.databaseType === 'CSV' ? null : sourceForm.databaseName,
    schemaName: sourceForm.databaseType === 'CSV' ? null : sourceForm.schemaName,
    username: sourceForm.databaseType === 'CSV' ? null : sourceForm.username,
    password: sourceForm.databaseType === 'CSV' ? null : sourceForm.password,
    jdbcUrl: null,
    jdbcParameters: sourceForm.databaseType === 'CSV' ? null : sourceForm.jdbcParameters,
    csvDirectory: sourceForm.databaseType === 'CSV' ? sourceForm.csvDirectory : null,
    permissionNote: sourceForm.permissionNote || null,
    tidbStatusPort: null
  })
  resetSourceForm()
}

function saveTarget() {
  emit('saveTargetProfile', targetForm.editingId, {
    name: targetForm.name,
    role: 'TARGET',
    databaseType: 'TIDB',
    host: targetForm.host,
    port: targetForm.port,
    databaseName: targetForm.databaseName,
    schemaName: targetForm.schemaName || null,
    username: targetForm.username,
    password: targetForm.password,
    jdbcUrl: null,
    jdbcParameters: targetForm.jdbcParameters,
    csvDirectory: null,
    permissionNote: targetForm.permissionNote || null,
    tidbStatusPort: targetForm.tidbStatusPort
  })
  resetTargetForm()
}

async function runPermissionCheck(profile: ConnectionProfile) {
  permissionCheckLoadingId.value = profile.id
  permissionCheckError.value = ''
  try {
    activePermissionCheck.value = await checkConnectionProfilePermissions(profile.id)
  } catch (error) {
    permissionCheckError.value = error instanceof Error ? error.message : '执行权限检测失败'
  } finally {
    permissionCheckLoadingId.value = null
  }
}
</script>

<template>
  <section class="content-stack">
    <section class="panel">
      <div class="panel-header">
        <div>
          <p class="eyebrow">Step 01</p>
          <h2>创建数据源与数据目标</h2>
          <p class="guide-text">先保存部署机器架构，再分别维护源端和 TiDB 目标端连接。后续工具路径、表结构任务和数据同步任务都会复用这里的连接配置。</p>
        </div>
      </div>

        <div class="guide-grid">
          <article class="guide-card">
            <h3>操作顺序</h3>
            <div class="guide-list">
              <div class="log-item"><span>1. 先保存部署机器架构，后续工具默认路径会按这个架构切换。</span></div>
              <div class="log-item"><span>2. 创建源端连接，CSV 源只填目录，数据库源填写主机、端口、账号、密码、库名和 schema。</span></div>
              <div class="log-item"><span>3. 创建 TiDB 目标连接，补充 tidb status port，供 Lightning 和状态检查使用。</span></div>
              <div class="log-item"><span>4. 保存后可直接执行权限检测，不通过时会返回缺失权限和建议授权 SQL。</span></div>
            </div>
          </article>

        <article class="guide-card">
          <h3>当前概览</h3>
          <div class="dashboard-grid">
            <div class="metric-card compact-card">
              <span class="metric-label">源端连接</span>
              <strong>{{ sourceProfiles.length }}</strong>
            </div>
            <div class="metric-card compact-card">
              <span class="metric-label">目标连接</span>
              <strong>{{ targetProfiles.length }}</strong>
            </div>
            <div class="metric-card compact-card">
              <span class="metric-label">任务总数</span>
              <strong>{{ overview?.totalJobs ?? 0 }}</strong>
            </div>
            <div class="metric-card compact-card">
              <span class="metric-label">运行中</span>
              <strong>{{ overview?.runningJobs ?? 0 }}</strong>
            </div>
          </div>
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
    </section>

    <section class="section-grid">
      <section class="panel">
        <div class="panel-header">
          <div>
            <h2>创建数据源</h2>
            <span class="muted">支持整库 / 单表后续任务复用，CSV 源仅支持全量导入</span>
          </div>
          <button class="ghost" @click="resetSourceForm">新建源</button>
        </div>

        <div class="source-picker-grid">
          <button
            v-for="item in sourceCatalog"
            :key="item.type"
            class="source-picker-card"
            :class="{ active: sourceForm.databaseType === item.type }"
            @click="sourceForm.databaseType = item.type"
          >
            <div class="source-card-header">
              <div class="source-logo" :style="{ '--logo-accent': item.accent, '--logo-surface': item.surface }">
                <span>{{ item.logoText }}</span>
              </div>
              <div>
                <strong>{{ item.label }}</strong>
                <small>{{ item.vendor }}</small>
              </div>
            </div>
            <p>{{ item.summary }}</p>
          </button>
        </div>

        <div class="form-grid profile-form">
          <label>
            <span>数据源名称</span>
            <input v-model="sourceForm.name" placeholder="例如：oracle-prod-source" />
          </label>
          <label>
            <span>数据库类型</span>
            <input :value="sourceMeta.label" disabled />
          </label>
          <template v-if="sourceForm.databaseType !== 'CSV'">
            <label>
              <span>IP / Host</span>
              <input v-model="sourceForm.host" />
            </label>
            <label>
              <span>端口</span>
              <input v-model.number="sourceForm.port" type="number" />
            </label>
            <label>
              <span>库名</span>
              <input v-model="sourceForm.databaseName" />
            </label>
            <label>
              <span>Schema</span>
              <input v-model="sourceForm.schemaName" />
            </label>
            <label>
              <span>账号</span>
              <input v-model="sourceForm.username" />
            </label>
            <label>
              <span>密码</span>
              <input v-model="sourceForm.password" type="password" />
            </label>
            <label class="wide">
              <span>URL 参数</span>
              <input v-model="sourceForm.jdbcParameters" placeholder="只填参数，不用填写完整 URL" />
            </label>
          </template>
          <template v-else>
            <label class="wide">
              <span>CSV 目录</span>
              <input v-model="sourceForm.csvDirectory" placeholder="/data/csv-load" />
            </label>
          </template>
          <label class="wide">
            <span>权限说明</span>
            <textarea v-model="sourceForm.permissionNote" rows="3" placeholder="补充该数据源账号权限或 CSV 目录权限说明" />
          </label>
        </div>

        <div class="actions">
          <button class="primary" @click="saveSource">{{ sourceForm.editingId ? '更新数据源' : '保存数据源' }}</button>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <div>
            <h2>创建数据目标</h2>
            <span class="muted">当前平台目标端固定为 TiDB</span>
          </div>
          <button class="ghost" @click="resetTargetForm">新建目标</button>
        </div>

        <div class="guide-card">
          <div class="source-card-header">
            <div class="source-logo" style="--logo-accent:#1272d2;--logo-surface:rgba(18,114,210,0.12)">
              <span>Ti</span>
            </div>
            <div>
              <strong>TiDB Target</strong>
              <p class="guide-text">目标连接需要补充 SQL 端口、目标库名和 status port，供 Lightning 导入与运行状态检查使用。</p>
            </div>
          </div>
        </div>

        <div class="form-grid profile-form">
          <label>
            <span>目标名称</span>
            <input v-model="targetForm.name" placeholder="例如：tidb-prod-target" />
          </label>
          <label>
            <span>数据库类型</span>
            <input value="TiDB" disabled />
          </label>
          <label>
            <span>IP / Host</span>
            <input v-model="targetForm.host" />
          </label>
          <label>
            <span>SQL 端口</span>
            <input v-model.number="targetForm.port" type="number" />
          </label>
          <label>
            <span>库名</span>
            <input v-model="targetForm.databaseName" />
          </label>
          <label>
            <span>Schema</span>
            <input v-model="targetForm.schemaName" />
          </label>
          <label>
            <span>账号</span>
            <input v-model="targetForm.username" />
          </label>
          <label>
            <span>密码</span>
            <input v-model="targetForm.password" type="password" />
          </label>
          <label>
            <span>TiDB Status Port</span>
            <input v-model.number="targetForm.tidbStatusPort" type="number" />
          </label>
          <label class="wide">
            <span>URL 参数</span>
            <input v-model="targetForm.jdbcParameters" placeholder="useUnicode=true&characterEncoding=utf8&rewriteBatchedStatements=true" />
          </label>
          <label class="wide">
            <span>权限说明</span>
            <textarea v-model="targetForm.permissionNote" rows="3" placeholder="补充 TiDB 导入/写入账号权限说明" />
          </label>
        </div>

        <div class="actions">
          <button class="primary" @click="saveTarget">{{ targetForm.editingId ? '更新目标' : '保存目标' }}</button>
        </div>
      </section>
    </section>

    <section class="section-grid">
      <section class="panel wide-card">
        <div class="panel-header">
          <div>
            <h2>连接资源管理视图</h2>
            <span class="muted">支持表格、卡片和拓扑三种视图，适合管理多个数据源和目标连接</span>
          </div>
        </div>
        <div class="table-toolbar">
          <label class="toolbar-field">
            <span>搜索连接资源</span>
            <input v-model="profileKeyword" placeholder="按名称、类型、库名、目录、主机搜索" />
          </label>
          <label class="toolbar-field compact-field">
            <span>展示方式</span>
            <select v-model="profileViewMode">
              <option value="table">表格视图</option>
              <option value="cards">卡片视图</option>
              <option value="topology">拓扑视图</option>
            </select>
          </label>
        </div>
        <div class="tag-list">
          <span v-for="item in sourceTypeSummary" :key="item.type" class="tag">{{ item.type }} {{ item.count }} 个</span>
          <span class="tag">TiDB 目标 {{ targetProfiles.length }} 个</span>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <h2>已保存数据源</h2>
          <span class="muted">{{ filteredSourceProfiles.length }}/{{ sourceProfiles.length }} 条</span>
        </div>
        <div v-if="profileViewMode === 'table' && filteredSourceProfiles.length" class="table">
          <div class="table-head simple-table">
            <span>名称</span>
            <span>类型</span>
            <span>库 / 目录</span>
            <span>连接信息</span>
            <span>操作</span>
          </div>
          <div v-for="profile in filteredSourceProfiles" :key="profile.id" class="table-row simple-table">
            <span><strong>{{ profile.name }}</strong></span>
            <span>{{ profile.databaseType }}</span>
            <span>{{ profile.csvDirectory || profile.databaseName || '-' }}</span>
            <span>{{ profile.host || '-' }}{{ profile.port ? `:${profile.port}` : '' }}</span>
            <span class="actions">
              <button class="ghost" @click="editSourceProfile(profile)">编辑</button>
              <button class="primary" :disabled="permissionCheckLoadingId === profile.id" @click="runPermissionCheck(profile)">
                {{ permissionCheckLoadingId === profile.id ? '检测中...' : '权限检测' }}
              </button>
            </span>
          </div>
        </div>
        <div v-else-if="profileViewMode === 'cards' && filteredSourceProfiles.length" class="profile-card-grid">
          <article v-for="profile in filteredSourceProfiles" :key="profile.id" class="guide-card profile-resource-card">
            <div class="source-card-header">
              <div
                class="source-logo"
                :style="{
                  '--logo-accent': sourceCatalogMap[profile.databaseType as keyof typeof sourceCatalogMap]?.accent || '#0fa76f',
                  '--logo-surface': sourceCatalogMap[profile.databaseType as keyof typeof sourceCatalogMap]?.surface || 'rgba(15,167,111,0.14)'
                }"
              >
                <span>{{ sourceCatalogMap[profile.databaseType as keyof typeof sourceCatalogMap]?.logoText || 'DB' }}</span>
              </div>
              <div class="stack-cell">
                <strong>{{ profile.name }}</strong>
                <small>{{ profile.databaseType }}</small>
              </div>
            </div>
            <p class="guide-text">{{ profile.csvDirectory || profile.databaseName || '-' }}</p>
            <div class="tag-list compact-tags">
              <span class="tag">{{ profile.host || '目录型资源' }}{{ profile.port ? `:${profile.port}` : '' }}</span>
            </div>
            <div class="actions">
              <button class="ghost" @click="editSourceProfile(profile)">编辑</button>
              <button class="primary" :disabled="permissionCheckLoadingId === profile.id" @click="runPermissionCheck(profile)">
                {{ permissionCheckLoadingId === profile.id ? '检测中...' : '权限检测' }}
              </button>
            </div>
          </article>
        </div>
        <div v-else-if="profileViewMode === 'topology' && (filteredSourceProfiles.length || filteredTargetProfiles.length)" class="profile-topology-board">
          <div class="topology-column">
            <h3>数据源节点</h3>
            <article v-for="profile in filteredSourceProfiles" :key="profile.id" class="guide-card topology-node">
              <strong>{{ profile.name }}</strong>
              <small>{{ profile.databaseType }}</small>
            </article>
          </div>
          <div class="topology-center">
            <div class="topology-hub">同步平台</div>
          </div>
          <div class="topology-column">
            <h3>目标节点</h3>
            <article v-for="profile in filteredTargetProfiles" :key="profile.id" class="guide-card topology-node">
              <strong>{{ profile.name }}</strong>
              <small>{{ profile.databaseType }}</small>
            </article>
          </div>
        </div>
        <div v-else class="empty-state compact">
          <strong>暂无数据源</strong>
          <p>{{ sourceProfiles.length ? '当前筛选条件下没有匹配的数据源。' : '先保存至少一个源端连接，后面的工具配置和任务会直接复用。' }}</p>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <h2>已保存数据目标</h2>
          <span class="muted">{{ filteredTargetProfiles.length }}/{{ targetProfiles.length }} 条</span>
        </div>
        <div v-if="profileViewMode === 'table' && filteredTargetProfiles.length" class="table">
          <div class="table-head simple-table">
            <span>名称</span>
            <span>类型</span>
            <span>目标库</span>
            <span>连接信息</span>
            <span>操作</span>
          </div>
          <div v-for="profile in filteredTargetProfiles" :key="profile.id" class="table-row simple-table">
            <span><strong>{{ profile.name }}</strong></span>
            <span>{{ profile.databaseType }}</span>
            <span>{{ profile.databaseName || '-' }}</span>
            <span>{{ profile.host || '-' }}{{ profile.port ? `:${profile.port}` : '' }} / status {{ profile.tidbStatusPort || 10080 }}</span>
            <span class="actions">
              <button class="ghost" @click="editTargetProfile(profile)">编辑</button>
              <button class="primary" :disabled="permissionCheckLoadingId === profile.id" @click="runPermissionCheck(profile)">
                {{ permissionCheckLoadingId === profile.id ? '检测中...' : '权限检测' }}
              </button>
            </span>
          </div>
        </div>
        <div v-else-if="profileViewMode === 'cards' && filteredTargetProfiles.length" class="profile-card-grid">
          <article v-for="profile in filteredTargetProfiles" :key="profile.id" class="guide-card profile-resource-card">
            <div class="source-card-header">
              <div class="source-logo" style="--logo-accent:#1272d2;--logo-surface:rgba(18,114,210,0.12)">
                <span>Ti</span>
              </div>
              <div class="stack-cell">
                <strong>{{ profile.name }}</strong>
                <small>{{ profile.databaseType }}</small>
              </div>
            </div>
            <p class="guide-text">{{ profile.databaseName || '-' }}</p>
            <div class="tag-list compact-tags">
              <span class="tag">{{ profile.host || '-' }}{{ profile.port ? `:${profile.port}` : '' }}</span>
              <span class="tag">status {{ profile.tidbStatusPort || 10080 }}</span>
            </div>
            <div class="actions">
              <button class="ghost" @click="editTargetProfile(profile)">编辑</button>
              <button class="primary" :disabled="permissionCheckLoadingId === profile.id" @click="runPermissionCheck(profile)">
                {{ permissionCheckLoadingId === profile.id ? '检测中...' : '权限检测' }}
              </button>
            </div>
          </article>
        </div>
        <div v-else-if="profileViewMode === 'topology'" class="empty-state compact">
          <strong>目标节点已在拓扑视图中展示</strong>
          <p>切换到“拓扑视图”时，左侧展示数据源，右侧展示 TiDB 目标，便于统一管理多个节点。</p>
        </div>
        <div v-else class="empty-state compact">
          <strong>暂无目标连接</strong>
          <p>{{ targetProfiles.length ? '当前筛选条件下没有匹配的目标连接。' : '至少保存一个 TiDB 目标连接，后面的表结构同步和数据同步任务才可创建。' }}</p>
        </div>
      </section>
    </section>

    <section class="panel" v-if="activePermissionCheck || permissionCheckError">
      <div class="panel-header">
        <div>
          <p class="eyebrow">权限检测</p>
          <h2>权限检查结果</h2>
        </div>
      </div>

      <div v-if="permissionCheckError" class="wide-card error">
        <span class="metric-label">执行失败</span>
        <p>{{ permissionCheckError }}</p>
      </div>

      <template v-else-if="activePermissionCheck">
        <div class="selection-hero-grid">
          <article class="guide-card selection-hero-card">
            <span class="metric-label">检测对象</span>
            <strong>{{ activePermissionCheck.profileName }}</strong>
            <p class="muted">{{ activePermissionCheck.databaseType }} · {{ activePermissionCheck.role === 'SOURCE' ? '数据源' : '数据目标' }}</p>
            <div class="inline-chip-row">
              <StatusChip :label="activePermissionCheck.passed ? '检测通过' : '检测未通过'" :tone="activePermissionCheck.passed ? 'success' : 'error'" />
              <StatusChip :label="`检测时间 ${formatDateTime(activePermissionCheck.checkedAt)}`" tone="neutral" />
            </div>
          </article>
          <article class="guide-card selection-hero-card">
            <span class="metric-label">检测结论</span>
            <p class="guide-text">{{ activePermissionCheck.summary }}</p>
            <div class="tag-list" v-if="activePermissionCheck.missingPermissions.length">
              <span v-for="item in activePermissionCheck.missingPermissions" :key="item" class="tag">{{ item }}</span>
            </div>
          </article>
        </div>

        <div class="guide-grid">
          <article class="guide-card">
            <h3>检查项</h3>
            <div class="readiness-list">
              <div v-for="item in activePermissionCheck.checks" :key="item.key" class="readiness-item">
                <div>
                  <strong>{{ item.label }}</strong>
                  <p class="muted">{{ item.detail }}</p>
                </div>
                <StatusChip :label="item.passed ? '通过' : '未通过'" :tone="item.passed ? 'success' : 'warn'" />
              </div>
            </div>
          </article>

          <article class="guide-card" v-if="activePermissionCheck.suggestedGrantStatements.length">
            <h3>建议授权 SQL / 命令</h3>
            <p class="guide-text">复制下面的语句到对应数据库或服务器执行，完成后再重新点击“权限检测”。</p>
            <pre><code>{{ activePermissionCheck.suggestedGrantStatements.join('\n') }}</code></pre>
          </article>
        </div>
      </template>
    </section>
  </section>
</template>
