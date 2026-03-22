<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { sourceCatalog, sourceCatalogMap } from '../sourceCatalog'
import type { DatabaseEndpointType, ManagedToolPaths, ToolConfig, ToolConfigUpsert } from '../types'

const props = defineProps<{
  toolConfigs: ToolConfig[]
  managedToolPaths: ManagedToolPaths
}>()

const emit = defineEmits<{
  saveToolConfig: [id: number | null, payload: ToolConfigUpsert]
}>()

const databaseTypes = [...sourceCatalog.map(item => item.type), 'TIDB'] as DatabaseEndpointType[]

const form = reactive({
  editingId: null as number | null,
  name: '',
  databaseType: 'ORACLE' as DatabaseEndpointType,
  exportToolBinary: '',
  lightningBinary: '',
  notes: ''
})

const activeMeta = computed(() => sourceCatalogMap[form.databaseType as keyof typeof sourceCatalogMap])

watch(
  () => props.managedToolPaths,
  () => {
    if (!form.lightningBinary) {
      form.lightningBinary = props.managedToolPaths.tidbLightningBinary
    }
  },
  { immediate: true, deep: true }
)

watch(
  () => form.databaseType,
  databaseType => {
    if (!form.exportToolBinary) {
      form.exportToolBinary = defaultExportBinary(databaseType)
    }
    if (!form.lightningBinary) {
      form.lightningBinary = props.managedToolPaths.tidbLightningBinary
    }
  },
  { immediate: true }
)

function defaultExportBinary(databaseType: DatabaseEndpointType): string {
  switch (databaseType) {
    case 'ORACLE':
      return props.managedToolPaths.sqluldr2Binary
    case 'MYSQL':
      return props.managedToolPaths.dumplingBinary
    case 'MARIADB':
      return 'mariadb-dump'
    case 'POSTGRESQL':
      return 'psql'
    case 'SQLSERVER':
      return props.managedToolPaths.bcpBinary
    case 'DB2':
      return 'db2'
    case 'HANA':
      return 'hdbsql'
    case 'MONGODB':
      return 'mongoexport'
    case 'CSV':
    case 'TIDB':
      return ''
  }
}

function resetForm() {
  form.editingId = null
  form.name = ''
  form.databaseType = 'ORACLE'
  form.exportToolBinary = defaultExportBinary('ORACLE')
  form.lightningBinary = props.managedToolPaths.tidbLightningBinary
  form.notes = ''
}

function editToolConfig(config: ToolConfig) {
  form.editingId = config.id
  form.name = config.name
  form.databaseType = config.databaseType
  form.exportToolBinary = config.exportToolBinary || defaultExportBinary(config.databaseType)
  form.lightningBinary = config.lightningBinary || props.managedToolPaths.tidbLightningBinary
  form.notes = config.notes || ''
}

function save() {
  emit('saveToolConfig', form.editingId, {
    name: form.name,
    databaseType: form.databaseType,
    exportToolBinary: form.exportToolBinary || null,
    lightningBinary: form.lightningBinary || null,
    notes: form.notes || null
  })
  resetForm()
}
</script>

