<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { Plus, Search, Delete } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { api, hasRole, date, label, fail, listItems } from "../lib/api";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import LoadError from "../components/LoadError.vue";
import FinanceGuide from "../components/FinanceGuide.vue";
const query = reactive({ q: "", status: "", page: 1, pageSize: 10 }),
  rows = ref([]),
  total = ref(0),
  options = ref({}),
  loading = ref(false),
  error = ref(""),
  dialog = ref(false),
  saving = ref(false),
  form = reactive({}),
  formRef = ref(),
  drawer = ref(false),
  detail = ref(null);
const rateTotal = computed(
    () =>
      Number(form.studioRate || 0) +
      (form.participants || []).reduce((s, x) => s + Number(x.rate || 0), 0),
  ),
  projectName = (id) =>
    options.value.projects?.find((x) => x.id === id)?.name || "—",
  creatorName = (id) =>
    options.value.creators?.find((x) => x.id === id)?.name || "—";
const projectCreators = computed(() => {
  const members = options.value.projects?.find(item => item.id === form.projectId)?.creatorIds || [];
  return (options.value.creators || []).filter(item => members.includes(item.id));
});
function changeProject() {
  const members = projectCreators.value.map(item => item.id);
  form.participants = form.participants.map(item => ({ ...item, creatorId: members.includes(item.creatorId) ? item.creatorId : null }));
}
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const data = await api.get("/contracts", { params: query });
    rows.value = listItems(data);
    total.value = data.total ?? rows.value.length;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
