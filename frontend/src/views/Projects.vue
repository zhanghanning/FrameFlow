<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { Plus, Search, Grid, List } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import {
  api,
  hasRole,
  session,
  money,
  date,
  fail,
  label,
  listItems,
} from "../lib/api";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import LoadError from "../components/LoadError.vue";
const route = useRoute(),
  query = reactive({ q: "", status: "", page: 1, pageSize: 9 }),
  rows = ref([]),
  total = ref(0),
  loading = ref(false),
  error = ref(""),
  options = ref({}),
  dialog = ref(false),
  saving = ref(false),
  editing = ref(null),
  form = reactive({}),
  formRef = ref(),
  drawer = ref(false),
  detail = ref(null),
  detailBusy = ref(false),
  view = ref("grid");
const canManageProject = (project) =>
  hasRole("ADMIN") ||
  (hasRole("PRODUCER") && project.ownerId === session.user?.id);
const ownerOptions = computed(() =>
  hasRole("ADMIN")
    ? options.value.users || []
    : [{ id: session.user?.id, name: session.user?.displayName }],
);
const statuses = ["PLANNING", "PRODUCING", "REVIEW", "COMPLETED", "ARCHIVED"];
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const data = await api.get("/projects", { params: query });
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
      clientName: "",
      description: "",
      budget: 0,
      startDate: new Date().toISOString().slice(0, 10),
      dueDate: "",
      status: "PLANNING",
      ownerId: hasRole("ADMIN") ? null : session.user?.id,
      creatorIds: [],
    },
    row || {},
  );
  form.creatorIds =
    row?.creatorIds || row?.members?.map((x) => x.creatorId || x.id) || [];
  dialog.value = true;
}
async function save() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  if (form.dueDate < form.startDate)
    return ElMessage.warning("截止日期应晚于开始日期");
  saving.value = true;
  try {
    await api[editing.value ? "put" : "post"](
      `/projects${editing.value ? "/" + editing.value : ""}`,
      form,
    );
    ElMessage.success("项目已保存");
    dialog.value = false;
    load();
    options.value = await api.get("/options");
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function show(id) {
  drawer.value = true;
  detailBusy.value = true;
  try {
    detail.value = await api.get(`/projects/${id}`);
  } catch (e) {
    fail(e);
    drawer.value = false;
  } finally {
    detailBusy.value = false;
  }
}
onMounted(async () => {
  await load();
  try {
    options.value = await api.get("/options");
  } catch (e) {
    fail(e);
  }
  if (route.query.create && hasRole("ADMIN", "PRODUCER")) open();
  if (route.query.detail) show(route.query.detail);
});
</script>
<template>
  <PageHeader
    title="创作项目"
    subtitle="从一个灵感，到一个作品。让每次协作都有清晰的方向。"
    eyebrow="CREATIVE PROJECTS"
    ><el-button
      v-if="hasRole('ADMIN', 'PRODUCER')"
      type="primary"
      :icon="Plus"
      @click="open()"
      >创建新项目</el-button
    ></PageHeader
  ><LoadError :message="error" @retry="load" />
  <div class="toolbar">
    <el-input
      v-model="query.q"
      class="search-input"
      :prefix-icon="Search"
      placeholder="搜索项目、客户…"
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
      v-model="query.status"
      class="filter-select"
      placeholder="全部状态"
      clearable
      @change="
        query.page = 1;
        load();
      "
      ><el-option
        v-for="status in statuses"
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
    ><span class="result-count">{{ total }} 个项目</span
    ><el-radio-group v-model="view" size="small"
      ><el-radio-button value="grid" aria-label="网格视图"
        ><el-icon><Grid /></el-icon></el-radio-button
      ><el-radio-button value="table" aria-label="表格视图"
        ><el-icon><List /></el-icon></el-radio-button
    ></el-radio-group>
  </div>
  <div v-loading="loading">
    <div v-if="view === 'grid'" class="project-grid">
      <article
        v-for="row in rows"
        :key="row.id"
        class="project-card"
        role="button"
        tabindex="0"
        :aria-label="`查看项目：${row.name}`"
        @click="show(row.id)"
        @keydown.enter="show(row.id)"
        @keydown.space.prevent="show(row.id)"
      >
        <div :class="['project-cover', `project-cover-${Number(row.id) % 3}`]">
          <svg class="project-cover-art" viewBox="0 0 320 190" aria-hidden="true" focusable="false">
            <circle class="art-orbit" cx="250" cy="68" r="69" />
            <rect class="art-sheet" x="126" y="29" width="132" height="122" rx="12" transform="rotate(13 192 90)" />
            <g class="art-slate" transform="translate(109 51) rotate(-10 76 55)">
              <rect class="art-plate" x="0" y="19" width="151" height="97" rx="9" />
              <path class="art-stripe" d="M0 20H151V40H0Z" />
              <path class="art-plate" d="M13 20H35L19 40H0ZM60 20H82L66 40H44ZM107 20H129L113 40H91ZM151 20V40H138Z" />
              <g transform="rotate(-9 0 20)">
                <path class="art-stripe" d="M7 0H144Q151 0 151 7V20H0V7Q0 0 7 0Z" />
                <path class="art-plate" d="M16 0H38L22 20H0ZM63 0H85L69 20H47ZM110 0H132L116 20H94Z" />
              </g>
              <rect class="art-rule" x="15" y="55" width="49" height="5" rx="2.5" />
              <rect class="art-rule" x="15" y="70" width="121" height="2" rx="1" />
              <path class="art-lines" d="M15 86H136M53 76V101M99 76V101" />
              <circle class="art-rivet" cx="8" cy="29" r="2.5" />
            </g>
            <rect class="art-frame" x="88" y="59" width="155" height="105" rx="7" transform="rotate(5 165 111)" />
          </svg>
          <span class="cover-type">项目档案 <b>{{ String(row.id).padStart(3, "0") }}</b></span>
          <span class="project-cover-caption">{{ row.clientName || "工作室原创" }}</span>
          <span class="project-cover-arrow" aria-hidden="true">↗</span>
        </div>
        <div class="project-card-body">
          <div class="project-card-head">
            <h2 :title="row.name">{{ row.name }}</h2>
            <StatusBadge :value="row.status" />
          </div>
          <p class="project-brief">{{ row.description || "尚未填写项目简介" }}</p>
          <div class="project-budget"><span>项目预算</span><strong>¥{{ money(row.budget) }}</strong></div>
          <div class="project-card-footer">
            <div class="project-owner"><span class="project-field-label">项目统筹</span><span class="project-owner-name">{{
              row.ownerName ||
              options.users?.find((x) => x.id === row.ownerId)?.name ||
              "团队项目"
            }}</span></div>
            <div class="project-due"><span class="project-field-label">截止日期</span><time :datetime="date(row.dueDate)">{{ date(row.dueDate) }}</time></div>
          </div>
        </div>
      </article>
    </div>
    <div v-else class="panel table-panel">
      <el-table :data="rows" @row-click="(row) => show(row.id)"
        ><el-table-column
          prop="name"
          label="项目名称"
          min-width="200"
        /><el-table-column
          prop="clientName"
          label="客户"
          min-width="140"
        /><el-table-column label="项目预算" min-width="140"
          ><template #default="{ row }"
            ><span class="money">¥{{ money(row.budget) }}</span></template
          ></el-table-column
        ><el-table-column label="截止日期" width="125"
          ><template #default="{ row }">{{
            date(row.dueDate)
          }}</template></el-table-column
        ><el-table-column label="状态" width="120"
          ><template #default="{ row }"
            ><StatusBadge :value="row.status" /></template></el-table-column
        ><el-table-column label="操作" width="90"
          ><template #default="{ row }"
            ><el-button text type="primary" @click.stop="show(row.id)"
              >详情</el-button
            ></template
          ></el-table-column
        ></el-table
      >
    </div>
    <el-empty
      v-if="!rows.length && !loading"
      :description="
        hasRole('ADMIN', 'PRODUCER')
          ? '暂无项目，开始记录新的创作计划'
          : '还未加入创作项目，请联系项目统筹邀请你参与'
      "
      :image-size="85"
    />
  </div>
  <div class="pagination">
    <span
      >显示第 {{ total ? (query.page - 1) * query.pageSize + 1 : 0 }}–{{
        Math.min(query.page * query.pageSize, total)
      }}
      个项目，共 {{ total }} 个</span
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
    :title="editing ? '编辑项目' : '创建新项目'"
    width="680px"
    destroy-on-close
    ><el-form ref="formRef" :model="form" label-position="top"
      ><div class="form-grid">
        <el-form-item
          label="项目名称"
          prop="name"
          :rules="[{ required: true, message: '请输入项目名称' }]"
          ><el-input v-model="form.name" maxlength="100" /></el-form-item
        ><el-form-item label="客户 / 品牌"
          ><el-input
            v-model="form.clientName"
            placeholder="工作室原创可留空"
            maxlength="100" /></el-form-item
        ><el-form-item label="项目预算（元）"
          ><el-input-number
            v-model="form.budget"
            :min="0"
            :precision="2"
            :controls="false"
            class="full-width" /></el-form-item
        ><el-form-item
          label="项目统筹"
          prop="ownerId"
          :rules="[{ required: true, message: '请选择负责人' }]"
          ><el-select
            v-model="form.ownerId"
            filterable
            :disabled="!hasRole('ADMIN')"
            ><el-option
              v-for="user in ownerOptions"
              :key="user.id"
              :label="user.name"
              :value="user.id" /></el-select></el-form-item
        ><el-form-item
          label="开始日期"
          prop="startDate"
          :rules="[{ required: true, message: '请选择开始日期' }]"
          ><el-date-picker
            v-model="form.startDate"
            value-format="YYYY-MM-DD"
            class="full-width" /></el-form-item
        ><el-form-item
          label="截止日期"
          prop="dueDate"
          :rules="[{ required: true, message: '请选择截止日期' }]"
          ><el-date-picker
            v-model="form.dueDate"
            value-format="YYYY-MM-DD"
            class="full-width" /></el-form-item
        ><el-form-item label="项目状态"
          ><el-select v-model="form.status"
            ><el-option
              v-for="status in statuses"
              :key="status"
              :label="label(status)"
              :value="status" /></el-select></el-form-item
        ><el-form-item label="参与创作者"
          ><el-select v-model="form.creatorIds" multiple filterable
            ><el-option
              v-for="creator in options.creators"
              :key="creator.id"
              :label="creator.name"
              :value="creator.id" /></el-select></el-form-item
        ><el-form-item label="项目简介" class="wide"
          ><el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="2000"
        /></el-form-item></div></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save"
        >保存项目</el-button
      ></template
    ></el-dialog
  ><el-drawer v-model="drawer" title="项目详情" size="640px"
    ><div v-loading="detailBusy" v-if="detail">
      <div class="panel-title">
        <h2 style="font-size: 22px">{{ detail.name }}</h2>
        <el-button v-if="canManageProject(detail)" @click="open(detail)"
          >编辑项目</el-button
        >
      </div>
      <p class="page-subtitle">{{ detail.description || "暂无项目简介" }}</p>
      <dl class="detail-grid">
        <div>
          <dt>项目状态</dt>
          <dd><StatusBadge :value="detail.status" /></dd>
        </div>
        <div>
          <dt>客户 / 品牌</dt>
          <dd>{{ detail.clientName || "工作室原创" }}</dd>
        </div>
        <div>
          <dt>项目预算</dt>
          <dd class="money">¥{{ money(detail.budget) }}</dd>
        </div>
        <div>
          <dt>项目周期</dt>
          <dd>{{ date(detail.startDate) }} → {{ date(detail.dueDate) }}</dd>
        </div>
        <div class="wide">
          <dt>参与创作者</dt>
          <dd>
            <span
              v-for="member in detail.members"
              :key="member.creatorId || member.id"
              class="role-tag"
              >{{ member.name || member.creatorName }}</span
            ><span v-if="!detail.members?.length">暂无成员</span>
          </dd>
        </div>
      </dl>
      <section class="drawer-section">
        <h3>项目作品</h3>
        <el-table :data="detail.works || []" empty-text="尚未创建作品"
          ><el-table-column prop="title" label="作品名称" /><el-table-column
            label="状态"
            width="110"
            ><template #default="{ row }"
              ><StatusBadge :value="row.status" /></template></el-table-column
        ></el-table>
      </section>
      <section class="drawer-section">
        <h3>分成合同</h3>
        <el-table :data="detail.contracts || []" empty-text="尚未签订合同"
          ><el-table-column prop="name" label="合同名称" /><el-table-column
            label="状态"
            width="100"
            ><template #default="{ row }"
              ><StatusBadge :value="row.status" /></template></el-table-column
        ></el-table>
      </section>
      <section class="drawer-section">
        <h3>项目成本</h3>
        <el-table :data="detail.costs || []" empty-text="暂无成本记录"
          ><el-table-column
            prop="description"
            label="费用说明" /><el-table-column label="金额" width="120"
            ><template #default="{ row }"
              >¥{{ money(row.amount) }}</template
            ></el-table-column
          ><el-table-column label="状态" width="100"
            ><template #default="{ row }"
              ><StatusBadge :value="row.status" /></template></el-table-column
        ></el-table>
      </section></div
  ></el-drawer>
