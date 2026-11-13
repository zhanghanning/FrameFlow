<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import {
  Plus,
  Search,
  UploadFilled,
  Download,
  Edit,
  FolderOpened,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  api,
  hasRole,
  session,
  date,
  label,
  fail,
  listItems,
  download,
} from "../lib/api";
import PageHeader from "../components/PageHeader.vue";
import LoadError from "../components/LoadError.vue";
import StatusBadge from "../components/StatusBadge.vue";
import AssetThumbnail from "../components/AssetThumbnail.vue";
const route = useRoute(),
  query = reactive({ q: "", kind: "", status: "", page: 1, pageSize: 12 }),
  rows = ref([]),
  total = ref(0),
  loading = ref(false),
  error = ref(""),
  options = ref({}),
  dialog = ref(false),
  editing = ref(null),
  saving = ref(false),
  form = reactive({}),
  formRef = ref(),
  file = ref(null),
  fileInput = ref(),
  drawer = ref(false),
  detail = ref(null),
  platformSelection = ref(["ALL"]),
  sharingDialog = ref(false),
  sharedProjectIds = ref([]),
  sharing = ref(false);
const canEditAsset = (asset) =>
  hasRole("ADMIN") ||
  (hasRole("CREATOR") && asset.creatorId === session.user?.creatorId);
const creatorOptions = computed(() =>
  (options.value.creators || []).filter(
    (creator) => hasRole("ADMIN") || creator.id === session.user?.creatorId,
  ),
);
const shareableProjects = computed(() =>
  (options.value.projects || []).filter(
    (project) =>
      hasRole("ADMIN") ||
      project.creatorIds?.includes(session.user?.creatorId) ||
      (hasRole("PRODUCER") && project.ownerId === session.user?.id),
  ),
);
function projectName(id) {
  return (
    options.value.projects?.find((project) => project.id === id)?.name ||
    `项目 #${id}`
  );
}
function openSharing() {
  sharedProjectIds.value = [...(detail.value.sharedProjectIds || [])];
  sharingDialog.value = true;
}
async function saveSharing() {
  sharing.value = true;
  try {
    await api.put(`/assets/${detail.value.id}/projects`, {
      projectIds: sharedProjectIds.value,
    });
    detail.value = await api.get(`/assets/${detail.value.id}`);
    sharingDialog.value = false;
    ElMessage.success("项目素材授权已更新");
    await load();
    options.value = await api.get("/options");
  } catch (e) {
    fail(e);
  } finally {
    sharing.value = false;
  }
}
const kinds = ["VIDEO", "IMAGE", "AUDIO", "DOCUMENT"],
  size = (value) =>
    value >= 1048576
      ? `${(value / 1048576).toFixed(1)} MB`
      : `${Math.round((value || 0) / 1024)} KB`;
const tags = (row) =>
  Array.isArray(row.tags)
    ? row.tags.map((t) => (typeof t === "string" ? t : t.name))
    : String(row.tags || "")
        .split(",")
        .filter(Boolean);
const days = (row) =>
  row.licenseEnd
    ? Math.ceil((new Date(row.licenseEnd) - new Date()) / 86400000)
    : null;
const licenseText = (row) =>
  row.licenseType === "ORIGINAL"
    ? "原创 · 长期有效"
    : days(row) < 0
      ? "授权已到期"
      : `授权至 ${date(row.licenseEnd)}`;
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const data = await api.get("/assets", { params: query });
    rows.value = listItems(data);
    total.value = data.total ?? rows.value.length;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
