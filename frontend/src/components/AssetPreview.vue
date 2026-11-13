<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { Document, Headset, Download, View } from "@element-plus/icons-vue";
import { api, label } from "../lib/api";
const emit = defineEmits(["details", "download"]);
const hoverAsset = ref(null),
  hoverVisible = ref(false),
  hoverPoster = ref(""),
  hoverClip = ref(""),
  hoverError = ref(""),
  hoverVideo = ref(),
  hoverPosition = ref({});
const fullAsset = ref(null),
  fullVisible = ref(false),
  fullUrl = ref(""),
  fullLoading = ref(false),
  fullError = ref(""),
  fullMedia = ref();
const reducedMotion = ref(false);
let hoverTimer,
  fadeTimer,
  hoverController,
  hoverRequestId = 0;
let fullController,
  fullRequestId = 0,
  motionQuery,
  skipRestoredFocus = false;
const fullType = computed(() => fullAsset.value?.kind);
function stop(element) {
  if (element && typeof element.pause === "function") element.pause();
}
function releaseHover() {
  if (hoverPoster.value) URL.revokeObjectURL(hoverPoster.value);
  if (hoverClip.value) URL.revokeObjectURL(hoverClip.value);
  hoverPoster.value = "";
  hoverClip.value = "";
  hoverAsset.value = null;
  hoverError.value = "";
}
function closeHover(immediate = false) {
  hoverRequestId++;
  clearTimeout(hoverTimer);
  clearTimeout(fadeTimer);
  hoverController?.abort();
  stop(hoverVideo.value);
  hoverVisible.value = false;
  if (immediate) releaseHover();
  else fadeTimer = setTimeout(releaseHover, reducedMotion.value ? 0 : 150);
}
function position(rect) {
  const margin = 12,
    gap = 14;
  const width = Math.min(440, window.innerWidth - margin * 2);
  const mediaHeight = Math.min(280, window.innerHeight - 96);
  const height = mediaHeight + 52;
  let left = rect.right + gap,
    top = rect.top;
  if (left + width > window.innerWidth - margin) {
    if (rect.left - gap - width >= margin) left = rect.left - gap - width;
    else {
      left = rect.left + (rect.width - width) / 2;
      top =
        rect.bottom + gap + height <= window.innerHeight - margin
          ? rect.bottom + gap
          : rect.top - gap - height;
    }
  }
  hoverPosition.value = {
    left: `${Math.max(margin, Math.min(left, window.innerWidth - width - margin))}px`,
    top: `${Math.max(margin, Math.min(top, window.innerHeight - height - margin))}px`,
    width: `${width}px`,
    "--preview-height": `${mediaHeight}px`,
  };
}
function schedule(asset, event) {
  if (event.type === "focus" && skipRestoredFocus) return;
  if (
    event.pointerType === "touch" ||
    fullVisible.value ||
    !["IMAGE", "VIDEO"].includes(asset.kind)
  )
    return;
  closeHover(true);
  const target = event.currentTarget;
  const current = hoverRequestId;
  hoverTimer = setTimeout(() => loadHover(asset, target, current), 800);
}
async function loadHover(asset, target, current) {
  if (!target.isConnected || current !== hoverRequestId) return;
  const request = new AbortController();
  hoverController = request;
  try {
    const media = await api.get(`/assets/${asset.id}/media`, {
      signal: request.signal,
      timeout: 120000,
    });
    if (request.signal.aborted || current !== hoverRequestId) return;
    if (media.posterUrl) {
      const poster = await api.get(media.posterUrl, {
        responseType: "blob",
        signal: request.signal,
      });
      if (request.signal.aborted || current !== hoverRequestId) return;
      hoverPoster.value = URL.createObjectURL(poster);
    }
    hoverAsset.value = asset;
    position(
      target.closest(".asset-card")?.getBoundingClientRect() ||
        target.getBoundingClientRect(),
    );
    hoverVisible.value = true;
    if (asset.kind === "VIDEO" && media.previewUrl && !reducedMotion.value) {
      const clip = await api.get(media.previewUrl, {
        responseType: "blob",
        signal: request.signal,
      });
      if (request.signal.aborted || current !== hoverRequestId) return;
      hoverClip.value = URL.createObjectURL(clip);
      await nextTick();
      if (request.signal.aborted || current !== hoverRequestId) return;
      if (hoverVideo.value) {
        hoverVideo.value.muted = true;
        await hoverVideo.value.play().catch(() => {});
      }
    }
  } catch (e) {
    if (request.signal.aborted || current !== hoverRequestId) return;
    hoverAsset.value = asset;
    hoverError.value = e.message;
    position(target.getBoundingClientRect());
    hoverVisible.value = true;
  }
}
function hoverPlaybackError() {
  stop(hoverVideo.value);
  if (hoverClip.value) URL.revokeObjectURL(hoverClip.value);
  hoverClip.value = "";
  hoverError.value = "浏览器无法播放预览，可点击查看完整素材";
}
function fullPlaybackError() {
  if (!fullVisible.value || !fullUrl.value) return;
  stop(fullMedia.value);
  fullError.value = "浏览器无法播放该文件，可下载原文件查看";
}
function clearFull() {
  fullRequestId++;
  fullController?.abort();
  stop(fullMedia.value);
  if (fullUrl.value) URL.revokeObjectURL(fullUrl.value);
  fullUrl.value = "";
  fullLoading.value = false;
  fullError.value = "";
}
function onCloseAutoFocus() {
  skipRestoredFocus = true;
  queueMicrotask(() => {
    skipRestoredFocus = false;
  });
}
function closeFull() {
  fullVisible.value = false;
  clearFull();
}
async function open(asset) {
  closeHover(true);
  clearFull();
  fullAsset.value = asset;
  fullVisible.value = true;
  if (!["IMAGE", "VIDEO", "AUDIO"].includes(asset.kind)) return;
  const current = fullRequestId;
  const request = new AbortController();
  fullController = request;
  fullLoading.value = true;
  try {
    const blob = await api.get(`/assets/${asset.id}/file`, {
      responseType: "blob",
      signal: request.signal,
      timeout: 120000,
    });
    if (request.signal.aborted || current !== fullRequestId) return;
    fullUrl.value = URL.createObjectURL(blob);
  } catch (e) {
    if (!request.signal.aborted && current === fullRequestId)
      fullError.value = e.message;
  } finally {
    if (current === fullRequestId) fullLoading.value = false;
  }
}
function details() {
  const asset = fullAsset.value;
  closeFull();
  emit("details", asset);
}
function download() {
  if (!fullUrl.value) return emit("download", fullAsset.value);
  const link = document.createElement("a");
  link.href = fullUrl.value;
  link.download =
    fullAsset.value.filename ||
    fullAsset.value.originalFilename ||
    fullAsset.value.name;
  link.click();
}
function closeAll() {
  closeHover(true);
  closeFull();
}
function onScroll() {
  closeHover();
}
function onBlur() {
  closeHover();
  closeFull();
}
function onKeydown(event) {
  if (event.key === "Escape") {
    closeHover();
    closeFull();
  }
}
function onMotionChange(event) {
  reducedMotion.value = event.matches;
  closeHover(true);
}
onMounted(() => {
  motionQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
  reducedMotion.value = motionQuery.matches;
  motionQuery.addEventListener("change", onMotionChange);
  window.addEventListener("scroll", onScroll, true);
  window.addEventListener("resize", onScroll);
  window.addEventListener("blur", onBlur);
  window.addEventListener("keydown", onKeydown);
});
onBeforeUnmount(() => {
  closeAll();
  clearTimeout(fadeTimer);
  motionQuery?.removeEventListener("change", onMotionChange);
  window.removeEventListener("scroll", onScroll, true);
  window.removeEventListener("resize", onScroll);
  window.removeEventListener("blur", onBlur);
  window.removeEventListener("keydown", onKeydown);
});
defineExpose({ schedule, closeHover, open, closeAll });
</script>
<template>
  <Teleport to="body">
    <div
      v-if="hoverAsset"
      class="asset-hover-preview"
      :class="{ 'is-visible': hoverVisible }"
      :style="hoverPosition"
      data-testid="asset-hover-preview"
      :data-asset-id="hoverAsset.id"
      :data-visible="hoverVisible"
      aria-hidden="true"
    >
      <div class="asset-hover-visual">
        <video
          v-if="hoverClip"
          ref="hoverVideo"
          :src="hoverClip"
          :poster="hoverPoster"
          muted
          loop
          playsinline
          preload="auto"
          @error="hoverPlaybackError"
          data-testid="asset-hover-video"
          :data-asset-id="hoverAsset.id"
        />
        <img
          v-else-if="hoverPoster"
          :src="hoverPoster"
          :alt="hoverAsset.name"
          data-testid="asset-hover-image"
        />
        <p v-else class="asset-preview-message">
          {{ hoverError || "点击查看完整素材" }}
        </p>
      </div>
      <div class="asset-hover-caption">
        <strong>{{ hoverAsset.name }}</strong
        ><span>{{
          hoverError
            ? "预览不可用 · 点击查看"
            : hoverAsset.kind === "VIDEO" && !reducedMotion && hoverClip
              ? "3 秒预览 · 点击完整播放"
              : "点击查看完整素材"
        }}</span>
      </div>
    </div>
  </Teleport>
  <el-dialog
    v-model="fullVisible"
    :title="fullAsset?.name || '素材预览'"
    width="min(1060px, calc(100vw - 48px))"
    align-center
    append-to-body
    destroy-on-close
    class="asset-full-preview-dialog"
    @close="clearFull"
    @close-auto-focus="onCloseAutoFocus"
  >
    <div
      v-if="fullAsset"
      class="asset-full-preview"
      data-testid="asset-full-preview"
      :data-asset-id="fullAsset.id"
    >
      <div
        v-if="fullLoading"
        class="asset-full-stage asset-preview-loading"
        role="status"
      >
        <span class="asset-preview-spinner"></span>
        <p>正在加载完整素材…</p>
      </div>
      <div
        v-else-if="fullError"
        class="asset-full-stage asset-preview-error"
        role="alert"
      >
        <p>{{ fullError }}</p>
        <el-button v-if="!fullUrl" @click="open(fullAsset)">重新加载</el-button>
      </div>
      <div v-else-if="fullType === 'IMAGE' && fullUrl" class="asset-full-stage">
        <img
          :src="fullUrl"
          :alt="fullAsset.name"
          data-testid="asset-full-image"
        />
      </div>
      <div v-else-if="fullType === 'VIDEO' && fullUrl" class="asset-full-stage">
        <video
          ref="fullMedia"
          :src="fullUrl"
          controls
          playsinline
          preload="metadata"
          @error="fullPlaybackError"
          data-testid="asset-full-video"
          :data-asset-id="fullAsset.id"
        >
          当前浏览器无法播放此视频，请下载原文件查看。
        </video>
      </div>
      <div
        v-else-if="fullType === 'AUDIO' && fullUrl"
        class="asset-full-stage asset-full-audio"
      >
        <el-icon><Headset /></el-icon>
        <h3>{{ fullAsset.name }}</h3>
        <audio
          ref="fullMedia"
          :src="fullUrl"
          controls
          preload="metadata"
          @error="fullPlaybackError"
          data-testid="asset-full-audio"
        >
          当前浏览器无法播放此音频，请下载原文件查看。
        </audio>
      </div>
      <div
        v-else-if="!['IMAGE', 'VIDEO', 'AUDIO'].includes(fullType)"
        class="asset-full-stage asset-full-document"
      >
        <el-icon><Document /></el-icon>
        <h3>{{ fullAsset.name }}</h3>
        <p>下载原文件查看文档内容，或打开详情查看版权与项目授权。</p>
      </div>
    </div>
    <template #footer
      ><div class="asset-full-footer">
        <span
          >{{ label(fullType)
          }}<span v-if="fullType === 'VIDEO' || fullType === 'AUDIO'">
            · 点击播放按钮开始播放</span
          ></span
        >
        <div>
          <el-button :icon="View" @click="details">素材详情</el-button
          ><el-button :icon="Download" @click="download">下载原文件</el-button>
        </div>
      </div></template
    >
  </el-dialog>
</template>
