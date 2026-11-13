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
const statuses = ["PLANNING", "PRODUCING", "REVIEW", "COMPLETED", "ARCHIVED"],
  progress = (status) =>
    ({
      PLANNING: 15,
      PRODUCING: 48,
      REVIEW: 80,
      COMPLETED: 100,
      ARCHIVED: 100,
    })[status] || 10;
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
      ><el-radio-button value="grid"
        ><el-icon><Grid /></el-icon></el-radio-button
      ><el-radio-button value="table"
        ><el-icon><List /></el-icon></el-radio-button
    ></el-radio-group>
  </div>
  <div v-loading="loading">
    <div v-if="view === 'grid'" class="project-grid">
      <article
        v-for="(row, i) in rows"
        :key="row.id"
        class="project-card"
        @click="show(row.id)"
      >
        <div class="project-cover" :class="`tone-${i % 6}`">
          <span class="cover-type"
            >FRAMEFLOW / PROJECT {{ String(row.id).padStart(3, "0") }}</span
          >
          <div class="cover-title">{{ row.name }}</div>
          <span class="cover-star">{{
            ["✳", "✦", "◒", "✷", "✽", "↗"][i % 6]
          }}</span>
          <div class="cover-shape"></div>
        </div>
        <div class="project-card-body">
          <div class="project-card-head">
            <h3>{{ row.name }}</h3>
            <StatusBadge :value="row.status" />
          </div>
          <p>
            {{ row.clientName || "工作室原创" }} · 预算 ¥{{ money(row.budget) }}
          </p>
          <div class="project-progress">
            <i :style="{ width: `${progress(row.status)}%` }"></i>
          </div>
          <div class="project-card-footer">
            <span>{{
              row.ownerName ||
              options.users?.find((x) => x.id === row.ownerId)?.name ||
              "团队项目"
            }}</span
            ><span>{{ date(row.dueDate) }} 截止</span>
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