function open(row = null) {
  editing.value = row?.id || null;
  Object.assign(
    form,
    {
      name: "",
      kind: "IMAGE",
      creatorId: hasRole("ADMIN") ? null : session.user?.creatorId,
      licenseType: "ORIGINAL",
      licenseStart: new Date().toISOString().slice(0, 10),
      licenseEnd: "",
      allowedPlatforms: "ALL",
      tags: "",
      description: "",
      status: "ACTIVE",
    },
    row || {},
  );
  form.tags = tags(form).join(",");
  platformSelection.value = String(form.allowedPlatforms || "ALL").split(",");
  file.value = null;
  dialog.value = true;
}
function choose(event) {
  file.value = event.target.files?.[0] || null;
  if (!file.value) return;
  if (file.value.size > 100 * 1024 * 1024) {
    ElMessage.warning("单文件最大100MB");
    file.value = null;
    return;
  }
  if (!form.name) form.name = file.value.name.replace(/\.[^.]+$/, "");
  const type = file.value.type;
  form.kind = type.startsWith("video/")
    ? "VIDEO"
    : type.startsWith("audio/")
      ? "AUDIO"
      : type.startsWith("image/")
        ? "IMAGE"
        : "DOCUMENT";
}
async function save() {
  form.allowedPlatforms = platformSelection.value.includes("ALL")
    ? "ALL"
    : platformSelection.value.join(",");
  if (!(await formRef.value.validate().catch(() => false))) return;
  if (!editing.value && !file.value)
    return ElMessage.warning("请选择要上传的素材文件");
  if (
    form.licenseType === "LICENSED" &&
    (!form.licenseEnd || form.licenseEnd < form.licenseStart)
  )
    return ElMessage.warning("授权素材需填写有效授权期限");
  saving.value = true;
  try {
    if (editing.value) await api.put(`/assets/${editing.value}`, form);
    else {
      const data = new FormData();
      data.append("file", file.value);
      Object.entries(form).forEach(([key, value]) => {
        if (value !== null && value !== "") data.append(key, value);
      });
      await api.post("/assets", data, { timeout: 120000 });
    }
    ElMessage.success("素材已保存");
    dialog.value = false;
    await load();
    options.value = await api.get("/options");
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
function show(row) {
  detail.value = row;
  drawer.value = true;
}
function selectPlatforms(values) {
  platformSelection.value =
    values.at(-1) === "ALL"
      ? ["ALL"]
      : values.filter((value) => value !== "ALL");
}
async function archive() {
  try {
    await ElMessageBox.confirm(
      `归档「${detail.value.name}」？已引用作品保留关联记录。`,
      "归档素材",
      { confirmButtonText: "归档", cancelButtonText: "取消" },
    );
    await api.put(`/assets/${detail.value.id}`, {
      ...detail.value,
      tags: tags(detail.value).join(","),
      status: "ARCHIVED",
    });
    drawer.value = false;
    ElMessage.success("素材已归档");
    load();
  } catch (e) {
    if (e instanceof Error) fail(e);
  }
}
async function getFile(row) {
  try {
    await download(
      `/assets/${row.id}/file`,
      row.filename || row.originalFilename || row.name,
    );
  } catch (e) {
    fail(e);
  }
}
onMounted(async () => {
  await load();
  try {
    options.value = await api.get("/options");
  } catch (e) {
    fail(e);
  }
  if (route.query.upload && hasRole("ADMIN", "CREATOR")) open();
  if (route.query.detail) {
    try {
      show(await api.get(`/assets/${route.query.detail}`));
    } catch (e) {
      fail(e);
    }
  }
});
</script>
<template>
  <PageHeader
    title="素材资产库"
    subtitle="让灵感有处安放。统一管理素材、版权与作品引用。"
    eyebrow="CREATIVE ASSETS"
    ><el-button
      v-if="hasRole('ADMIN', 'CREATOR')"
      type="primary"
      :icon="Plus"
      @click="open()"
      >上传素材</el-button
    ></PageHeader
  ><LoadError :message="error" @retry="load" />
  <div class="toolbar">
    <el-input
      v-model="query.q"
      class="search-input"
      :prefix-icon="Search"
      placeholder="搜索素材名称、标签…"
      clearable
      @clear="
        query.page = 1;
        load();
      "
      @keyup.enter="
        query.page = 1;
        load();
      "
    /><el-select
      v-model="query.kind"
      class="filter-select"
      placeholder="所有类型"
      clearable
      @change="
        query.page = 1;
        load();
      "
      ><el-option
        v-for="kind in kinds"
        :key="kind"
        :label="label(kind)"
        :value="kind" /></el-select
    ><el-select
      v-model="query.status"
      class="filter-select"
      placeholder="全部状态"
      clearable
      @change="
        query.page = 1;
        load();
      "
      ><el-option label="使用中" value="ACTIVE" /><el-option
        label="已归档"
        value="ARCHIVED" /></el-select
    ><el-button
      @click="
        query.page = 1;
        load();
      "
      >查询</el-button
    ><span class="spacer"></span
    ><span class="result-count">{{ total }} 份创作素材</span>
  </div>
  <div v-loading="loading" class="asset-grid">
    <article v-for="row in rows" :key="row.id" class="asset-card">
      <div class="asset-preview" style="cursor: pointer" @click="show(row)">
        <AssetThumbnail :asset="row" /><span class="asset-type">{{
          label(row.kind)
        }}</span
        ><span class="asset-size">{{ size(row.sizeBytes) }}</span>
      </div>
      <div class="asset-body">
        <h3>{{ row.name }}</h3>
        <div class="asset-meta">
          <span>{{
            row.creatorName ||
            options.creators?.find((x) => x.id === row.creatorId)?.name ||
            "工作室素材"
          }}</span
          ><span>{{ row.referenceCount || 0 }} 次引用</span>
        </div>
        <div class="asset-tags">
          <span v-for="tag in tags(row).slice(0, 3)" :key="tag">{{ tag }}</span
          ><span v-if="!tags(row).length">未添加标签</span>
        </div>
        <div class="asset-bottom">
          <span
            class="asset-license"
            :class="{
              warning:
                row.licenseType === 'LICENSED' &&
                days(row) >= 0 &&
                days(row) <= 30,
              expired: row.licenseType === 'LICENSED' && days(row) < 0,
            }"
            >{{ licenseText(row) }}</span
          ><el-button text type="primary" @click="show(row)">详情 ↗</el-button>
        </div>
      </div>
    </article>
  </div>
  <el-empty
    v-if="!rows.length && !loading"
    description="暂无素材，上传文件开始积累创作资产"
    :image-size="85"
  />
  <div class="pagination">
    <span>共 {{ total }} 份素材</span
    ><el-pagination
      v-model:current-page="query.page"
      :page-size="query.pageSize"
      :total="total"
      layout="prev, pager, next"
      @current-change="load"
    />
  </div>
  <el-dialog
    v-model="dialog"
    :title="editing ? '编辑素材与授权' : '上传创作素材'"
    width="680px"
    destroy-on-close
    ><div v-if="!editing" class="upload-area" @click="fileInput.click()">
      <input ref="fileInput" type="file" @change="choose" /><el-icon
        ><UploadFilled
      /></el-icon>
      <p>{{ file ? file.name : "点击选择素材文件" }}</p>
      <small>{{
        file ? size(file.size) : "视频、图片、音频、文档 · 单文件最大 100MB"
      }}</small>
    </div>
    <el-form ref="formRef" :model="form" label-position="top"
      ><div class="form-grid">
        <el-form-item
          label="素材名称"
          prop="name"
          :rules="[{ required: true, message: '请输入素材名称' }]"
          ><el-input v-model="form.name" maxlength="200" /></el-form-item
        ><el-form-item label="素材类型"
          ><el-select v-model="form.kind"
            ><el-option
              v-for="kind in kinds"
              :key="kind"
              :label="label(kind)"
              :value="kind" /></el-select></el-form-item
        ><el-form-item
          label="所属创作者"
          prop="creatorId"
          :rules="[{ required: true, message: '请选择创作者' }]"
          ><el-select v-model="form.creatorId" filterable
            ><el-option
              v-for="item in creatorOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select></el-form-item
        ><el-form-item label="授权类型"
          ><el-select v-model="form.licenseType"
            ><el-option label="工作室原创" value="ORIGINAL" /><el-option
              label="获得外部授权"
              value="LICENSED" /></el-select></el-form-item
        ><el-form-item label="授权起始日期"
          ><el-date-picker
            v-model="form.licenseStart"
            value-format="YYYY-MM-DD"
            class="full-width" /></el-form-item
        ><el-form-item label="授权截止日期"
          ><el-date-picker
            v-model="form.licenseEnd"
            value-format="YYYY-MM-DD"
            :placeholder="
              form.licenseType === 'ORIGINAL'
                ? '原创素材可长期有效'
                : '请选择到期日'
            "
            class="full-width" /></el-form-item
        ><el-form-item
          label="允许发布的平台"
          prop="allowedPlatforms"
          :rules="[{ required: true, message: '请选择允许的平台' }]"
          ><el-select
            v-model="platformSelection"
            multiple
            @change="selectPlatforms"
            ><el-option label="全部平台" value="ALL" /><el-option
              v-for="item in options.platforms"
              :key="item.id"
              :label="item.name"
              :value="item.code" /></el-select></el-form-item
        ><el-form-item label="素材标签"
          ><el-input
            v-model="form.tags"
            placeholder="用英文逗号分隔，如 城市,日常" /></el-form-item
        ><el-form-item label="素材说明 / 授权说明" class="wide"
          ><el-input v-model="form.description" type="textarea" :rows="3"
        /></el-form-item></div></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save">{{
        editing ? "保存修改" : "上传并保存"
      }}</el-button></template
    ></el-dialog
  ><el-drawer v-model="drawer" title="素材详情与授权" size="540px"
    ><div v-if="detail">
      <div
        class="asset-preview"
        style="height: 230px; border-radius: 10px; margin-bottom: 22px"
      >
        <AssetThumbnail :asset="detail" /><span class="asset-type">{{
          label(detail.kind)
        }}</span>
      </div>
      <div class="panel-title">
        <h2 style="font-size: 20px">{{ detail.name }}</h2>
        <StatusBadge :value="detail.status" />
      </div>
      <p class="page-subtitle">{{ detail.description || "暂无素材说明" }}</p>
      <dl class="detail-grid">
        <div>
          <dt>原始文件</dt>
          <dd>{{ detail.filename || detail.originalFilename }}</dd>
        </div>
        <div>
          <dt>文件大小</dt>
          <dd>{{ size(detail.sizeBytes) }}</dd>
        </div>
        <div>
          <dt>授权类型</dt>
          <dd>{{ label(detail.licenseType) }}</dd>
        </div>
        <div>
          <dt>授权有效期</dt>
          <dd>{{ licenseText(detail) }}</dd>
        </div>
        <div>
          <dt>允许平台</dt>
          <dd>
            {{
              String(detail.allowedPlatforms || "ALL")
                .split(",")
                .map(label)
                .join("、")
            }}
          </dd>
        </div>
        <div class="wide">
          <dt>授权使用的项目</dt>
          <dd>
            <span
              v-for="id in detail.sharedProjectIds || []"
              :key="id"
              class="role-tag"
              >{{ projectName(id) }}</span
            ><span v-if="!detail.sharedProjectIds?.length"
              >尚未授权项目，当前由素材所有者使用</span
            >
          </dd>
        </div>
        <div>
          <dt>作品引用</dt>
          <dd>{{ detail.referenceCount || 0 }} 个作品</dd>
        </div>
      </dl>
      <div class="heading-actions">
        <el-button type="primary" :icon="Download" @click="getFile(detail)"
          >下载原文件</el-button
        ><el-button
          v-if="canEditAsset(detail)"
          :icon="Edit"
          @click="open(detail)"
          >编辑素材</el-button
        ><el-button v-if="canEditAsset(detail)" @click="openSharing"
          >项目授权</el-button
        ><el-button
          v-if="canEditAsset(detail) && detail.status !== 'ARCHIVED'"
          :icon="FolderOpened"
          @click="archive"
          >归档</el-button
        >
      </div>
    </div></el-drawer
  >
  <el-dialog
    v-model="sharingDialog"
    title="项目素材使用授权"
    width="620px"
    destroy-on-close
  >
    <p class="application-role-description">
      授权后，项目统筹和参与创作者可在该项目的作品中引用此素材。素材内容与版权信息由所有者维护。
    </p>
    <el-form label-position="top"
      ><el-form-item label="允许使用的项目"
        ><el-select
          v-model="sharedProjectIds"
          multiple
          filterable
          class="full-width"
          placeholder="选择你参与或负责的项目"
          ><el-option
            v-for="project in shareableProjects"
            :key="project.id"
            :label="project.name"
            :value="project.id" /></el-select></el-form-item
    ></el-form>
    <p class="form-note">
      已经被项目作品引用的授权将保留。作品使用本人素材时会自动建立对应项目授权。
    </p>
    <template #footer
      ><el-button @click="sharingDialog = false">取消</el-button
      ><el-button type="primary" :loading="sharing" @click="saveSharing"
        >保存授权</el-button
      ></template
    >
  </el-dialog>
</template>