<template>
  <section class="content-stack">
    <section class="panel">
      <div class="panel-header">
        <div>
          <p class="eyebrow">Step 03</p>
          <h2>配置数据库工具目录</h2>
          <p class="guide-text">按数据源类型维护全量导出工具和 TiDB Lightning 路径。默认路径会优先指向项目内 `vendor/tools/...`，也支持覆盖为你部署机上的绝对路径。</p>
        </div>
        <button class="ghost" @click="resetForm">新建工具配置</button>
      </div>

      <div class="guide-grid">
        <article class="guide-card">
          <h3>默认工具路径</h3>
          <div class="log-list">
            <div class="log-item">
              <strong>TiDB Lightning</strong>
              <span>{{ managedToolPaths.tidbLightningBinary }}</span>
            </div>
            <div class="log-item">
              <strong>Dumpling</strong>
              <span>{{ managedToolPaths.dumplingBinary }}</span>
            </div>
          <div class="log-item">
            <strong>SQLULDR2</strong>
            <span>{{ managedToolPaths.sqluldr2Binary }}</span>
          </div>
          <div class="log-item">
            <strong>BCP</strong>
            <span>{{ managedToolPaths.bcpBinary }}</span>
          </div>
          <div class="log-item">
            <strong>SQLCMD</strong>
            <span>{{ managedToolPaths.sqlcmdBinary }}</span>
          </div>
          </div>
        </article>

        <article class="guide-card">
          <h3>配置规则</h3>
          <div class="guide-list">
            <div class="log-item"><span>Oracle 全量优先配置 `sqluldr2`。</span></div>
            <div class="log-item"><span>MySQL 全量优先配置 `dumpling`。</span></div>
            <div class="log-item"><span>SQL Server 默认建议配置 `bcp`，`sqlcmd` 作为可选兜底工具。</span></div>
            <div class="log-item"><span>所有全量导入到 TiDB 的任务都会复用这里配置的 `tidb-lightning`。</span></div>
          </div>
        </article>
      </div>
    </section>

    <section class="section-grid">
      <section class="panel">
        <div class="panel-header">
          <h2>工具配置表单</h2>
          <span class="muted">按源类型选择对应工具</span>
        </div>

        <div class="source-picker-grid">
          <button
            v-for="type in databaseTypes"
            :key="type"
            class="source-picker-card"
            :class="{ active: form.databaseType === type }"
            @click="form.databaseType = type"
          >
            <div class="source-card-header">
              <div
                class="source-logo"
                :style="type === 'TIDB'
                  ? { '--logo-accent': '#1272d2', '--logo-surface': 'rgba(18,114,210,0.12)' }
                  : { '--logo-accent': sourceCatalogMap[type as keyof typeof sourceCatalogMap].accent, '--logo-surface': sourceCatalogMap[type as keyof typeof sourceCatalogMap].surface }"
              >
                <span>{{ type === 'TIDB' ? 'Ti' : sourceCatalogMap[type as keyof typeof sourceCatalogMap].logoText }}</span>
              </div>
              <div>
                <strong>{{ type === 'TIDB' ? 'TiDB' : sourceCatalogMap[type as keyof typeof sourceCatalogMap].label }}</strong>
                <small>{{ type }}</small>
              </div>
            </div>
          </button>
        </div>

        <div class="form-grid profile-form">
          <label>
            <span>配置名称</span>
            <input v-model="form.name" placeholder="例如：oracle-prod-tools" />
          </label>
          <label>
            <span>数据源类型</span>
            <input :value="form.databaseType" disabled />
          </label>
          <label class="wide">
            <span>全量工具路径 / 命令</span>
            <input v-model="form.exportToolBinary" :placeholder="defaultExportBinary(form.databaseType)" />
          </label>
          <label class="wide">
            <span>TiDB Lightning 路径</span>
            <input v-model="form.lightningBinary" :placeholder="managedToolPaths.tidbLightningBinary" />
          </label>
          <label class="wide">
            <span>备注</span>
            <textarea v-model="form.notes" rows="3" :placeholder="activeMeta ? activeMeta.fullNote : '补充该工具的部署目录、版本或执行注意事项'" />
          </label>
        </div>

        <div class="actions">
          <button class="primary" @click="save">{{ form.editingId ? '更新工具配置' : '保存工具配置' }}</button>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <h2>已保存工具配置</h2>
          <span class="muted">{{ toolConfigs.length }} 条</span>
        </div>
        <div class="table" v-if="toolConfigs.length">
          <div class="table-head simple-table">
            <span>名称</span>
            <span>类型</span>
            <span>全量工具</span>
            <span>Lightning</span>
            <span>操作</span>
          </div>
          <div v-for="config in toolConfigs" :key="config.id" class="table-row simple-table">
            <span><strong>{{ config.name }}</strong></span>
            <span>{{ config.databaseType }}</span>
            <span class="truncate-cell">{{ config.exportToolBinary || '-' }}</span>
            <span class="truncate-cell">{{ config.lightningBinary || '-' }}</span>
            <span class="actions">
              <button class="ghost" @click="editToolConfig(config)">编辑</button>
            </span>
          </div>
        </div>
        <div v-else class="empty-state compact">
          <strong>暂无工具配置</strong>
          <p>先根据不同数据库类型保存工具目录，后面的数据同步任务会直接带入这些路径。</p>
        </div>
      </section>
    </section>
  </section>
</template>