function open() {
  Object.assign(form, {
    projectId: null,
    name: "",
    startDate: new Date().toISOString().slice(0, 10),
    endDate: "",
    studioRate: 30,
    participants: [{ creatorId: null, rate: 70 }],
  });
  dialog.value = true;
}
async function save() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  if (Math.abs(rateTotal.value - 100) > 0.0001)
    return ElMessage.warning("工作室与创作者分成合计应为100%");
  if (!form.participants.length || form.participants.some((x) => !x.creatorId))
    return ElMessage.warning("请为每一行选择创作者");
  if (
    new Set(form.participants.map((x) => x.creatorId)).size !==
    form.participants.length
  )
    return ElMessage.warning("同一创作者只能参与一次");
  if (form.endDate < form.startDate)
    return ElMessage.warning("结束日期应晚于开始日期");
  saving.value = true;
  try {
    await api.post("/contracts", form);
    ElMessage.success("分成合同已创建");
    dialog.value = false;
    load();
    options.value = await api.get("/options");
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function activate(row) {
  try {
    await ElMessageBox.confirm(
      `激活「${row.name}」？生效后合同日期和分成比例固定，用于期间内的收入与成本。`,
      "激活分成合同",
      {
        confirmButtonText: "激活合同",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
    await api.post(`/contracts/${row.id}/activate`);
    ElMessage.success("合同已生效");
    load();
    options.value = await api.get("/options");
  } catch (e) {
    if (e instanceof Error) fail(e);
  }
}
function show(row) {
  detail.value = row;
  drawer.value = true;
}
onMounted(async () => {
  await load();
  try {
    options.value = await api.get("/options");
  } catch (e) {
    fail(e);
  }
});
</script>
<template>
  <PageHeader
    title="分成合同"
    subtitle="事先约定每一份贡献的价值，按清晰规则完成收益分配。"
    eyebrow="COLLABORATION AGREEMENTS"
    ><el-button
      v-if="hasRole('ADMIN', 'FINANCE')"
      type="primary"
      :icon="Plus"
      @click="open"
      >创建分成合同</el-button
    ></PageHeader
  ><FinanceGuide stage="contracts" /><LoadError :message="error" @retry="load" />
  <div class="panel table-panel">
    <div class="toolbar">
      <el-input
        v-model="query.q"
        class="search-input"
        :prefix-icon="Search"
        placeholder="搜索合同名称…"
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
        ><el-option label="草稿" value="DRAFT" /><el-option
          label="已生效"
          value="ACTIVE" /></el-select
      ><el-button
        @click="
          query.page = 1;
          load();
        "
        >查询</el-button
      ><span class="spacer"></span
      ><span class="result-count">{{ total }} 份合同</span>
    </div>
    <el-table v-loading="loading" :data="rows" empty-text="暂无分成合同"
      ><el-table-column label="合同名称" min-width="220"
        ><template #default="{ row }"
          ><span class="table-primary">{{ row.name }}</span
          ><span class="table-secondary">{{
            row.projectName || projectName(row.projectId)
          }}</span></template
        ></el-table-column
      ><el-table-column label="有效期" min-width="215"
        ><template #default="{ row }"
          >{{ date(row.startDate) }} → {{ date(row.endDate) }}</template
        ></el-table-column
      ><el-table-column label="工作室分成" width="130"
        ><template #default="{ row }"
          ><span class="money">{{ Number(row.studioRate) }}%</span></template
        ></el-table-column
      ><el-table-column label="状态" width="110"
        ><template #default="{ row }"
          ><StatusBadge :value="row.status" /></template></el-table-column
      ><el-table-column label="操作" width="145" fixed="right"
        ><template #default="{ row }"
          ><div class="table-actions">
            <el-button text type="primary" @click="show(row)">查看</el-button
            ><el-button
              v-if="hasRole('ADMIN', 'FINANCE') && row.status === 'DRAFT'"
              text
              type="primary"
              @click="activate(row)"
              >激活</el-button
            >
          </div></template
        ></el-table-column
      ></el-table
    >
    <div class="pagination">
      <span>共 {{ total }} 份分成合同</span
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
    title="创建分成合同"
    width="680px"
    destroy-on-close
    ><el-form ref="formRef" :model="form" label-position="top"
      ><div class="form-grid">
        <el-form-item
          label="合同名称"
          prop="name"
          :rules="[{ required: true, message: '请输入合同名称' }]"
          ><el-input v-model="form.name" /></el-form-item
        ><el-form-item
          label="关联项目"
          prop="projectId"
          :rules="[{ required: true, message: '请选择项目' }]"
          ><el-select v-model="form.projectId" filterable @change="changeProject"
            ><el-option
              v-for="item in options.projects"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select></el-form-item
        ><el-form-item
          label="生效日期"
          prop="startDate"
          :rules="[{ required: true, message: '请选择生效日期' }]"
          ><el-date-picker
            v-model="form.startDate"
            value-format="YYYY-MM-DD"
            class="full-width" /></el-form-item
        ><el-form-item
          label="结束日期"
          prop="endDate"
          :rules="[{ required: true, message: '请选择结束日期' }]"
          ><el-date-picker
            v-model="form.endDate"
            value-format="YYYY-MM-DD"
            class="full-width" /></el-form-item
        ><el-form-item label="工作室分成比例（%）"
          ><el-input-number
            v-model="form.studioRate"
            :min="0"
            :max="100"
            :precision="4"
            :controls="false"
            class="full-width"
        /></el-form-item>
      </div>
      <div class="section-heading" style="margin-top: 5px">
        <h2>参与创作者</h2>
        <el-button
          text
          type="primary"
          :icon="Plus"
          @click="form.participants.push({ creatorId: null, rate: 0 })"
          >添加创作者</el-button
        >
      </div>
      <p class="hint">先选择项目，再从该项目的创作者成员中分配比例。</p>
      <div
        v-for="(participant, i) in form.participants"
        :key="i"
        class="contract-participant"
      >
        <el-select
          v-model="participant.creatorId"
          filterable
          :disabled="!form.projectId"
          placeholder="选择项目成员"
          ><el-option
            v-for="creator in projectCreators"
            :key="creator.id"
            :label="creator.name"
            :value="creator.id" /></el-select
        ><el-input-number
          v-model="participant.rate"
          :min="0"
          :max="100"
          :precision="4"
          :controls="false"
        /><el-button
          text
          :icon="Delete"
          aria-label="移除参与者"
          @click="form.participants.splice(i, 1)"
        />
      </div>
      <div
        class="rate-summary"
        :class="{ invalid: Math.abs(rateTotal - 100) > 0.0001 }"
        style="margin-top: 18px"
      >
        <span>分成比例合计 · 工作室 + 创作者</span
        ><strong>{{ Number(rateTotal.toFixed(4)) }} / 100%</strong>
      </div>
      <p class="hint">
        结算基数 = 确认净收入 −
        批准成本。创作者按合同分成，金额取分，尾差归工作室。
      </p></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save"
        >保存合同草稿</el-button
      ></template
    ></el-dialog
  ><el-drawer v-model="drawer" title="分成合同详情" size="540px"
    ><div v-if="detail">
      <div class="panel-title">
        <h2 style="font-size: 20px">{{ detail.name }}</h2>
        <StatusBadge :value="detail.status" />
      </div>
      <dl class="detail-grid">
        <div class="wide">
          <dt>关联项目</dt>
          <dd>{{ detail.projectName || projectName(detail.projectId) }}</dd>
        </div>
        <div>
          <dt>生效日期</dt>
          <dd>{{ date(detail.startDate) }}</dd>
        </div>
        <div>
          <dt>结束日期</dt>
          <dd>{{ date(detail.endDate) }}</dd>
        </div>
      </dl>
      <h3 style="font-size: 13px; margin-bottom: 16px">分成规则</h3>
      <div class="rate-summary">
        <span>工作室</span><strong>{{ Number(detail.studioRate) }}%</strong>
      </div>
      <el-table
        :data="detail.participants || []"
        empty-text="合同参与者信息尚未返回"
        ><el-table-column label="创作者"
          ><template #default="{ row }">{{
            row.creatorName || row.name || creatorName(row.creatorId)
          }}</template></el-table-column
        ><el-table-column label="分成比例" align="right"
          ><template #default="{ row }"
            >{{ Number(row.rate) }}%</template
          ></el-table-column
        ></el-table
      >
      <p class="hint">
        合同生效后用于收入确认与成本审批。结算单保留当次分配比例与金额。
      </p>
    </div></el-drawer
  >
</template>
