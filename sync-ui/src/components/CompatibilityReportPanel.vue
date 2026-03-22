<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { sourceCatalogMap } from '../sourceCatalog'
import type { CompatibilityReport, CompatibilityReportUpsert, ConnectionProfile } from '../types'

const props = defineProps<{
  sourceProfiles: ConnectionProfile[]
  targetProfiles: ConnectionProfile[]
  reports: CompatibilityReport[]
}>()

const emit = defineEmits<{
  saveReport: [id: number | null, payload: CompatibilityReportUpsert]
  executeReport: [id: number]
}>

const selectedReportId = ref<number | null>(null)
const form = reactive({
  editingId: null as number | null,
  name: '',
  sourceProfileId: 0,
  targetProfileId: 0
})

const activeReport = computed(() => props.reports.find(item => item.id === selectedReportId.value) ?? null)

function resetForm() {
  form.editingId = null
  form.name = ''
  form.sourceProfileId = props.sourceProfiles[0]?.id ?? 0
  form.targetProfileId = props.targetProfiles[0]?.id ?? 0
}

function editReport(report: CompatibilityReport) {
  selectedReportId.value = report.id
  form.editingId = report.id
  form.name = report.name
  form.sourceProfileId = report.sourceProfileId
  form.targetProfileId = report.targetProfileId
}

function selectReport(report: CompatibilityReport) {
  selectedReportId.value = report.id
}

function sourceLabel(profileId: number): string {
  const profile = props.sourceProfiles.find(item => item.id === profileId)
  return profile ? profile.name : `#${profileId}`
}

function targetLabel(profileId: number): string {
  const profile = props.targetProfiles.find(item => item.id === profileId)
  return profile ? profile.name : `#${profileId}`
}

function sourceType(profileId: number): string {
  const profile = props.sourceProfiles.find(item => item.id === profileId)
  return profile?.databaseType || 'UNKNOWN'
}

