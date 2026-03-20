import type { DashboardOverview, SyncJob, SyncJobDefinition, SyncJobLog } from './types'

const headers = {
  'Content-Type': 'application/json'
}

export async function fetchOverview(): Promise<DashboardOverview> {
  const response = await fetch('/api/dashboard/overview')
  return await response.json()
}

export async function fetchJobs(): Promise<SyncJob[]> {
  const response = await fetch('/api/jobs')
  return await response.json()
}

export async function fetchJobLogs(id: number): Promise<SyncJobLog[]> {
  const response = await fetch(`/api/jobs/${id}/logs`)
  return await response.json()
}

export async function fetchJobDefinition(id: number): Promise<SyncJobDefinition> {
  const response = await fetch(`/api/jobs/${id}/definition`)
  const payload = await response.json()
  return payload.definition
}

export async function createJob(name: string, definition: SyncJobDefinition): Promise<SyncJob> {
  const response = await fetch('/api/jobs', {
    method: 'POST',
    headers,
    body: JSON.stringify({ name, definition })
  })
  return await response.json()
}

export async function updateJob(id: number, name: string, definition: SyncJobDefinition): Promise<SyncJob> {
  const response = await fetch(`/api/jobs/${id}`, {
    method: 'PUT',
    headers,
    body: JSON.stringify({ name, definition })
  })
  return await response.json()
}

export async function startJob(id: number): Promise<void> {
  await fetch(`/api/jobs/${id}/start`, { method: 'POST' })
}

export async function stopJob(id: number): Promise<void> {
  await fetch(`/api/jobs/${id}/stop`, { method: 'POST' })
}
