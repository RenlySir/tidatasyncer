<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { ConnectionProfile, SchemaSyncTask, SchemaSyncTaskUpsert } from '../types'

const props = defineProps<{
  sourceProfiles: ConnectionProfile[]
  targetProfiles: ConnectionProfile[]
  schemaTasks: SchemaSyncTask[]
}>()

const emit = defineEmits<{
  saveSchemaTask: [id: number | null, payload: SchemaSyncTaskUpsert]
  executeSchemaTask: [id: number]
}>()

const selectedTaskId = ref<number | null>(null)
const form = reactive({
  editingId: null as number | null,
  name: '',
  sourceProfileId: 0,
  targetProfileId: 0,
  tableSelectionMode: 'DATABASE_ALL' as 'DATABASE_ALL' | 'SELECTED_TABLES',
  selectedTablesText: '',
  overrideMappingsText: ''
})

const activeTask = computed(() => props.schemaTasks.find(item => item.id === selectedTaskId.value) ?? null)

function parseLines(value: string): string[] {
  return value
    .split(/\r?\n|,/)
    .map(item => item.trim())
    .filter(Boolean)
}

function parseOverrideMappings(value: string): Record<string, string> {
  const entries = value
    .split(/\r?\n/)
    .map(item => item.trim())
    .filter(Boolean)
    .map(item => {
      const [key, ...rest] = item.split('=')
      return [key?.trim() || '', rest.join('=').trim()]
    })
    .filter(([key, targetType]) => key && targetType)
  return Object.fromEntries(entries)
}

function formatOverrideMappings(value: Record<string, string>): string {
  return Object.entries(value).map(([key, targetType]) => `${key}=${targetType}`).join('\n')
}

function resetForm() {
  form.editingId = null
  form.name = ''
  form.sourceProfileId = props.sourceProfiles[0]?.id ?? 0
  form.targetProfileId = props.targetProfiles[0]?.id ?? 0
  form.tableSelectionMode = 'DATABASE_ALL'
  form.selectedTablesText = ''
  form.overrideMappingsText = ''
}

function editTask(task: SchemaSyncTask) {
  selectedTaskId.value = task.id
  form.editingId = task.id
  form.name = task.name
  form.sourceProfileId = task.sourceProfileId
  form.targetProfileId = task.targetProfileId
  form.tableSelectionMode = task.tableSelectionMode
  form.selectedTablesText = task.selectedTables.join('\n')
  form.overrideMappingsText = formatOverrideMappings(task.overrideMappings)
}

function selectTask(task: SchemaSyncTask) {
  selectedTaskId.value = task.id
}

function save() {
  emit('saveSchemaTask', form.editingId, {
    name: form.name,
    sourceProfileId: form.sourceProfileId,
    targetProfileId: form.targetProfileId,
    tableSelectionMode: form.tableSelectionMode,
    selectedTables: form.tableSelectionMode === 'DATABASE_ALL' ? [] : parseLines(form.selectedTablesText),
    overrideMappings: parseOverrideMappings(form.overrideMappingsText)
  })
  if (form.editingId === null) {
    resetForm()
  }
}

resetForm()
</script>

