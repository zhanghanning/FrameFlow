<script setup>
import { onBeforeUnmount, ref, watch } from "vue";
import {
  Picture,
  VideoCamera,
  Headset,
  Document,
} from "@element-plus/icons-vue";
import { api } from "../lib/api";
const props = defineProps({ asset: Object });
const url = ref(""),
  loading = ref(false),
  error = ref("");
const icons = {
  IMAGE: Picture,
  VIDEO: VideoCamera,
  AUDIO: Headset,
  DOCUMENT: Document,
};
let controller,
  requestId = 0;
function release() {
  controller?.abort();
  if (url.value) URL.revokeObjectURL(url.value);
  url.value = "";
}
async function load() {
  const current = ++requestId;
  release();
  error.value = "";
  loading.value = false;
  if (!["IMAGE", "VIDEO"].includes(props.asset.kind)) return;
  const request = new AbortController();
  controller = request;
  loading.value = true;
  try {
    const media = await api.get(`/assets/${props.asset.id}/media`, {
      signal: request.signal,
      timeout: 120000,
    });
    if (request.signal.aborted || current !== requestId) return;
    if (media.posterUrl) {
      const blob = await api.get(media.posterUrl, {
        responseType: "blob",
        signal: request.signal,
      });
      if (request.signal.aborted || current !== requestId) return;
      url.value = URL.createObjectURL(blob);
    }
  } catch (e) {
    if (!request.signal.aborted && current === requestId)
      error.value = e.message;
  } finally {
    if (current === requestId) loading.value = false;
  }
}
watch(() => [props.asset.id, props.asset.kind], load, { immediate: true });
onBeforeUnmount(() => {
  requestId++;
  release();
});
</script>
<template>
  <img
    v-if="url"
    :src="url"
    :alt="asset.name"
    loading="lazy"
    data-testid="asset-thumbnail-image"
    :data-asset-id="asset.id"
  />
  <span
    v-else
    class="asset-thumbnail-placeholder"
    :data-loading="loading"
    :data-asset-id="asset.id"
    data-testid="asset-thumbnail-placeholder"
  >
    <el-icon><component :is="icons[asset.kind] || Document" /></el-icon>
    <small v-if="loading">正在准备封面</small>
    <button
      v-else-if="error"
      type="button"
      class="asset-thumbnail-retry"
      :title="error"
      @click.stop="load"
      @keydown.stop
    >
      重试封面
    </button>
  </span>
</template>
