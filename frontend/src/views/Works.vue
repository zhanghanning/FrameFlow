<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Plus, Search } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import {
  api,
  hasRole,
  session,
  date,
  label,
  fail,
  listItems,
} from "../lib/api";
import { platformIntegrationsEnabled } from "../lib/features";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import LoadError from "../components/LoadError.vue";
import PlatformContent from "./PlatformContent.vue";
const route = useRoute(),
  router = useRouter();
const tab = ref(
    platformIntegrationsEnabled &&
    route.query.tab === "platform" &&
    hasRole("ADMIN", "CREATOR")
      ? "platform"
      : "works",
  ),
  query = reactive({ q: "", status: "", projectId: "", page: 1, pageSize: 10 }),
  rows = ref([]),
  total = ref(0),
  options = ref({}),
  optionsLoading = ref(true),
  optionsError = ref(""),
  loading = ref(false),
  error = ref(""),
  dialog = ref(false),
  publishDialog = ref(false),
  editing = ref(null),
  saving = ref(false),
  form = reactive({}),
  publish = reactive({}),
  formRef = ref(),
  publishRef = ref(),
  drawer = ref(false),
  detail = ref(null),
  existingAssets = ref([]);
const projectName = (id) =>
    options.value.projects?.find((x) => x.id === id)?.name || "—",
  accountName = (id) =>
    options.value.platformAccounts?.find((x) => x.id === id)?.name || "—";
const managesProject = (project) =>
  hasRole("PRODUCER") && project?.ownerId === session.user?.id;
const canWriteProject = (project) =>
  hasRole("ADMIN") ||
  managesProject(project) ||
  (hasRole("CREATOR") &&
    project?.creatorIds?.includes(session.user?.creatorId));
const writableProjects = computed(() =>
  (options.value.projects || []).filter(canWriteProject),
);
const createWorkMessage = computed(() => {
  if (optionsLoading.value) return "正在加载可创作的项目…";
  if (optionsError.value) return "项目列表加载失败，请重新加载后创建作品。";
  if (writableProjects.value.length) return "";
  if (hasRole("ADMIN")) return "创建作品前，请先创建一个项目。";
  if (hasRole("PRODUCER"))
    return hasRole("CREATOR")
      ? "你还没有负责或参与的项目。请先创建项目，或请管理员、项目统筹将你加入项目。"
      : "你还没有负责的项目，请先创建项目。";
  return "你尚未加入项目，请联系管理员或项目统筹将你加入项目后再创建作品。";
});
const canWriteWork = (work) =>
  canWriteProject(
    options.value.projects?.find((project) => project.id === work.projectId),
  );
