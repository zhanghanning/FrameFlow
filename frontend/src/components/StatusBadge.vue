<script setup>
import { label } from "../lib/api";
defineProps({ value: String });
</script>
<template>
  <span class="status-badge" :class="value?.toLowerCase()"
    ><i></i>{{ label(value) }}</span
  >
</template>
