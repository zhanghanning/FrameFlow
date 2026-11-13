<script setup>
defineProps({ message: String });
defineEmits(["retry"]);
</script>
<template>
  <div v-if="message" class="error-panel" role="alert">
    <span>{{ message }}</span
    ><el-button size="small" @click="$emit('retry')">重新加载</el-button>
  </div>
</template>
