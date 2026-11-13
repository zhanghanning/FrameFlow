<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { Plus, Search, DocumentChecked } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  api,
  session,
  hasRole,
  money,
  date,
  datetime,
  label,
  fail,
  listItems,
} from "../lib/api";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import LoadError from "../components/LoadError.vue";
const route = useRoute(),
  query = reactive({ q: "", status: "", page: 1, pageSize: 10 }),
  rows = ref([]),
  total = ref(0),
  options = ref({}),
  loading = ref(false),
  error = ref(""),
  dialog = ref(false),
  saving = ref(false),
  form = reactive({ contractId: null, periodStart: "", periodEnd: "" }),
  formRef = ref(),
  drawer = ref(false),
  detail = ref(null),
  detailBusy = ref(false),
  detailTab = ref("lines"),
  actionBusy = ref(false);
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const data = await api.get("/settlements", { params: query });
    rows.value = listItems(data);
    total.value = data.total ?? rows.value.length;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
function open() {
  Object.assign(form, { contractId: null, periodStart: "", periodEnd: "" });
  dialog.value = true;
}
async function generate() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  if (form.periodEnd < form.periodStart)
    return ElMessage.warning("结算结束日期应晚于开始日期");
  saving.value = true;
  try {
    const result = await api.post("/settlements", form);
    ElMessage.success("结算单已生成，分配明细已固定");
    dialog.value = false;
    await load();
    if (result?.id || result?.settlementId)
      show(result.id || result.settlementId);
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
    detail.value = await api.get(`/settlements/${id}`);
  } catch (e) {
    fail(e);
    drawer.value = false;
  } finally {
    detailBusy.value = false;
  }
}
async function action(kind) {
  let payload = {};
  try {
    if (kind === "reject") {
      const result = await ElMessageBox.prompt(
        "请说明需要调整或核对的事项。",
        "退回结算单",
        {
          confirmButtonText: "退回",
          cancelButtonText: "取消",
          inputType: "textarea",
          inputValidator: (value) => !!value?.trim() || "请填写退回原因",
        },
      );
      payload = { reason: result.value };
    } else
      await ElMessageBox.confirm(
        kind === "submit"
          ? "提交后由另一位审核人核对结算明细。"
          : "批准后，创作者分成将计入可付款余额。",
        kind === "submit" ? "提交结算审批" : "批准收益结算",
        {
          confirmButtonText: kind === "submit" ? "提交审批" : "批准结算",
          cancelButtonText: "取消",
          type: "warning",
        },
      );
    actionBusy.value = true;
    await api.post(`/settlements/${detail.value.id}/${kind}`, payload);
    ElMessage.success(
      kind === "submit"
        ? "已提交审批"
        : kind === "approve"
          ? "结算已批准"
          : "已退回草稿",
    );
    await show(detail.value.id);
    load();
  } catch (e) {
    if (e instanceof Error) fail(e);
  } finally {
    actionBusy.value = false;
  }
}
onMounted(async () => {
  await load();
  try {
    options.value = await api.get("/options");
  } catch (e) {
    fail(e);
  }
  if (route.query.detail) show(route.query.detail);
});
</script>
<template>
  <PageHeader
    title="收益结算"
    subtitle="汇总收入与成本，按合同分配，让创作者的收益落到实处。"
    eyebrow="CREATOR SETTLEMENTS"
    ><el-button
      v-if="hasRole('ADMIN', 'FINANCE')"
      type="primary"
      :icon="Plus"
      @click="open"
      >生成结算单</el-button
    ></PageHeader
  ><LoadError :message="error" @retry="load" />
  <div class="notice-banner">
    <el-icon style="font-size: 17px; flex-shrink: 0"
      ><DocumentChecked /></el-icon
    ><span
      >结算仅纳入已确认收入与已批准成本；每笔明细只分配一次。提交人与审核人分别完成核对，批准后形成创作者应付余额。</span
    >
  </div>
  <div class="panel table-panel">
    <div class="toolbar">
      <el-input
        v-model="query.q"
        class="search-input"
        :prefix-icon="Search"
        placeholder="搜索结算单号、项目…"
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
          v-for="status in ['DRAFT', 'SUBMITTED', 'APPROVED']"
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
      ><span class="result-count">{{ total }} 份结算单</span>
    </div>
    <el-table v-loading="loading" :data="rows" empty-text="暂无结算单"
      ><el-table-column label="结算单 / 项目" min-width="225"
        ><template #default="{ row }"
          ><span class="table-primary">{{ row.settlementNo }}</span
          ><span class="table-secondary">{{
            row.projectName ||
            options.projects?.find((x) => x.id === row.projectId)?.name
          }}</span></template
        ></el-table-column
      ><el-table-column label="结算期间" min-width="200"
        ><template #default="{ row }"
          >{{ date(row.periodStart) }} → {{ date(row.periodEnd) }}</template
        ></el-table-column
      ><el-table-column label="可分配金额" width="145" align="right"
        ><template #default="{ row }"
          ><span
            class="money"
            :class="{ negative: row.distributableAmount < 0 }"
            >¥{{ money(row.distributableAmount) }}</span
          ></template
        ></el-table-column
      ><el-table-column label="工作室分成" width="140" align="right"
        ><template #default="{ row }"
          >¥{{ money(row.studioAmount) }}</template
        ></el-table-column
      ><el-table-column label="状态" width="110"
        ><template #default="{ row }"
          ><StatusBadge :value="row.status" /></template></el-table-column
      ><el-table-column label="操作" width="90" fixed="right"
        ><template #default="{ row }"
          ><el-button text type="primary" @click="show(row.id)"
            >查看明细</el-button
          ></template
        ></el-table-column
      ></el-table
    >
    <div class="pagination">
      <span>共 {{ total }} 份结算单</span
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
    title="生成收益结算单"
    width="560px"
    destroy-on-close
    ><div class="form-note">
      选择合同与结算期间，系统将汇总符合条件的收入、退款与成本，并生成分成明细。
    </div>
    <el-form ref="formRef" :model="form" label-position="top"
      ><el-form-item
        label="分成合同"
        prop="contractId"
        :rules="[{ required: true, message: '请选择生效合同' }]"
        ><el-select v-model="form.contractId" filterable
          ><el-option
            v-for="item in options.contracts?.filter(
              (x) => !x.status || x.status === 'ACTIVE',
            )"
            :key="item.id"
            :label="item.name"
            :value="item.id" /></el-select
      ></el-form-item>
      <div class="form-grid">
        <el-form-item
          label="期间起始日期"
          prop="periodStart"
          :rules="[{ required: true, message: '请选择起始日期' }]"
          ><el-date-picker
            v-model="form.periodStart"
            value-format="YYYY-MM-DD"
            class="full-width" /></el-form-item
        ><el-form-item
          label="期间结束日期"
          prop="periodEnd"
          :rules="[{ required: true, message: '请选择结束日期' }]"
          ><el-date-picker
            v-model="form.periodEnd"
            value-format="YYYY-MM-DD"
            class="full-width"
        /></el-form-item></div></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="generate"
        >计算并生成结算单</el-button
      ></template
    ></el-dialog
  ><el-drawer v-model="drawer" title="结算明细与审批" size="760px"
    ><div v-loading="detailBusy" v-if="detail">
      <div class="panel-title">
        <div>
          <h2 style="font-size: 20px">{{ detail.settlementNo }}</h2>
          <p>
            {{
              detail.projectName ||
              options.projects?.find((x) => x.id === detail.projectId)?.name
            }}
            · {{ date(detail.periodStart) }} — {{ date(detail.periodEnd) }}
          </p>
        </div>
        <StatusBadge :value="detail.status" />
      </div>
      <div class="step-flow">
        <span :class="{ current: detail.status === 'DRAFT' }">01 生成结算</span
        ><i>→</i
        ><span :class="{ current: detail.status === 'SUBMITTED' }"
          >02 提交审批</span
        ><i>→</i
        ><span :class="{ current: detail.status === 'APPROVED' }"
          >03 确认应付</span
        >
      </div>
      <div class="settlement-summary">
        <div>
          <small>确认净收入</small
          ><strong class="money">¥{{ money(detail.totalIncome) }}</strong>
        </div>
        <div>
          <small>分摊成本</small
          ><strong class="money">¥{{ money(detail.totalCost) }}</strong>
        </div>
        <div style="background: #e7efd8">
          <small>可分配金额</small
          ><strong
            class="money"
            :class="{ negative: detail.distributableAmount < 0 }"
            >¥{{ money(detail.distributableAmount) }}</strong
          >
        </div>
      </div>
      <div v-if="detail.rejectReason" class="notice-banner">
        退回原因：{{ detail.rejectReason }}
      </div>
      <el-tabs v-model="detailTab"
        ><el-tab-pane label="分成明细" name="lines"
          ><el-table :data="detail.lines || []" empty-text="暂无分成明细"
            ><el-table-column label="创作者"
              ><template #default="{ row }">{{
                row.creatorName ||
                options.creators?.find((x) => x.id === row.creatorId)?.name
              }}</template></el-table-column
            ><el-table-column label="分成比例" width="120" align="right"
              ><template #default="{ row }"
                >{{ Number(row.rate) }}%</template
              ></el-table-column
            ><el-table-column label="应得金额" width="150" align="right"
              ><template #default="{ row }"
                ><span class="money" :class="{ negative: row.amount < 0 }"
                  >¥{{ money(row.amount) }}</span
                ></template
              ></el-table-column
            ></el-table
          >
          <div class="rate-summary" style="margin-top: 15px">
            <span>工作室分成 · {{ Number(detail.studioRate) }}%</span
            ><strong class="money">¥{{ money(detail.studioAmount) }}</strong>
          </div>
          <p class="hint">
            分成比例与金额保存于结算生成时。负数结算用于抵扣后续收益。
          </p></el-tab-pane
        ><el-tab-pane label="收入明细" name="revenues"
          ><el-table :data="detail.revenues || []" empty-text="暂无收入"
            ><el-table-column
              prop="sourceRef"
              label="来源流水"
              min-width="160"
            /><el-table-column label="日期" width="110"
              ><template #default="{ row }">{{
                date(row.revenueDate)
              }}</template></el-table-column
            ><el-table-column label="类型" width="80"
              ><template #default="{ row }">{{
                label(row.kind)
              }}</template></el-table-column
            ><el-table-column label="净收入" width="130" align="right"
              ><template #default="{ row }"
                >¥{{ money(row.netAmount) }}</template
              ></el-table-column
            ></el-table
          ></el-tab-pane
        ><el-tab-pane label="成本明细" name="costs"
          ><el-table :data="detail.costs || []" empty-text="暂无成本"
            ><el-table-column
              prop="description"
              label="费用说明"
            /><el-table-column
              prop="category"
              label="类别"
              width="90"
            /><el-table-column label="金额" width="130" align="right"
              ><template #default="{ row }"
                >¥{{ money(row.amount) }}</template
              ></el-table-column
            ></el-table
          ></el-tab-pane
        ><el-tab-pane label="审批记录" name="history"
          ><el-timeline style="padding: 20px 5px"
            ><el-timeline-item
              v-for="item in detail.history"
              :key="item.id"
              :timestamp="datetime(item.createdAt)"
              color="#8dab70"
              ><strong style="font-size: 12px; font-weight: 500"
                >{{ label(item.fromStatus) }} →
                {{ label(item.toStatus) }}</strong
              >
              <p class="hint">
                {{
                  item.actorName ||
                  options.users?.find((x) => x.id === item.actorId)?.name ||
                  `成员 #${item.actorId}`
                }}{{ item.reason ? ` · ${item.reason}` : "" }}
              </p></el-timeline-item
            ></el-timeline
          ></el-tab-pane
        ></el-tabs
      >
      <div class="heading-actions" style="margin-top: 24px">
        <el-button
          v-if="detail.status === 'DRAFT' && hasRole('ADMIN', 'FINANCE')"
          type="primary"
          :loading="actionBusy"
          @click="action('submit')"
          >提交审批</el-button
        ><template
          v-if="detail.status === 'SUBMITTED' && hasRole('ADMIN', 'REVIEWER')"
          ><el-button
            type="primary"
            :loading="actionBusy"
            :disabled="detail.submittedBy === session.user?.id"
            @click="action('approve')"
            >批准结算</el-button
          ><el-button :loading="actionBusy" @click="action('reject')"
            >退回修改</el-button
          ><span v-if="detail.submittedBy === session.user?.id" class="hint"
            >请由其他审核人完成批准</span
          ></template
        ><router-link v-if="detail.status === 'APPROVED'" to="/balances"
          ><el-button type="primary">查看应付余额 →</el-button></router-link
        >
      </div>
    </div></el-drawer
  >
</template>
