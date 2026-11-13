<script setup>
defineProps({
  title: String,
  subtitle: String,
  eyebrow: { type: String, default: "FRAMEFLOW WORKSPACE" },
});
</script>
<template>
  <header class="page-heading">
    <div>
      <div class="eyebrow">{{ eyebrow }}</div>
      <h1>{{ title }}</h1>
      <p>{{ subtitle }}</p>
    </div>
    <div class="heading-actions"><slot /></div>
  </header>
</template>