</template>

<style scoped>
.project-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 24px; margin-bottom: 26px; }
.project-card { min-width: 0; overflow: hidden; border: 1px solid var(--line); border-radius: 18px; background: var(--surface); box-shadow: var(--shadow-soft); cursor: pointer; transition: transform 180ms ease, box-shadow 180ms ease, border-color 180ms ease; }
.project-card:hover { transform: translateY(-4px); border-color: color-mix(in srgb, var(--accent) 27%, var(--line)); box-shadow: 0 12px 30px #20273512, 0 2px 5px #20273506; }
.project-card:focus-visible { outline: 3px solid var(--accent); outline-offset: 4px; }
.project-cover { --cover-base: #273a4c; --cover-text: #f6eee3; --art-plate: #f3e8d7; --art-stripe: #2f465b; --art-sheet: #738f9c; --art-orbit: #bf7b5e; position: relative; isolation: isolate; height: 178px; overflow: hidden; padding: 22px; color: var(--cover-text); background: var(--cover-base); }
.project-cover-1 { --cover-base: #e9d7bb; --cover-text: #554335; --art-plate: #faf3e7; --art-stripe: #5f655e; --art-sheet: #b79774; --art-orbit: #dca181; }
.project-cover-2 { --cover-base: #cbd9d3; --cover-text: #334d4d; --art-plate: #f0f1e7; --art-stripe: #577879; --art-sheet: #91b0aa; --art-orbit: #d09b7e; }
.project-cover-art { position: absolute; right: -22px; top: -1px; height: 190px; width: 320px; z-index: -1; }
.art-orbit { fill: var(--art-orbit); opacity: .7; }
.art-sheet { fill: var(--art-sheet); opacity: .8; }
.art-slate { filter: drop-shadow(0 7px 6px #20273520); }
.art-plate { fill: var(--art-plate); }
.art-stripe, .art-rivet { fill: var(--art-stripe); }
.art-rule { fill: var(--art-stripe); opacity: .48; }
.art-lines { stroke: var(--art-stripe); stroke-width: 1.5; fill: none; opacity: .28; }
.art-frame { fill: none; stroke: var(--art-plate); stroke-width: 1.4; opacity: .38; }
.cover-type { display: flex; align-items: center; gap: 9px; font-size: 11px; letter-spacing: .7px; }
.cover-type b { font-size: 12px; font-weight: 500; font-variant-numeric: tabular-nums; }
.project-cover-caption { position: absolute; left: 22px; bottom: 21px; max-width: calc(100% - 84px); overflow: hidden; white-space: nowrap; text-overflow: ellipsis; padding: 7px 10px; border: 1px solid color-mix(in srgb, var(--cover-text) 18%, transparent); border-radius: 7px; background: color-mix(in srgb, var(--cover-base) 90%, transparent); font-size: 12px; font-weight: 500; }
.project-cover-arrow { position: absolute; right: 18px; bottom: 19px; display: grid; place-items: center; width: 31px; height: 31px; border-radius: 50%; border: 1px solid color-mix(in srgb, var(--cover-text) 30%, transparent); background: color-mix(in srgb, var(--cover-base) 78%, transparent); font-size: 18px; }
.project-card-body { padding: 22px 22px 18px; }
.project-card-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.project-card-head h2 { margin: 0; min-width: 0; color: var(--ink); font-size: 17px; line-height: 1.55; font-weight: 600; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.project-card-head :deep(.status-badge) { flex-shrink: 0; margin-top: 2px; }
.project-brief { margin: 11px 0 18px; color: var(--muted); font-size: 13px; line-height: 1.75; height: 46px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.project-budget { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin-bottom: 20px; }
.project-budget > span { color: var(--muted); font-size: 12px; }
.project-budget strong { color: var(--ink); font-size: 17px; font-weight: 600; font-variant-numeric: tabular-nums; letter-spacing: -.3px; }
.project-card-footer { display: flex; justify-content: space-between; gap: 18px; padding-top: 16px; border-top: 1px solid var(--line); color: var(--ink); font-size: 12px; }
.project-owner, .project-due { min-width: 0; display: flex; flex-direction: column; gap: 6px; }
.project-due { flex-shrink: 0; text-align: right; font-variant-numeric: tabular-nums; }
.project-field-label { color: var(--muted); font-size: 11px; }
.project-owner-name { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
@media (max-width: 1180px) { .project-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; } }
@media (max-width: 640px) { .project-grid { grid-template-columns: 1fr; gap: 18px; } .project-cover { height: 170px; } .project-card-body { padding: 20px; } }
@media (prefers-reduced-motion: reduce) { .project-card { transition: none; } .project-card:hover { transform: none; } }
</style>
