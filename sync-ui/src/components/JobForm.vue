<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import type { SourceDatabaseType, SyncJob, SyncJobDefinition, SyncMode, TableMapping } from '../types'

const props = defineProps<{
  job: SyncJob | null
  definition: SyncJobDefinition | null
}>()

const emit = defineEmits<{
  save: [name: string, definition: SyncJobDefinition]
}>()

const databaseOptions: SourceDatabaseType[] = ['MYSQL', 'ORACLE', 'SQLSERVER', 'POSTGRESQL', 'HANA']
const modeOptions: SyncMode[] = ['FULL_ONLY', 'INCREMENTAL_ONLY', 'FULL_AND_INCREMENTAL']

function emptyMapping(): TableMapping {
  return {
    sourceCatalog: '',
    sourceSchema: '',
    sourceTable: '',
    targetDatabase: '',
    targetTable: '',
    primaryKeys: [],
    incrementalColumn: '',
    includedColumns: [],
    columnMappings: {}
  }
}

function emptyDefinition(): SyncJobDefinition {
  return {
    syncMode: 'FULL_AND_INCREMENTAL',
    source: {
      databaseType: 'MYSQL',
      host: '127.0.0.1',
      port: 3306,
      databaseName: '',
      schemaName: '',
      username: '',
      password: '',
      jdbcUrl: '',
      commandTemplate: ''
    },
    target: {
      host: '127.0.0.1',
      port: 4000,
      databaseName: '',
      username: 'root',
      password: '',
      jdbcUrl: 'jdbc:mysql://127.0.0.1:4000/test?useUnicode=true&characterEncoding=utf8&rewriteBatchedStatements=true',
      lightningBinary: 'tidb-lightning'
    },
    tableMappings: [emptyMapping()],
    fullLoad: {
      exportToolBinary: '',
      exportBaseDir: './work/export',
      fetchSize: 1000,
      parallelism: 1,
      additionalProperties: {}
    },
    incremental: {
      serverName: 'sync_server',
      slotName: 'sync_slot',
      publicationName: 'sync_pub',
      offsetStoragePath: './work/offsets/offset.dat',
      pollingIntervalSeconds: 5,
      batchSize: 500,
      additionalProperties: {}
    }
  }
}

const state = reactive({
  name: '',
  definition: emptyDefinition()
})

watch(
  () => props.job,
  () => {
    state.name = props.job?.name ?? ''
    state.definition = props.definition ? structuredClone(props.definition) : emptyDefinition()
  },
  { immediate: true }
)

const canEditIncremental = computed(() => state.definition.syncMode !== 'FULL_ONLY')

function addMapping() {
  state.definition.tableMappings.push(emptyMapping())
}

function removeMapping(index: number) {
  state.definition.tableMappings.splice(index, 1)
  if (!state.definition.tableMappings.length) {
    addMapping()
  }
}

function submit() {
  emit('save', state.name, structuredClone(state.definition))
}
</script>

<template>
  <section class="panel form-panel">
    <div class="panel-header">
      <h2>任务配置</h2>
      <button class="primary" @click="submit">保存任务</button>
    </div>

    <div class="form-grid">
      <label>
        <span>任务名称</span>
        <input v-model="state.name" placeholder="例如：mysql-to-tidb-order-sync" />
      </label>
      <label>
        <span>同步模式</span>
        <select v-model="state.definition.syncMode">
          <option v-for="mode in modeOptions" :key="mode" :value="mode">{{ mode }}</option>
        </select>
      </label>
      <label>
        <span>源库类型</span>
        <select v-model="state.definition.source.databaseType">
          <option v-for="item in databaseOptions" :key="item" :value="item">{{ item }}</option>
        </select>
      </label>
      <label>
        <span>源端主机</span>
        <input v-model="state.definition.source.host" />
      </label>
      <label>
        <span>源端端口</span>
        <input v-model.number="state.definition.source.port" type="number" />
      </label>
      <label>
        <span>源端数据库</span>
        <input v-model="state.definition.source.databaseName" />
      </label>
      <label>
        <span>源端 Schema</span>
        <input v-model="state.definition.source.schemaName" />
      </label>
      <label>
        <span>源端用户名</span>
        <input v-model="state.definition.source.username" />
      </label>
      <label>
        <span>源端密码</span>
        <input v-model="state.definition.source.password" type="password" />
      </label>
      <label class="wide">
        <span>源端 JDBC URL</span>
        <input v-model="state.definition.source.jdbcUrl" />
      </label>
      <label class="wide">
        <span>全量导出命令模板</span>
        <textarea
          v-model="state.definition.source.commandTemplate"
          rows="2"
          placeholder="可覆盖默认命令，支持 ${host} ${port} ${database} ${schema} ${table} ${username} ${password} ${file}"
        />
      </label>
      <label>
        <span>TiDB 主机</span>
        <input v-model="state.definition.target.host" />
      </label>
      <label>
        <span>TiDB 端口</span>
        <input v-model.number="state.definition.target.port" type="number" />
      </label>
      <label>
        <span>TiDB 用户名</span>
        <input v-model="state.definition.target.username" />
      </label>
      <label>
        <span>TiDB 密码</span>
        <input v-model="state.definition.target.password" type="password" />
      </label>
      <label class="wide">
        <span>TiDB JDBC URL</span>
        <input v-model="state.definition.target.jdbcUrl" />
      </label>
      <label class="wide">
        <span>Lightning 二进制</span>
        <input v-model="state.definition.target.lightningBinary" />
      </label>
    </div>

    <div class="sub-panel">
      <div class="sub-panel-header">
        <h3>表映射</h3>
        <button @click="addMapping">新增表</button>
      </div>

      <div v-for="(mapping, index) in state.definition.tableMappings" :key="index" class="mapping-card">
        <div class="mapping-grid">
          <label>
            <span>源目录</span>
            <input v-model="mapping.sourceCatalog" />
          </label>
          <label>
            <span>源 Schema</span>
            <input v-model="mapping.sourceSchema" />
          </label>
          <label>
            <span>源表</span>
            <input v-model="mapping.sourceTable" />
          </label>
          <label>
            <span>目标库</span>
            <input v-model="mapping.targetDatabase" />
          </label>
          <label>
            <span>目标表</span>
            <input v-model="mapping.targetTable" />
          </label>
          <label>
            <span>主键</span>
            <input
              :value="mapping.primaryKeys.join(',')"
              @input="mapping.primaryKeys = String(($event.target as HTMLInputElement).value).split(',').map(v => v.trim()).filter(Boolean)"
              placeholder="id,tenant_id"
            />
          </label>
          <label v-if="canEditIncremental">
            <span>增量列</span>
            <input v-model="mapping.incrementalColumn" placeholder="HANA 建议填写更新时间列" />
          </label>
        </div>
        <button class="ghost danger" @click="removeMapping(index)">删除表映射</button>
      </div>
    </div>
  </section>
</template>