function save() {
  emit('saveReport', form.editingId, {
    name: form.name,
    sourceProfileId: form.sourceProfileId,
    targetProfileId: form.targetProfileId
  })
  if (!form.editingId) {
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
          <p class="eyebrow">Step 02</p>
          <h2>对象兼容性检测报告</h2>
          <p class="guide-text">在创建完数据源和目标后，可先执行对象兼容性检查。系统会参考 MySQL/TiDB 兼容性扫描类项目的思路，检查表、列类型、视图、触发器、过程、函数、序列等对象，并输出 TiDB 兼容性报告。</p>
        </div>
        <button class="ghost" @click="resetForm">新建检测任务</button>
      </div>

      <div class="guide-grid">
        <article class="guide-card">
          <h3>检测范围</h3>
          <div class="guide-list">
            <div class="log-item"><span>检查表和字段类型是否可直接映射到 TiDB。</span></div>
            <div class="log-item"><span>统计视图、触发器、存储过程、函数、序列等对象并提示人工处理。</span></div>
            <div class="log-item"><span>输出 Markdown + HTML 报告文件，便于评审、留档和页面直观预览。</span></div>
          </div>
        </article>
        <article class="guide-card">
          <h3>适用数据源</h3>
          <div class="guide-list">
            <div class="log-item"><span>Oracle、MySQL、MariaDB、SQL Server、PostgreSQL 优先支持。</span></div>
            <div class="log-item"><span>CSV 不参与对象兼容性检测。</span></div>
            <div class="log-item"><span>目标端当前固定为 TiDB。</span></div>
          </div>
        </article>
      </div>
    </section>

    <section class="section-grid">
      <section class="panel">
        <div class="panel-header">
          <h2>检测任务表单</h2>
          <span class="muted">建议在结构同步前先执行</span>
        </div>
        <div class="form-grid profile-form">
          <label>
            <span>任务名称</span>
            <input v-model="form.name" placeholder="例如：oracle-prod-compatibility" />
          </label>
          <label>
            <span>数据源</span>
            <select v-model.number="form.sourceProfileId">
              <option v-for="profile in sourceProfiles.filter(item => item.databaseType !== 'CSV')" :key="profile.id" :value="profile.id">
                {{ profile.name }} / {{ profile.databaseType }}
              </option>
            </select>
          </label>
          <label>
            <span>目标端</span>
            <select v-model.number="form.targetProfileId">
              <option v-for="profile in targetProfiles" :key="profile.id" :value="profile.id">
                {{ profile.name }} / {{ profile.databaseName }}
              </option>
            </select>
          </label>
        </div>
        <div class="actions">
          <button class="primary" @click="save">{{ form.editingId ? '更新检测任务' : '保存检测任务' }}</button>
          <button v-if="form.editingId" class="ghost" @click="emit('executeReport', form.editingId)">执行检测</button>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <h2>检测任务列表</h2>
          <span class="muted">{{ reports.length }} 条</span>
        </div>
        <div class="table" v-if="reports.length">
          <div class="table-head compatibility-table">
            <span>任务</span>
            <span>源端</span>
            <span>目标端</span>
            <span>状态</span>
            <span>操作</span>
          </div>
          <div
            v-for="report in reports"
            :key="report.id"
            class="table-row compatibility-table"
            :class="{ active: selectedReportId === report.id }"
            @click="selectReport(report)"
          >
            <span><strong>{{ report.name }}</strong></span>
            <span class="source-card-header compact-source-header">
              <div
                v-if="sourceCatalogMap[sourceType(report.sourceProfileId) as keyof typeof sourceCatalogMap]"
                class="source-logo mini-logo"
                :style="{
                  '--logo-accent': sourceCatalogMap[sourceType(report.sourceProfileId) as keyof typeof sourceCatalogMap].accent,
                  '--logo-surface': sourceCatalogMap[sourceType(report.sourceProfileId) as keyof typeof sourceCatalogMap].surface
                }"
              >
                <span>{{ sourceCatalogMap[sourceType(report.sourceProfileId) as keyof typeof sourceCatalogMap].logoText }}</span>
              </div>
              <span>{{ sourceLabel(report.sourceProfileId) }}</span>
            </span>
            <span>{{ targetLabel(report.targetProfileId) }}</span>
            <span>{{ report.status }}</span>
            <span class="actions">
              <button class="ghost" @click.stop="editReport(report)">编辑</button>
              <button class="primary" @click.stop="emit('executeReport', report.id)">执行</button>
            </span>
          </div>
        </div>
        <div v-else class="empty-state compact">
          <strong>暂无检测任务</strong>
          <p>先保存一条检测任务，在结构同步和数据同步前先跑兼容性报告。</p>
        </div>
      </section>
    </section>

    <section v-if="activeReport" class="section-grid narrow-side">
      <section class="panel">
        <div class="panel-header">
          <h2>检测摘要</h2>
          <span class="muted">{{ activeReport.status }}</span>
        </div>
        <div class="dashboard-grid">
          <div class="metric-card compact-card">
            <span class="metric-label">总问题数</span>
            <strong>{{ activeReport.summary.totalFindings }}</strong>
          </div>
          <div class="metric-card compact-card">
            <span class="metric-label">不兼容</span>
            <strong>{{ activeReport.summary.incompatibleCount }}</strong>
          </div>
          <div class="metric-card compact-card">
            <span class="metric-label">需人工评估</span>
            <strong>{{ activeReport.summary.partialCount }}</strong>
          </div>
          <div class="metric-card compact-card">
            <span class="metric-label">表数</span>
            <strong>{{ activeReport.summary.tableCount }}</strong>
          </div>
          <div class="metric-card compact-card">
            <span class="metric-label">视图</span>
            <strong>{{ activeReport.summary.viewCount }}</strong>
          </div>
          <div class="metric-card compact-card">
            <span class="metric-label">触发器</span>
            <strong>{{ activeReport.summary.triggerCount }}</strong>
          </div>
          <div class="metric-card compact-card">
            <span class="metric-label">过程</span>
            <strong>{{ activeReport.summary.procedureCount }}</strong>
          </div>
          <div class="metric-card compact-card">
            <span class="metric-label">序列</span>
            <strong>{{ activeReport.summary.sequenceCount }}</strong>
          </div>
        </div>
        <div class="sub-panel">
          <div class="sub-panel-header">
            <h3>执行结果</h3>
          </div>
          <div class="log-list">
            <div class="log-item">
              <strong>说明</strong>
              <span>{{ activeReport.lastMessage || '-' }}</span>
            </div>
            <div class="log-item" v-if="activeReport.reportPath">
              <strong>报告文件</strong>
              <span>{{ activeReport.reportPath }}</span>
            </div>
            <div class="log-item" v-if="activeReport.reportHtmlPath">
              <strong>HTML 报告</strong>
              <span>{{ activeReport.reportHtmlPath }}</span>
            </div>
          </div>
        </div>
        <div class="sub-panel">
          <div class="sub-panel-header">
            <h3>对象问题清单</h3>
          </div>
          <div class="log-list" v-if="activeReport.findings.length">
            <div v-for="finding in activeReport.findings" :key="`${finding.objectType}-${finding.objectName}-${finding.message}`" class="log-item">
              <strong>[{{ finding.severity }}] {{ finding.objectType }} {{ finding.objectName }}</strong>
              <span>{{ finding.message }}</span>
              <small class="muted">{{ finding.suggestion }}</small>
            </div>
          </div>
          <div v-else class="empty-state compact">
            <strong>暂无问题明细</strong>
            <p>执行检测后，这里会列出字段类型和对象级兼容性问题。</p>
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <h2>报告预览</h2>
          <span class="muted">支持 Markdown 和 HTML 两种产物</span>
        </div>
        <div class="guide-grid">
          <div class="runtime-log-viewer">
            <pre><code>{{ activeReport.reportMarkdown || '尚未生成 Markdown 报告' }}</code></pre>
          </div>
          <iframe v-if="activeReport.reportHtml" class="html-report-preview-frame" :srcdoc="activeReport.reportHtml"></iframe>
          <div v-else class="empty-state compact">
            <strong>尚未生成 HTML 报告</strong>
            <p>执行检测后，这里会展示 HTML 报告预览。</p>
          </div>
        </div>
      </section>
    </section>
  </section>
</template>
