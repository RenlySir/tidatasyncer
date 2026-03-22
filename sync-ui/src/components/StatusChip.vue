<script setup lang="ts">
defineProps<{
  label: string
  tone?: 'neutral' | 'success' | 'warn' | 'error' | 'info'
}>()
</script>

<template>
  <span class="status-chip" :class="tone || 'neutral'">{{ label }}</span>
</template>
