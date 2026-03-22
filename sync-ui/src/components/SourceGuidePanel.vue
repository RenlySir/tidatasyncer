<script setup lang="ts">
import { sourceCatalog } from '../sourceCatalog'

withDefaults(defineProps<{
  compact?: boolean
}>(), {
  compact: false
})

const implementationTips = [
  '全量任务统一走“源端工具导出 + TiDB Lightning 导入”链路，优先确保 UTF-8、文件命名和分片规则一致。',
  '增量任务统一走“数据库日志 / 变更流解析”链路，优先对齐 Debezium 官方 connector 参数和日志权限。',
  '全量 + 增量模式优先选择能记录导出前位点的数据源，避免导入完成后出现重复或遗漏。',
  '监控中心重点关注全量导表进度、导入表数、导入数据量、最新日志位点和同步延迟。'
]
</script>

<template>
  <section class="content-stack">
    <section class="panel">
      <div class="panel-header">
        <div>
          <p class="eyebrow">{{ compact ? '辅助说明' : 'Step 08' }}</p>
          <h2>{{ compact ? '数据源工具说明' : '数据源与工具说明' }}</h2>
        </div>
        <span class="muted">统一梳理全量工具、增量 connector 和识别 logo</span>
      </div>
      <div class="source-grid" :class="{ 'compact-source-grid': compact }">
        <article v-for="item in sourceCatalog" :key="item.type" class="guide-card source-guide-card">
          <div class="source-card-header">
            <div class="source-logo" :style="{ '--logo-accent': item.accent, '--logo-surface': item.surface }">
              <span>{{ item.logoText }}</span>
            </div>
            <div>
              <h3>{{ item.label }}</h3>
              <small>{{ item.vendor }}</small>
            </div>
          </div>
          <p class="guide-text">{{ item.summary }}</p>
          <p class="guide-text"><strong>全量：</strong>{{ item.fullTool }}</p>
          <p class="guide-text">{{ item.fullNote }}</p>
          <p class="guide-text"><strong>增量：</strong>{{ item.incrementalTool }}</p>
          <p class="guide-text">{{ item.incrementalNote }}</p>
        </article>
      </div>
    </section>

    <section v-if="!compact" class="panel">
      <div class="panel-header">
        <h2>实施提示</h2>
        <span class="muted">按统一逻辑组织全量、增量和监控</span>
      </div>
      <div class="guide-list">
        <div v-for="tip in implementationTips" :key="tip" class="log-item">
          <span>{{ tip }}</span>
        </div>
      </div>
    </section>
  </section>
</template>
