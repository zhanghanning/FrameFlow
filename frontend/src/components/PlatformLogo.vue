<script setup>
import { computed } from "vue";
import {
  siBilibili,
  siTiktok,
  siKuaishou,
  siXiaohongshu,
  siYoutube,
  siInstagram,
} from "simple-icons";
const props = defineProps({ code: String, name: String });
// 抖音与 TikTok 共用音符品牌标记；这里只使用标记路径，平台仍是 DOUYIN。
const icons = {
  BILIBILI: siBilibili,
  DOUYIN: siTiktok,
  KUAISHOU: siKuaishou,
  XIAOHONGSHU: siXiaohongshu,
  YOUTUBE: siYoutube,
  INSTAGRAM: siInstagram,
};
const icon = computed(() => icons[props.code]);
</script>
<template>
  <span
    class="platform-logo"
    :class="code?.toLowerCase()"
    :aria-label="name || icon?.title"
    role="img"
    ><svg v-if="icon" viewBox="0 0 24 24" aria-hidden="true">
      <path :d="icon.path" /></svg
    ><svg v-else viewBox="0 0 24 24" aria-hidden="true">
      <rect
        x="3"
        y="5"
        width="18"
        height="14"
        rx="3"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
      />
      <path d="m10 9 6 3-6 3z" /></svg
  ></span>
</template>
