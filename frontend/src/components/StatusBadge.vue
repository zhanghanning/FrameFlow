<script setup>
import { label } from "../lib/api";
defineProps({ value: String });
</script>
<template>
  <span class="status-badge" :class="value?.toLowerCase()"
    ><i></i>{{ label(value) }}</span
  >
</template>

<style scoped>
.status-badge.cancelled { color: var(--muted); background: var(--paper); border: 1px solid var(--line); }
.status-badge.cancelled i { background: var(--muted); }
</style>
