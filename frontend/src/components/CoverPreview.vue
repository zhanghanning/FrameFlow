<script setup>
import { onBeforeUnmount, onMounted, ref } from "vue";
const item = ref(null),
  visible = ref(false),
  failed = ref(false),
  position = ref({});
let timer,
  fadeTimer,
  generation = 0;
function close(immediate = false) {
  generation++;
  clearTimeout(timer);
  clearTimeout(fadeTimer);
  visible.value = false;
  if (immediate) item.value = null;
  else
    fadeTimer = setTimeout(() => {
      item.value = null;
    }, 150);
}
function schedule(row, event) {
  close(true);
  if (event.pointerType === "touch" || !row.coverUrl) return;
  const target = event.currentTarget,
    current = generation;
  timer = setTimeout(() => {
    if (current !== generation || !target.isConnected) return;
    const rect = target.getBoundingClientRect(),
      margin = 12,
      gap = 14;
    const width = Math.min(420, window.innerWidth - 24),
      height = Math.min(360, window.innerHeight - 24);
    let left = rect.right + gap,
      top = rect.top;
    if (left + width > window.innerWidth - margin)
      left = rect.left - gap - width;
    if (left < margin) {
      left = rect.left + (rect.width - width) / 2;
      top = rect.top - gap - height;
    }
    position.value = {
      left: `${Math.max(margin, Math.min(left, window.innerWidth - width - margin))}px`,
      top: `${Math.max(margin, Math.min(top, window.innerHeight - height - margin))}px`,
      width: `${width}px`,
      height: `${height}px`,
    };
    failed.value = false;
    item.value = row;
    visible.value = true;
  }, 800);
}
function onEscape(event) {
  if (event.key === "Escape") close();
}
function dismiss() {
  close();
}
onMounted(() => {
  window.addEventListener("scroll", dismiss, true);
  window.addEventListener("resize", dismiss);
  window.addEventListener("blur", dismiss);
  window.addEventListener("keydown", onEscape);
});
onBeforeUnmount(() => {
  close(true);
  window.removeEventListener("scroll", dismiss, true);
  window.removeEventListener("resize", dismiss);
  window.removeEventListener("blur", dismiss);
  window.removeEventListener("keydown", onEscape);
});
defineExpose({ schedule, close });
</script>
<template>
  <Teleport to="body"
    ><div
      v-if="item"
      class="platform-cover-preview"
      :class="{ 'is-visible': visible }"
      :style="position"
      aria-hidden="true"
      data-testid="platform-cover-preview"
      :data-content-id="item.id"
    >
      <img
        v-if="!failed"
        :src="item.coverUrl"
        :alt="item.title"
        referrerpolicy="no-referrer"
        @error="failed = true"
      /><span v-else>封面暂不可用</span><strong>{{ item.title }}</strong>
    </div></Teleport
  >
</template>