const publishableWorks = computed(() =>
  (options.value.works || []).filter(
    (work) => work.status === "READY" && canWriteWork(work),
  ),
);
const selectableAssets = computed(() => {
  const assets = [
    ...new Map(
      [...(options.value.assets || []), ...existingAssets.value].map(
        (asset) => [asset.id, asset],
      ),
    ).values(),
  ];
  return assets.filter(
    (asset) =>
      asset.creatorId === session.user?.creatorId ||
      asset.sharedProjectIds?.includes(form.projectId),
  );
});
function changeProject() {
  const allowedIds = selectableAssets.value.map((asset) => asset.id);
  form.assetIds = form.assetIds.filter((id) => allowedIds.includes(id));
}
const publishAccounts = computed(() => {
  const work = options.value.works?.find((item) => item.id === publish.workId);
  const project = options.value.projects?.find(
    (item) => item.id === work?.projectId,
  );
  return (options.value.platformAccounts || []).filter(
    (account) =>
      account.status === "ACTIVE" &&
      (hasRole("ADMIN") ||
        managesProject(project) ||
        (hasRole("CREATOR") &&
          account.creatorId === session.user?.creatorId)) &&
      (!project || project.creatorIds?.includes(account.creatorId)),
  );
});
async function loadOptions() {
  optionsLoading.value = true;
  optionsError.value = "";
  try {
    options.value = await api.get("/options");
  } catch (e) {
    optionsError.value = e.message;
  } finally {
    optionsLoading.value = false;
  }
}
async function refreshProjectAccess() {
  await Promise.all([loadOptions(), load()]);
}
async function load() {
  if (tab.value === "platform") return;
  loading.value = true;
  error.value = "";
  try {
    const data = await api.get(`/${tab.value}`, { params: query });
    rows.value = listItems(data);
    total.value = data.total ?? rows.value.length;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
async function open(row = null) {
  editing.value = row?.id || null;
  let data = row;
  if (row)
    try {
      data = await api.get(`/works/${row.id}`);
    } catch (e) {
      fail(e);
      return;
    }
  Object.assign(
    form,
    {
      projectId: null,
      title: "",
      description: "",
      status: "DRAFT",
      assetIds: [],
    },
    data || {},
  );
  existingAssets.value = data?.assets || [];
  form.assetIds = data?.assetIds || data?.assets?.map((x) => x.id) || [];
  dialog.value = true;
}
async function save() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  saving.value = true;
  try {
    await api[editing.value ? "put" : "post"](
      `/works${editing.value ? "/" + editing.value : ""}`,
      form,
    );
    ElMessage.success("作品已保存");
    dialog.value = false;
    load();
    await loadOptions();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
function openPublish(row = null) {
  Object.assign(publish, {
    workId: row?.id || null,
    platformAccountId: null,
    externalId: "",
    publishedAt: new Date().toISOString().slice(0, 10),
    url: "",
  });
  publishDialog.value = true;
}
async function publishWork() {
  if (!(await publishRef.value.validate().catch(() => false))) return;
  saving.value = true;
  try {
    await api.post("/publications", publish);
    ElMessage.success("发布记录已创建，素材授权校验通过");
    publishDialog.value = false;
    tab.value = "publications";
    query.page = 1;
    load();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function show(row) {
  try {
    detail.value = await api.get(`/works/${row.id}`);
    drawer.value = true;
  } catch (e) {
    fail(e);
  }
}
onMounted(async () => {
  if (!platformIntegrationsEnabled && route.query.tab === "platform") {
    const remaining = { ...route.query };
    delete remaining.tab;
    delete remaining.connectionId;
    await router.replace({ query: remaining });
  }
  await refreshProjectAccess();
});
</script>
<template>
  <PageHeader
    title="作品与发布"
    subtitle="连接素材与成片，记录每一次正式发布。"
    eyebrow="CONTENT & PUBLICATION"
    ><el-button
      v-if="tab !== 'platform' && hasRole('ADMIN', 'PRODUCER', 'CREATOR')"
      :disabled="optionsLoading || !!optionsError || !publishableWorks.length"
      @click="openPublish()"
      >登记发布</el-button
    ><el-button
      v-if="tab !== 'platform' && hasRole('ADMIN', 'PRODUCER', 'CREATOR')"
      type="primary"
      :icon="Plus"
      :disabled="!!createWorkMessage"
      :aria-describedby="createWorkMessage ? 'work-creation-guidance' : undefined"
      @click="open()"
      >创建作品</el-button
    ></PageHeader>
  <div
    v-if="tab !== 'platform' && hasRole('ADMIN', 'PRODUCER', 'CREATOR') && createWorkMessage"
    id="work-creation-guidance"
    class="work-creation-guidance"
    :class="{ 'is-error': optionsError }"
    role="status"
    data-testid="work-creation-guidance"
  >
    <div>
      <p>{{ createWorkMessage }}</p>
      <small v-if="optionsError">{{ optionsError }}</small>
    </div>
    <div class="work-creation-guidance-actions">
      <router-link
        v-if="!optionsLoading && !optionsError && hasRole('ADMIN', 'PRODUCER')"
        to="/projects"
        >前往项目</router-link
      >
      <el-button
        size="small"
        :loading="optionsLoading"
        @click="refreshProjectAccess"
        >{{ optionsError ? "重新加载项目" : "刷新项目权限" }}</el-button
      >
    </div>
  </div>
  <LoadError
    v-if="tab !== 'platform'"
    :message="error"
    @retry="load"
  /><el-tabs
    v-model="tab"
    class="section-tabs"
    @tab-change="
      query.page = 1;
      query.status = '';
      load();
    "
    ><el-tab-pane label="作品库" name="works" /><el-tab-pane
      label="发布记录"
      name="publications" /><el-tab-pane
      v-if="platformIntegrationsEnabled && hasRole('ADMIN', 'CREATOR')"
      label="平台作品"
      name="platform"
  /></el-tabs>
  <PlatformContent v-if="platformIntegrationsEnabled && tab === 'platform'" />
  <div v-else class="panel table-panel">
    <div class="toolbar">
      <el-input
        v-model="query.q"
        class="search-input"
        :prefix-icon="Search"
        placeholder="搜索作品名称…"
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
        v-model="query.projectId"
        class="filter-select"
        placeholder="全部项目"
        filterable
        clearable
        @change="
          query.page = 1;
          load();
        "
        ><el-option
          v-for="project in options.projects"
          :key="project.id"
          :label="project.name"
          :value="project.id" /></el-select
      ><el-select
        v-if="tab === 'works'"
        v-model="query.status"
        class="filter-select"
        placeholder="全部状态"
        clearable
        @change="
          query.page = 1;
          load();
        "
        ><el-option
          v-for="status in ['DRAFT', 'EDITING', 'REVIEW', 'READY']"
          :key="status"
          :label="label(status)"
          :value="status" /></el-select
      ><el-button
        @click="
          query.page = 1;
          load();
        "
        >查询</el-button
      ><span class="spacer"></span
      ><span class="result-count">{{ total }} 条记录</span>
    </div>
    <el-table v-loading="loading" :data="rows" empty-text="暂无作品记录"
      ><el-table-column
        :label="tab === 'works' ? '作品名称' : '发布作品'"
        min-width="220"
        ><template #default="{ row }"
          ><span class="table-primary">{{
            row.title ||
            row.workTitle ||
            options.works?.find((x) => x.id === row.workId)?.name
          }}</span
          ><span class="table-secondary">{{
            row.projectName || projectName(row.projectId)
          }}</span></template
        ></el-table-column
      ><el-table-column v-if="tab === 'works'" label="创作状态" width="130"
        ><template #default="{ row }"
          ><StatusBadge :value="row.status" /></template></el-table-column
      ><el-table-column v-if="tab === 'works'" label="创建日期" width="130"
        ><template #default="{ row }">{{
          date(row.createdAt)
        }}</template></el-table-column
      ><el-table-column
        v-if="tab === 'publications'"
        label="发布账号"
        min-width="155"
        ><template #default="{ row }"
          >{{ row.accountName || accountName(row.platformAccountId)
          }}<span class="table-secondary">{{
            row.platformName
          }}</span></template
        ></el-table-column
      ><el-table-column
        v-if="tab === 'publications'"
        prop="externalId"
        label="平台作品 ID"
        width="160"
      /><el-table-column
        v-if="tab === 'publications'"
        label="发布日期"
        width="120"
        ><template #default="{ row }">{{
          date(row.publishedAt)
        }}</template></el-table-column
      ><el-table-column label="操作" min-width="150" fixed="right"
        ><template #default="{ row }"
          ><div v-if="tab === 'works'" class="table-actions">
            <el-button text type="primary" @click="show(row)">详情</el-button
            ><el-button v-if="canWriteWork(row)" text @click="open(row)"
              >编辑</el-button
            ><el-button
              v-if="canWriteWork(row) && row.status === 'READY'"
              text
              type="primary"
              @click="openPublish(row)"
              >发布</el-button
            >
          </div>
          <a
            v-else-if="row.url && /^https?:\/\//.test(row.url)"
            :href="row.url"
            target="_blank"
            rel="noopener noreferrer"
            style="color: var(--accent); font-size: 12px"
            >查看平台作品 ↗</a
          ><span v-else class="muted small">未填写平台链接</span></template
        ></el-table-column
      ></el-table
    >
    <div class="pagination">
      <span>共 {{ total }} 条记录</span
      ><el-pagination
        v-model:current-page="query.page"
        :page-size="query.pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="load"
      />
    </div>
  </div>
  <el-dialog
    v-model="dialog"
    :title="editing ? '编辑作品' : '创建作品'"
    width="650px"
    destroy-on-close
    ><el-form ref="formRef" :model="form" label-position="top"
      ><div class="form-grid">
        <el-form-item
          label="作品标题"
          prop="title"
          :rules="[{ required: true, message: '请输入作品标题' }]"
          ><el-input v-model="form.title" /></el-form-item
        ><el-form-item
          label="所属项目"
          prop="projectId"
          :rules="[{ required: true, message: '请选择项目' }]"
          ><el-select
            v-model="form.projectId"
            filterable
            @change="changeProject"
            ><el-option
              v-for="item in writableProjects"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select></el-form-item
        ><el-form-item label="制作状态"
          ><el-select v-model="form.status"
            ><el-option
              v-for="status in ['DRAFT', 'EDITING', 'REVIEW', 'READY']"
              :key="status"
              :label="label(status)"
              :value="status" /></el-select></el-form-item
        ><el-form-item label="引用素材"
          ><el-select
            v-model="form.assetIds"
            :disabled="!form.projectId"
            placeholder="先选择项目，再选择本人或获授权素材"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            ><el-option
              v-for="item in selectableAssets"
              :disabled="item.status !== 'ACTIVE'"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select></el-form-item
        ><el-form-item label="作品说明" class="wide"
          ><el-input v-model="form.description" type="textarea" :rows="4"
        /></el-form-item>
      </div>
      <div class="form-note">
        本人素材可直接用于参与或负责的项目；他人素材需先授权给当前项目。发布时会核对版权期限与平台范围。
      </div></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save"
        >保存作品</el-button
      ></template
    ></el-dialog
  ><el-dialog
    v-model="publishDialog"
    title="登记作品发布"
    width="620px"
    destroy-on-close
    ><el-form ref="publishRef" :model="publish" label-position="top"
      ><div class="form-grid">
        <el-form-item
          label="发布作品"
          prop="workId"
          :rules="[{ required: true, message: '请选择作品' }]"
          ><el-select
            v-model="publish.workId"
            filterable
            @change="publish.platformAccountId = null"
            ><el-option
              v-for="item in publishableWorks"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select></el-form-item
        ><el-form-item
          label="平台账号"
          prop="platformAccountId"
          :rules="[{ required: true, message: '请选择平台账号' }]"
          ><el-select v-model="publish.platformAccountId" filterable
            ><el-option
              v-for="item in publishAccounts"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select></el-form-item
        ><el-form-item
          label="平台作品 ID"
          prop="externalId"
          :rules="[{ required: true, message: '请输入平台作品 ID' }]"
          ><el-input v-model="publish.externalId" /></el-form-item
        ><el-form-item
          label="发布日期"
          prop="publishedAt"
          :rules="[{ required: true, message: '请选择发布日期' }]"
          ><el-date-picker
            v-model="publish.publishedAt"
            value-format="YYYY-MM-DD"
            class="full-width" /></el-form-item
        ><el-form-item label="平台作品链接" class="wide"
          ><el-input v-model="publish.url" placeholder="https://…"
        /></el-form-item>
      </div>
      <div class="form-note">
        登记已完成的实际发布信息。只有“可发布”状态的作品可以创建发布记录。
      </div></el-form
    ><template #footer
      ><el-button @click="publishDialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="publishWork"
        >校验授权并登记</el-button
      ></template
    ></el-dialog
  ><el-drawer v-model="drawer" title="作品详情" size="550px"
    ><div v-if="detail">
      <div class="panel-title">
        <h2 style="font-size: 21px">{{ detail.title }}</h2>
        <StatusBadge :value="detail.status" />
      </div>
      <p class="page-subtitle">{{ detail.description || "暂无作品说明" }}</p>
      <dl class="detail-grid">
        <div>
          <dt>所属项目</dt>
          <dd>{{ detail.projectName || projectName(detail.projectId) }}</dd>
        </div>
        <div>
          <dt>创建日期</dt>
          <dd>{{ date(detail.createdAt) }}</dd>
        </div>
      </dl>
      <section class="drawer-section">
        <h3>引用素材</h3>
        <el-table :data="detail.assets || []" empty-text="尚未关联素材"
          ><el-table-column prop="name" label="素材名称" /><el-table-column
            label="类型"
            width="80"
            ><template #default="{ row }">{{
              label(row.kind)
            }}</template></el-table-column
          ><el-table-column label="授权截止" width="120"
            ><template #default="{ row }">{{
              row.licenseType === "ORIGINAL" ? "原创长期" : date(row.licenseEnd)
            }}</template></el-table-column
          ></el-table
        >
      </section>
    </div></el-drawer
  >
</template>

<style scoped>
.work-creation-guidance {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin: -7px 0 20px;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: var(--paper);
  color: var(--ink);
  font-size: 13px;
  line-height: 1.7;
}
.work-creation-guidance p { margin: 0; }
.work-creation-guidance small { display: block; margin-top: 3px; }
.work-creation-guidance.is-error { border-color: #eed5ce; background: #fff5f1; color: #955847; }
.work-creation-guidance-actions { display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
.work-creation-guidance-actions a { color: var(--accent); text-decoration: underline; text-underline-offset: 3px; }
</style>
