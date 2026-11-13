<script setup>
import { onMounted, onUnmounted, ref, watch } from "vue";
import {
  Picture,
  VideoCamera,
  Headset,
  Document,
} from "@element-plus/icons-vue";
import { api } from "../lib/api";
const props = defineProps({ asset: Object }),
  url = ref(""),
  icons = {
    IMAGE: Picture,
    VIDEO: VideoCamera,
    AUDIO: Headset,
    DOCUMENT: Document,
  };
async function load() {
  if (url.value) URL.revokeObjectURL(url.value);
  url.value = "";
  if (
    props.asset.kind === "IMAGE" ||
    props.asset.mimeType?.startsWith("image/")
  ) {
    try {
      const blob = await api.get(`/assets/${props.asset.id}/file`, {
        responseType: "blob",
      });
      url.value = URL.createObjectURL(blob);
    } catch {}
  }
}
onMounted(load);
watch(() => props.asset.id, load);
onUnmounted(() => {
  if (url.value) URL.revokeObjectURL(url.value);
});
</script>
<template>
  <img v-if="url" :src="url" :alt="asset.name" loading="lazy" /><el-icon v-else
    ><component :is="icons[asset.kind] || Document"
  /></el-icon>
</template>
