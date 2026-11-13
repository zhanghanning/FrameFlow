<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { Search, Refresh, Picture } from "@element-plus/icons-vue";
import { api, date, listItems } from "../lib/api";
import PlatformLogo from "../components/PlatformLogo.vue";
import CoverPreview from "../components/CoverPreview.vue";
import LoadError from "../components/LoadError.vue";
const route = useRoute();
const query = reactive({
  q: "",
  platformCode: "",
  connectionId: route.query.connectionId
    ? Number(route.query.connectionId)
    : "",
  page: 1,
  pageSize: 12,
});
const rows = ref([]),
  total = ref(0),
  loading = ref(false),
  error = ref(""),
  connections = ref([]),
  platforms = ref([]),
  failedImages = ref(new Set()),
  preview = ref();
let request,
  generation = 0;
const accountOptions = computed(() =>
  connections.value.filter(
    (item) => !query.platformCode || item.platformCode === query.platformCode,
  ),
);
const hasConnection = computed(() =>
  connections.value.some((item) => item.status !== "DISCONNECTED"),
);
const hasFilter = computed(
  () => !!(query.q || query.platformCode || query.connectionId),
);
function safeLink(value) {
  return /^https?:\/\//i.test(value || "") ? value : undefined;
}
function number(value) {
  return value == null ? "—" : Number(value).toLocaleString("zh-CN");
}
function markImageFailed(id) {
  failedImages.value = new Set([...failedImages.value, id]);
}
async function load() {
  preview.value?.close(true);
  request?.abort();
  const current = ++generation,
    controller = new AbortController();
  request = controller;
  loading.value = true;
  error.value = "";
  try {
    const data = await api.get("/platform-content", {
      params: query,
      signal: controller.signal,
    });
    if (current !== generation) return;
    rows.value = listItems(data);
    total.value = data.total ?? rows.value.length;
    failedImages.value = new Set();
  } catch (e) {
    if (!controller.signal.aborted) error.value = e.message;
  } finally {
    if (current === generation) loading.value = false;
  }
}
function search() {
  query.page = 1;
  load();
}
function changePlatform() {
  query.connectionId = "";
  search();
}
function hover(row, event) {
  if (!failedImages.value.has(row.id)) preview.value.schedule(row, event);
}
onMounted(async () => {
  const results = await Promise.allSettled([
    api.get("/platform-integrations"),
    api.get("/platform-connections"),
  ]);
  if (results[0].status === "fulfilled") platforms.value = results[0].value;
  if (results[1].status === "fulfilled") connections.value = results[1].value;
  await load();
  const failed = results.find((result) => result.status === "rejected");
  if (failed) error.value = failed.reason.message;
});
onBeforeUnmount(() => {
  generation++;
  request?.abort();
});
</script>
<template>
  <LoadError :message="error" @retry="load" />
  <div class="toolbar platform-content-toolbar">
    <el-input
      v-model="query.q"
      class="search-input"
      :prefix-icon="Search"
      placeholder="搜索平台作品…"
      clearable
      @clear="search"
      @keyup.enter="search"
    /><el-select
      v-model="query.platformCode"
      class="filter-select"
      placeholder="全部平台"
      clearable
      @change="changePlatform"
      ><el-option
        v-for="item in platforms"
        :key="item.code"
        :label="item.name"
        :value="item.code" /></el-select
    ><el-select
      v-model="query.connectionId"
      class="platform-account-filter"
      placeholder="全部账号"
      clearable
      @change="search"
      ><el-option
        v-for="item in accountOptions"
        :key="item.id"
        :label="`${item.platformName} · ${item.accountName}`"
        :value="item.id" /></el-select
    ><el-button @click="search">查询</el-button><span class="spacer"></span
    ><el-button :icon="Refresh" @click="load">刷新</el-button>
  </div>
  <div
    v-loading="loading"
    class="platform-content-grid"
    data-testid="platform-content-grid"
  >
    <article
      v-for="row in rows"
      :key="row.id"
      class="platform-content-card"
      :data-content-id="row.id"
    >
      <a
        class="platform-content-cover"
        :href="safeLink(row.permalink)"
        target="_blank"
        rel="noopener noreferrer"
        :aria-label="`在${row.platformName}查看：${row.title}`"
        @pointerenter="hover(row, $event)"
        @pointerleave="preview.close()"
        @focus="hover(row, $event)"
        @blur="preview.close()"
        @click="preview.close(true)"
        data-testid="platform-content-cover"
        :data-content-id="row.id"
      >
        <img
          v-if="row.coverUrl && !failedImages.has(row.id)"
          :src="row.coverUrl"
          :alt="row.title"
          loading="lazy"
          referrerpolicy="no-referrer"
          @error="markImageFailed(row.id)"
        /><span v-else class="platform-cover-empty"
          ><el-icon><Picture /></el-icon><small>封面暂不可用</small></span
        ><span class="platform-content-badge"
          ><PlatformLogo :code="row.platformCode" :name="row.platformName" />{{
            row.platformName
          }}</span
        ><span v-if="safeLink(row.permalink)" class="platform-content-open"
          >↗</span
        >
      </a>
      <div class="platform-content-body">
        <h3>
          <a
            :href="safeLink(row.permalink)"
            target="_blank"
            rel="noopener noreferrer"
            >{{ row.title || "未命名作品" }}</a
          >
        </h3>
        <div class="platform-content-meta">
          <span>{{ row.accountName }}</span
          ><time>{{ date(row.publishedAt) }}</time>
        </div>
        <div class="platform-content-stats">
          <span
            >播放 <b>{{ number(row.viewCount) }}</b></span
          ><span
            >点赞 <b>{{ number(row.likeCount) }}</b></span
          ><span
            >评论 <b>{{ number(row.commentCount) }}</b></span
          >
        </div>
      </div>
    </article>
  </div>
  <section v-if="!rows.length && !loading && !error" class="platform-empty">
    <el-icon><Picture /></el-icon>
    <h3>
      {{
        hasFilter
          ? "没有符合条件的平台作品"
          : hasConnection
            ? "还没有同步的作品"
            : "连接平台账号后，作品会显示在这里"
      }}
    </h3>
    <p>
      {{
        hasFilter
          ? "调整搜索条件后重试。"
          : hasConnection
            ? "在平台账号页面同步已授权账号。"
            : "完成平台应用配置与账号授权后，可同步作品封面和发布信息。"
      }}
    </p>
    <router-link v-if="!hasFilter" to="/platform-accounts"
      >管理平台账号 ↗</router-link
    >
  </section>
  <div class="pagination">
    <span>共 {{ total }} 条平台作品</span
    ><el-pagination
      v-model:current-page="query.page"
      :page-size="query.pageSize"
      :total="total"
      layout="prev, pager, next"
      @current-change="load"
    />
  </div>
  <CoverPreview ref="preview" />
</template>
