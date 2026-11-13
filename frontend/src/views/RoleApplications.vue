<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import {
  Plus,
  Refresh,
  Search,
  DocumentChecked,
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import {
  api,
  session,
  hasRole,
  label,
  datetime,
  fail,
  listItems,
  roleDescriptions,
  refreshSession,
} from "../lib/api";
import PageHeader from "../components/PageHeader.vue";
import LoadError from "../components/LoadError.vue";
import StatusBadge from "../components/StatusBadge.vue";
const query = reactive({
  scope: "MINE",
  status: "",
  q: "",
  page: 1,
  pageSize: 10,
});
const rows = ref([]),
  total = ref(0),
  loading = ref(false),
  error = ref("");
const dialog = ref(false),
  saving = ref(false),
  formRef = ref();
const form = reactive({ requestedRole: "", reason: "" });
const reviewDialog = ref(false),
  reviewFormRef = ref(),
  selected = ref(null);
const review = reactive({ action: "approve", reviewNote: "" });
const requestedRoles = ["ADMIN", "PRODUCER", "FINANCE", "REVIEWER"];
const availableRoles = computed(() =>
  requestedRoles.filter((role) => !hasRole(role)),
);
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const data = await api.get("/role-applications", { params: query });
    rows.value = listItems(data);
    total.value = data.total ?? rows.value.length;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
async function refresh() {
  try {
    await refreshSession();
    if (!hasRole("ADMIN") && query.scope === "ALL")
      Object.assign(query, { scope: "MINE", status: "", page: 1 });
    await load();
  } catch (e) {
    fail(e);
  }
}
function changeScope() {
  query.page = 1;
  query.status = query.scope === "ALL" ? "PENDING" : "";
  load();
}
function open() {
  Object.assign(form, {
    requestedRole: availableRoles.value[0] || "",
    reason: "",
  });
  dialog.value = true;
}
async function submit() {
  form.reason = form.reason.trim();
  if (!(await formRef.value.validate().catch(() => false))) return;
  saving.value = true;
  try {
    await api.post("/role-applications", form);
    ElMessage.success("身份申请已提交，管理员审核后生效");
    dialog.value = false;
    Object.assign(query, { scope: "MINE", status: "", page: 1 });
    await load();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
function openReview(row, action) {
  selected.value = row;
  Object.assign(review, { action, reviewNote: "" });
  reviewDialog.value = true;
}
async function reviewApplication() {
  review.reviewNote = review.reviewNote.trim();
  if (!(await reviewFormRef.value.validate().catch(() => false))) return;
  saving.value = true;
  try {
    await api.post(`/role-applications/${selected.value.id}/${review.action}`, {
      reviewNote: review.reviewNote,
    });
    ElMessage.success(
      review.action === "approve"
        ? "申请已批准，身份已生效"
        : "申请已拒绝，审核意见已保存",
    );
    reviewDialog.value = false;
    await load();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>
<template>
  <PageHeader
    title="身份申请"
    subtitle="从创作出发，在合适的岗位承担更多职责。"
    eyebrow="YOUR ROLE, YOUR CONTRIBUTION"
  >
    <el-button :icon="Refresh" @click="refresh">刷新身份与记录</el-button>
    <el-button
      v-if="availableRoles.length"
      type="primary"
      :icon="Plus"
      @click="open"
      >申请新身份</el-button
    >
  </PageHeader>
  <LoadError :message="error" @retry="load" />
  <section class="role-application-intro">
    <div class="role-intro-icon">
      <el-icon><DocumentChecked /></el-icon>
    </div>
    <div>
      <h2>当前身份</h2>
      <div class="role-current-tags">
        <span
          v-for="role in session.user?.roles"
          :key="role"
          class="role-tag"
          >{{ label(role) }}</span
        >
      </div>
      <p>
        根据实际职责申请岗位，审核通过后将增加对应权限。创作者档案与已有作品持续保留。
      </p>
    </div>
  </section>
  <el-tabs
    v-if="hasRole('ADMIN')"
    v-model="query.scope"
    class="section-tabs"
    @tab-change="changeScope"
  >
    <el-tab-pane label="我的申请" name="MINE" /><el-tab-pane
      label="团队申请审批"
      name="ALL"
    />
  </el-tabs>
  <section class="panel table-panel">
    <div class="toolbar">
      <el-input
        v-model="query.q"
        :prefix-icon="Search"
        class="search-input"
        placeholder="搜索申请人、申请说明…"
        clearable
        @clear="
          query.page = 1;
          load();
        "
        @keyup.enter="
          query.page = 1;
          load();
        "
      />
      <el-select
        v-model="query.status"
        class="filter-select"
        placeholder="全部状态"
        clearable
        @change="
          query.page = 1;
          load();
        "
        ><el-option
          v-for="status in ['PENDING', 'APPROVED', 'REJECTED']"
          :key="status"
          :label="label(status)"
          :value="status"
      /></el-select>
      <el-button
        @click="
          query.page = 1;
          load();
        "
        >查询</el-button
      ><span class="spacer"></span
      ><span class="result-count">共 {{ total }} 条申请</span>
    </div>
    <el-table
      v-loading="loading"
      :data="rows"
      :empty-text="
        query.scope === 'ALL'
          ? '暂无符合条件的团队申请'
          : '还没有身份申请，按实际职责申请新岗位'
      "
    >
      <el-table-column
        v-if="query.scope === 'ALL'"
        label="申请人"
        min-width="150"
        ><template #default="{ row }"
          ><span class="table-primary">{{ row.displayName }}</span
          ><span class="table-secondary">@{{ row.username }}</span></template
        ></el-table-column
      >
      <el-table-column label="申请身份" min-width="120"
        ><template #default="{ row }"
          ><span class="role-tag">{{
            label(row.requestedRole)
          }}</span></template
        ></el-table-column
      >
      <el-table-column
        prop="reason"
        label="申请说明"
        min-width="230"
        show-overflow-tooltip
      />
      <el-table-column label="状态" width="110"
        ><template #default="{ row }"
          ><StatusBadge :value="row.status" /></template
      ></el-table-column>
      <el-table-column label="提交时间" min-width="150"
        ><template #default="{ row }">{{
          datetime(row.createdAt)
        }}</template></el-table-column
      >
      <el-table-column label="审核结果" min-width="220"
        ><template #default="{ row }"
          ><span>{{
            row.reviewNote ||
            (row.status === "PENDING" ? "等待管理员审核" : "—")
          }}</span
          ><span v-if="row.reviewedAt" class="table-secondary"
            >{{ row.reviewerName }} · {{ datetime(row.reviewedAt) }}</span
          ></template
        ></el-table-column
      >
      <el-table-column
        v-if="query.scope === 'ALL'"
        label="操作"
        width="150"
        fixed="right"
        ><template #default="{ row }"
          ><div
            v-if="row.status === 'PENDING' && row.userId !== session.user?.id"
            class="table-actions"
          >
            <el-button text type="primary" @click="openReview(row, 'approve')"
              >批准</el-button
            ><el-button text @click="openReview(row, 'reject')">拒绝</el-button>
          </div>
          <span v-else-if="row.status === 'PENDING'" class="muted small"
            >由其他管理员审核</span
          ><span v-else class="muted small">审核完成</span></template
        ></el-table-column
      >
    </el-table>
    <div class="pagination">
      <span>共 {{ total }} 条申请</span
      ><el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="sizes, prev, pager, next"
        @change="load"
      />
    </div>
  </section>
  <el-dialog
    v-model="dialog"
    title="申请新的协作身份"
    width="640px"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" label-position="top">
      <el-form-item
        label="申请身份"
        prop="requestedRole"
        :rules="[{ required: true, message: '请选择身份' }]"
        ><el-select v-model="form.requestedRole" class="full-width"
          ><el-option
            v-for="role in availableRoles"
            :key="role"
            :label="label(role)"
            :value="role" /></el-select
      ></el-form-item>
      <div class="application-role-description">
        {{ roleDescriptions[form.requestedRole] }}
      </div>
      <el-form-item
        label="申请说明"
        prop="reason"
        :rules="[{ required: true, message: '请说明申请岗位的职责与用途' }]"
        ><el-input
          v-model="form.reason"
          type="textarea"
          :rows="5"
          maxlength="1000"
          show-word-limit
          placeholder="说明你准备承担的职责、负责的项目或申请原因，便于管理员审核。"
      /></el-form-item>
      <div class="form-note">
        每次申请一个岗位。提交后可在“我的申请”查看进度与审核意见。
      </div>
    </el-form>
    <template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="submit"
        >提交申请</el-button
      ></template
    >
  </el-dialog>
  <el-dialog
    v-model="reviewDialog"
    :title="review.action === 'approve' ? '批准身份申请' : '拒绝身份申请'"
    width="620px"
    destroy-on-close
  >
    <div v-if="selected" class="application-review-summary">
      <strong
        >{{ selected.displayName }} ·
        {{ label(selected.requestedRole) }}</strong
      >
      <p>{{ selected.reason }}</p>
    </div>
    <el-form ref="reviewFormRef" :model="review" label-position="top"
      ><el-form-item
        :label="review.action === 'reject' ? '拒绝原因' : '审核意见（可选）'"
        prop="reviewNote"
        :rules="[
          {
            required: review.action === 'reject',
            message: '请填写拒绝原因，方便申请人了解结果',
          },
        ]"
        ><el-input
          v-model="review.reviewNote"
          type="textarea"
          :rows="4"
          maxlength="1000"
          show-word-limit
          :placeholder="
            review.action === 'approve'
              ? '可补充岗位职责与授权说明'
              : '请明确需要补充的信息或未通过原因'
          " /></el-form-item
    ></el-form>
    <template #footer
      ><el-button @click="reviewDialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="reviewApplication">{{
        review.action === "approve" ? "批准并授予身份" : "确认拒绝"
      }}</el-button></template
    >
  </el-dialog>
</template>