<template>
  <section class="content-stack">
    <section class="panel">
      <div class="panel-header">
        <div>
          <p class="eyebrow">Step 04</p>
          <h2>创建表结构同步任务</h2>
          <p class="guide-text">选择已保存的数据源和 TiDB 目标，先生成或执行目标端表结构。Oracle 会默认做字段类型到 TiDB 的映射，未自动支持的类型会写入文件并在页面展示，可修改 override 后重新执行。</p>
        </div>
        <button class="ghost" @click="resetForm">新建结构任务</button>
      </div>
    </section>

    <section class="section-grid">
      <section class="panel">
        <div class="panel-header">
          <h2>结构任务表单</h2>
          <span class="muted">先生成 DDL，再按需要重跑</span>
        </div>

        <div class="form-grid profile-form">
          <label>
            <span>任务名称</span>
            <input v-model="form.name" placeholder="例如：oracle-to-tidb-schema" />
          </label>
          <label>
            <span>源端连接</span>
            <select v-model.number="form.sourceProfileId">
              <option v-for="profile in sourceProfiles" :key="profile.id" :value="profile.id">{{ profile.name }} / {{ profile.databaseType }}</option>
            </select>
          </label>
          <label>
            <span>目标连接</span>
            <select v-model.number="form.targetProfileId">
              <option v-for="profile in targetProfiles" :key="profile.id" :value="profile.id">{{ profile.name }} / {{ profile.databaseName }}</option>
            </select>
          </label>
          <label>
            <span>表范围</span>
            <select v-model="form.tableSelectionMode">
              <option value="DATABASE_ALL">整库同步</option>
              <option value="SELECTED_TABLES">指定表同步</option>
            </select>
          </label>
          <label class="wide" v-if="form.tableSelectionMode === 'SELECTED_TABLES'">
            <span>指定表列表</span>
            <textarea v-model="form.selectedTablesText" rows="4" placeholder="每行一个，支持 table 或 schema.table" />
          </label>
          <label class="wide">
            <span>类型覆盖 override</span>
            <textarea v-model="form.overrideMappingsText" rows="4" placeholder="每行一个，格式 table.column=TARGET_TYPE 或 SOURCE_TYPE=TARGET_TYPE" />
          </label>
        </div>

        <div class="actions">
          <button class="primary" @click="save">{{ form.editingId ? '更新结构任务' : '保存结构任务' }}</button>
          <button v-if="form.editingId" class="ghost" @click="emit('executeSchemaTask', form.editingId)">执行结构同步</button>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <h2>结构任务列表</h2>
          <span class="muted">{{ schemaTasks.length }} 条</span>
        </div>
        <div class="table" v-if="schemaTasks.length">
          <div class="table-head schema-table">
            <span>任务</span>
            <span>源 / 目标</span>
            <span>表范围</span>
            <span>状态</span>
            <span>操作</span>
          </div>
          <div
            v-for="task in schemaTasks"
            :key="task.id"
            class="table-row schema-table"
            :class="{ active: selectedTaskId === task.id }"
            @click="selectTask(task)"
          >
            <span><strong>{{ task.name }}</strong></span>
            <span>{{ task.sourceProfileId }} -> {{ task.targetProfileId }}</span>
            <span>{{ task.tableSelectionMode }}</span>
            <span>{{ task.status }}</span>
            <span class="actions">
              <button class="ghost" @click.stop="editTask(task)">编辑</button>
              <button class="primary" @click.stop="emit('executeSchemaTask', task.id)">执行</button>
            </span>
          </div>
        </div>
        <div v-else class="empty-state compact">
          <strong>暂无表结构任务</strong>
          <p>先选择源端和 TiDB 目标，生成第一条表结构同步任务。</p>
        </div>
      </section>
    </section>

    <section v-if="activeTask" class="section-grid narrow-side">
      <section class="panel">
        <div class="panel-header">
          <h2>结构任务详情</h2>
          <span class="muted">{{ activeTask.status }}</span>
        </div>
        <div class="log-list">
          <div class="log-item">
            <strong>执行说明</strong>
            <span>{{ activeTask.lastMessage || '-' }}</span>
          </div>
          <div class="log-item" v-if="activeTask.generatedDdlPath">
            <strong>DDL 文件</strong>
            <span>{{ activeTask.generatedDdlPath }}</span>
          </div>
          <div class="log-item" v-if="activeTask.unsupportedItemsPath">
            <strong>不兼容类型文件</strong>
            <span>{{ activeTask.unsupportedItemsPath }}</span>
          </div>
        </div>

        <div class="sub-panel" v-if="activeTask.unsupportedItems.length">
          <div class="sub-panel-header">
            <h3>待处理字段类型</h3>
          </div>
          <div class="log-list">
            <div v-for="item in activeTask.unsupportedItems" :key="`${item.tableName}.${item.columnName}`" class="log-item">
              <strong>{{ item.tableName }}.{{ item.columnName }}</strong>
              <span>{{ item.sourceType }} -> {{ item.suggestedTargetType }}</span>
              <small class="muted">{{ item.reason }}</small>
            </div>
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <h2>生成的 DDL</h2>
          <span class="muted">可复制 review 后再次执行</span>
        </div>
        <div class="runtime-log-viewer">
          <pre><code>{{ activeTask.generatedDdl || '尚未生成 DDL' }}</code></pre>
        </div>
      </section>
    </section>
  </section>
</template>
