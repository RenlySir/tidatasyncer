export function formatBytes(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '-'
  }
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

export function formatLag(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '-'
  }
  if (value < 1000) {
    return `${value} ms`
  }
  const seconds = value / 1000
  if (seconds < 60) {
    return `${seconds.toFixed(seconds >= 10 ? 0 : 1)} s`
  }
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = Math.round(seconds % 60)
  return `${minutes}m ${remainingSeconds}s`
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString()
}

export function formatSyncMode(value: string): string {
  switch (value) {
    case 'FULL_ONLY':
      return '仅全量'
    case 'INCREMENTAL_ONLY':
      return '仅增量'
    case 'FULL_AND_INCREMENTAL':
      return '全量 + 增量'
    default:
      return value
  }
}

export function formatJobStatus(value: string): string {
  switch (value) {
    case 'DRAFT':
      return '草稿'
    case 'RUNNING':
      return '运行中'
    case 'FAILED':
      return '失败'
    case 'COMPLETED':
      return '已完成'
    case 'STOPPED':
      return '已停止'
    default:
      return value
  }
}

export function formatJobPhase(value: string): string {
  switch (value) {
    case 'CREATED':
      return '已创建'
    case 'VALIDATING':
      return '校验配置'
    case 'EXPORTING_FULL':
      return '全量导出'
    case 'IMPORTING_FULL':
      return '全量导入'
    case 'BUFFERING_INCREMENTAL':
      return '缓存增量'
    case 'REPLAYING_INCREMENTAL_BUFFER':
      return '回放增量缓存'
    case 'STARTING_INCREMENTAL':
      return '启动增量'
    case 'RUNNING_INCREMENTAL':
      return '增量同步'
    case 'COMPLETED':
      return '已完成'
    case 'FAILED':
      return '执行失败'
    case 'STOPPED':
      return '已停止'
    default:
      return value
  }
}

export function toDisplayPercent(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '0%'
  }
  return `${Math.max(0, Math.min(100, value))}%`
}

export function statusTone(value: string): 'neutral' | 'success' | 'warn' | 'error' | 'info' {
  switch (value) {
    case 'COMPLETED':
    case 'RUNNING':
    case 'RUNNING_INCREMENTAL':
      return value === 'RUNNING' || value === 'RUNNING_INCREMENTAL' ? 'info' : 'success'
    case 'FAILED':
      return 'error'
    case 'STOPPED':
    case 'REVIEW_REQUIRED':
      return 'warn'
    default:
      return 'neutral'
  }
}
