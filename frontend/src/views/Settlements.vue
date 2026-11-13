<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { Plus, Search } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { api, session, hasRole, money, date, datetime, label, listItems } from "../lib/api";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import LoadError from "../components/LoadError.vue";
import FinanceGuide from "../components/FinanceGuide.vue";
const route = useRoute(),
  query = reactive({ q: "", status: "", page: 1, pageSize: 10 }),
  rows = ref([]), total = ref(0), options = ref({}), loading = ref(false), error = ref(""),
  optionsLoading = ref(false), optionsError = ref(""), dialog = ref(false), saving = ref(false),
  dialogMode = ref("create"), dialogError = ref(""),
  form = reactive({ contractId: null, periodStart: "", periodEnd: "", reason: "" }),
  formRef = ref(), drawer = ref(false), detail = ref(null), detailBusy = ref(false),
  detailError = ref(""), detailTab = ref("lines"), actionBusy = ref(false), revisionId = ref("current");
const activeContracts = computed(() => (options.value.contracts || []).filter(item => item.status === "ACTIVE"));
const selectedRevision = computed(() => detail.value?.revisions?.find(item => item.id === revisionId.value));
const snapshot = computed(() => selectedRevision.value?.snapshot || detail.value);
const operationNames = { EDIT: "修改重算", RECALCULATE: "按原条件重算", CANCEL: "撤销结算" };
const dialogTitles = { create: "生成收益结算单", edit: "修改结算并重算", recalculate: "按当前条件重算", cancel: "撤销结算单" };
const dialogButtons = { create: "计算并生成结算单", edit: "保存修改并重算", recalculate: "确认重算", cancel: "确认撤销" };
const canAdjust = computed(() => detail.value?.status === "DRAFT" && hasRole("ADMIN", "FINANCE"));
const operationHistory = computed(() => [
  ...(detail.value?.history || []).filter(item => item.toStatus !== "CANCELLED" || !detail.value?.revisions?.some(revision => revision.action === "CANCEL")).map(item => ({ ...item, key: `approval-${item.id}`, title: `${label(item.fromStatus)} → ${label(item.toStatus)}` })),
  ...(detail.value?.revisions || []).map(item => ({ ...item, key: `revision-${item.id}`, title: operationNames[item.action] || item.action, revisionId: item.id })),
].sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt))));
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const data = await api.get("/settlements", { params: query });
    rows.value = listItems(data);
    total.value = data.total ?? rows.value.length;
  } catch (e) { error.value = e.message; }
  finally { loading.value = false; }
}
async function loadOptions() {
  optionsLoading.value = true;
  optionsError.value = "";
  try { options.value = await api.get("/options"); }
  catch (e) { optionsError.value = e.message; }
  finally { optionsLoading.value = false; }
}
function open(mode = "create") {
  dialogMode.value = mode;
  dialogError.value = "";
  const current = mode === "create" ? null : detail.value;
  Object.assign(form, {
    contractId: current?.contractId || null,
    periodStart: current?.periodStart ? date(current.periodStart) : "",
    periodEnd: current?.periodEnd ? date(current.periodEnd) : "",
    reason: "",
  });
  dialog.value = true;
  if (["create", "edit"].includes(mode)) loadOptions();
}
async function saveSettlement() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  dialogError.value = "";
  if (["create", "edit"].includes(dialogMode.value) && form.periodEnd < form.periodStart) {
    dialogError.value = "结算结束日期应不早于开始日期";
    return;
  }
  saving.value = true;
  try {
    const mode = dialogMode.value;
    const fields = { contractId: form.contractId, periodStart: form.periodStart, periodEnd: form.periodEnd };
    const reason = form.reason.trim();
    const id = detail.value?.id;
    let result;
    if (mode === "create") result = await api.post("/settlements", fields);
    else if (mode === "edit") result = await api.put(`/settlements/${id}`, { ...fields, reason });
    else result = await api.post(`/settlements/${id}/${mode}`, { reason });
    dialog.value = false;
    ElMessage.success({ create: "结算单已生成", edit: "结算条件已修改，原单号已重算", recalculate: "结算已按当前条件重算", cancel: "结算已撤销，收支来源已释放" }[mode]);
    await Promise.all([load(), show(result?.id || result?.settlementId || id)]);
  } catch (e) { dialogError.value = e.message; }
  finally { saving.value = false; }
}
async function show(id, reset = true) {
  drawer.value = true;
  detailBusy.value = true;
  detailError.value = "";
  if (reset) { revisionId.value = "current"; detailTab.value = "lines"; detail.value = null; }
  try { detail.value = await api.get(`/settlements/${id}`); }
  catch (e) { detailError.value = e.message; }
  finally { detailBusy.value = false; }
}
function viewRevision(id) {
  revisionId.value = id;
  detailTab.value = "lines";
}
async function action(kind) {
  let payload = {};
  detailError.value = "";
  try {
    if (kind === "reject") {
      const result = await ElMessageBox.prompt("请说明需要调整或核对的事项。", "退回结算单", {
        confirmButtonText: "退回", cancelButtonText: "取消", inputType: "textarea",
        inputValidator: value => (!!value?.trim() && value.trim().length <= 1000) || "请填写 1 至 1000 字的退回原因",
      });
      payload = { reason: result.value.trim() };
    } else {
      await ElMessageBox.confirm(
        kind === "submit" ? "提交后由另一位审核人核对结算明细。" : "批准后，创作者分成将计入可付款余额。",
        kind === "submit" ? "提交结算审批" : "批准收益结算",
        { confirmButtonText: kind === "submit" ? "提交审批" : "批准结算", cancelButtonText: "取消", type: "warning" },
      );
    }
    actionBusy.value = true;
    const id = detail.value.id;
    await api.post(`/settlements/${id}/${kind}`, payload);
    ElMessage.success({ submit: "已提交审批", approve: "结算已批准", reject: "已退回草稿，可修改重算或撤销" }[kind]);
    await Promise.all([show(id, false), load()]);
  } catch (e) { if (e instanceof Error) detailError.value = e.message; }
  finally { actionBusy.value = false; }
}
onMounted(async () => {
  await Promise.all([load(), loadOptions()]);
  if (route.query.detail) await show(route.query.detail);
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
      @click="open()"
      >生成结算单</el-button
    ></PageHeader
  ><FinanceGuide stage="settlements" /><LoadError :message="error" @retry="load" />
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
          v-for="status in ['DRAFT', 'SUBMITTED', 'APPROVED', 'CANCELLED']"
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
  <el-dialog v-model="dialog" :title="dialogTitles[dialogMode]" width="580px" destroy-on-close :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
    <p class="form-note" v-if="dialogMode === 'create'">选择生效合同与结算期间，汇总已确认收入、退款与已批准成本，并生成分成明细。</p>
    <p class="form-note" v-else-if="dialogMode === 'edit'">修改合同或期间后重新计算，沿用原结算单号。金额由已确认收入与已批准成本计算；修改前的金额、来源与分成明细保存为历史快照。</p>
    <p class="form-note" v-else-if="dialogMode === 'recalculate'">按当前合同与期间重新汇总可纳入的收入和成本，沿用原单号并保存重算前的历史快照。</p>
    <p class="form-note" v-else>撤销后释放本单占用的收入与成本，可重新生成结算；本单保留金额与分成记录，状态改为“已撤销”。</p>
    <p v-if="dialogMode !== 'create'" class="settlement-dialog-context">{{ detail?.settlementNo }} · {{ detail?.contractName || options.contracts?.find(item => item.id === detail?.contractId)?.name }}<br />{{ date(detail?.periodStart) }} — {{ date(detail?.periodEnd) }}</p>
    <el-alert v-if="dialogError" :title="dialogError" type="error" :closable="false" show-icon class="settlement-feedback" />
    <LoadError v-if="['create', 'edit'].includes(dialogMode)" :message="optionsError" @retry="loadOptions" />
    <el-form ref="formRef" :model="form" label-position="top" :disabled="saving">
      <template v-if="['create', 'edit'].includes(dialogMode)">
        <el-form-item label="分成合同" prop="contractId" :rules="[{ required: true, message: '请选择生效合同' }]">
          <el-select v-model="form.contractId" filterable :loading="optionsLoading" placeholder="选择生效合同">
            <el-option v-for="item in activeContracts" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="期间起始日期" prop="periodStart" :rules="[{ required: true, message: '请选择起始日期' }]"><el-date-picker v-model="form.periodStart" value-format="YYYY-MM-DD" class="full-width" /></el-form-item>
          <el-form-item label="期间结束日期" prop="periodEnd" :rules="[{ required: true, message: '请选择结束日期' }]"><el-date-picker v-model="form.periodEnd" value-format="YYYY-MM-DD" class="full-width" /></el-form-item>
        </div>
      </template>
      <el-form-item v-if="dialogMode !== 'create'" :label="dialogMode === 'cancel' ? '撤销理由' : '调整理由'" prop="reason" :rules="[{ required: true, whitespace: true, message: '请填写本次操作理由' }]">
        <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="说明需要调整的事项及依据，供后续审核查阅" />
      </el-form-item>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="['create', 'edit'].includes(dialogMode) && (optionsLoading || !!optionsError)" @click="saveSettlement">{{ dialogButtons[dialogMode] }}</el-button></template>
  </el-dialog>
  <el-drawer v-model="drawer" title="结算明细与审批" size="min(840px, 100vw)">
    <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" show-icon class="settlement-feedback" />
    <div v-loading="detailBusy" class="settlement-detail" :aria-busy="detailBusy">
    <template v-if="detail && snapshot">
      <div class="settlement-snapshot-picker">
        <label for="settlement-snapshot">查阅内容</label>
        <el-select id="settlement-snapshot" v-model="revisionId" aria-label="选择结算快照">
          <el-option value="current" :label="detail.status === 'CANCELLED' ? '撤销留档' : '当前结算'" />
          <el-option v-for="item in detail.revisions || []" :key="item.id" :value="item.id" :label="`${datetime(item.createdAt)} · ${operationNames[item.action] || item.action}前`" />
        </el-select>
      </div>
      <div v-if="selectedRevision" class="settlement-record-note">
        <strong>历史快照 · {{ operationNames[selectedRevision.action] }}前</strong>
        <p>{{ datetime(selectedRevision.createdAt) }} · {{ selectedRevision.actorName || `成员 #${selectedRevision.actorId}` }} · {{ selectedRevision.reason }}</p>
        <el-button text type="primary" @click="revisionId = 'current'">返回{{ detail.status === 'CANCELLED' ? '撤销留档' : '当前结算' }}</el-button>
      </div>
      <div v-else-if="detail.status === 'CANCELLED'" class="settlement-record-note">
        <strong>已撤销 · 来源已释放</strong>
        <p>{{ detail.cancelReason }}<br />{{ datetime(detail.cancelledAt) }} · 本页金额及来源为撤销时留档，可用于核对历史分账。</p>
      </div>
      <div v-else-if="detail.status === 'DRAFT' && detail.rejectReason" class="settlement-record-note">
        <strong>退回原因</strong><p>{{ detail.rejectReason }}</p>
        <p v-if="hasRole('ADMIN', 'FINANCE')">请核对来源数据，修改条件或按原条件重算后重新提交；无需继续的结算可撤销。</p>
      </div>
      <div class="panel-title">
        <div>
          <h2 style="font-size: 20px">{{ snapshot.settlementNo }}</h2>
          <p>
            {{
              snapshot.projectName ||
              options.projects?.find((x) => x.id === snapshot.projectId)?.name
            }}
            · {{ date(snapshot.periodStart) }} — {{ date(snapshot.periodEnd) }}
          </p>
          <p class="hint">合同 · {{ snapshot.contractName || options.contracts?.find(item => item.id === snapshot.contractId)?.name || '—' }}</p>
        </div>
        <StatusBadge :value="snapshot.status" />
      </div>
      <div v-if="!selectedRevision && detail.status !== 'CANCELLED'" class="step-flow">
        <span :class="{ current: snapshot.status === 'DRAFT' }">01 生成结算</span
        ><i>→</i
        ><span :class="{ current: snapshot.status === 'SUBMITTED' }"
          >02 提交审批</span
        ><i>→</i
        ><span :class="{ current: snapshot.status === 'APPROVED' }"
          >03 确认应付</span
        >
      </div>
      <div class="settlement-summary">
        <div>
          <small>确认净收入</small
          ><strong class="money">¥{{ money(snapshot.totalIncome) }}</strong>
        </div>
        <div>
          <small>分摊成本</small
          ><strong class="money">¥{{ money(snapshot.totalCost) }}</strong>
        </div>
        <div class="settlement-distributable">
          <small>可分配金额</small
          ><strong
            class="money"
            :class="{ negative: snapshot.distributableAmount < 0 }"
            >¥{{ money(snapshot.distributableAmount) }}</strong
          >
        </div>
      </div>
      <el-tabs v-model="detailTab"
        ><el-tab-pane label="分成明细" name="lines"
          ><el-table :data="snapshot.lines || []" empty-text="暂无分成明细"
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
            <span>工作室分成 · {{ Number(snapshot.studioRate) }}%</span
            ><strong class="money">¥{{ money(snapshot.studioAmount) }}</strong>
          </div>
          <p class="hint">
            分成比例、金额与来源保存在当前选中的快照中。负数结算用于抵扣后续收益。
          </p></el-tab-pane
        ><el-tab-pane label="收入明细" name="revenues"
          ><el-table :data="snapshot.revenues || []" empty-text="暂无收入"
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
          ><el-table :data="snapshot.costs || []" empty-text="暂无成本"
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
        ><el-tab-pane label="操作记录" name="history">
          <el-empty v-if="!operationHistory.length" description="暂无操作记录" :image-size="50" />
          <el-timeline class="settlement-history">
            <el-timeline-item v-for="item in operationHistory" :key="item.key" :timestamp="datetime(item.createdAt)" color="var(--accent)">
              <strong>{{ item.title }}</strong>
              <p class="hint">{{ item.actorName || options.users?.find(x => x.id === item.actorId)?.name || `成员 #${item.actorId}` }}{{ item.reason ? ` · ${item.reason}` : '' }}</p>
              <el-button v-if="item.revisionId" text type="primary" @click="viewRevision(item.revisionId)">查看变更前的分成与来源</el-button>
            </el-timeline-item>
          </el-timeline>
        </el-tab-pane></el-tabs>
      <div v-if="!selectedRevision" class="heading-actions settlement-actions">
        <el-button
          v-if="canAdjust"
          type="primary"
          :loading="actionBusy"
          :disabled="detailBusy"
          @click="action('submit')"
          >提交审批</el-button
        ><template v-if="canAdjust">
          <el-button :disabled="actionBusy || detailBusy" @click="open('edit')">修改并重算</el-button>
          <el-button :disabled="actionBusy || detailBusy" @click="open('recalculate')">重新计算</el-button>
          <el-button text class="settlement-cancel-action" :disabled="actionBusy || detailBusy" @click="open('cancel')">撤销结算</el-button>
        </template><template
          v-if="detail.status === 'SUBMITTED' && hasRole('ADMIN', 'REVIEWER')"
          ><el-button
            type="primary"
            :loading="actionBusy"
            :disabled="detailBusy || detail.submittedBy === session.user?.id"
            @click="action('approve')"
            >批准结算</el-button
          ><el-button :loading="actionBusy" :disabled="detailBusy || detail.submittedBy === session.user?.id" @click="action('reject')"
            >退回修改</el-button
          ><span v-if="detail.submittedBy === session.user?.id" class="hint"
            >请由其他审核人完成批准或退回</span
          ></template
        ><router-link v-if="detail.status === 'APPROVED'" to="/balances"
          ><el-button type="primary">查看应付余额 →</el-button></router-link
        >
      </div>
    </template></div></el-drawer>
</template>

<style scoped>
.settlement-detail { min-height: 100px; }
.settlement-feedback { margin: 0 0 16px; }
.settlement-dialog-context { color: var(--muted); font-size: 12px; line-height: 1.8; }
.settlement-snapshot-picker { display: flex; align-items: center; gap: 12px; margin-bottom: 18px; }
.settlement-snapshot-picker > label { color: var(--muted); font-size: 12px; flex-shrink: 0; }
.settlement-snapshot-picker :deep(.el-select) { flex: 1; }
.settlement-record-note { margin-bottom: 20px; padding: 13px 15px; border: 1px solid var(--line); border-left: 3px solid var(--accent); color: var(--ink); background: var(--paper); }
.settlement-record-note strong { font-size: 13px; }
.settlement-record-note p { margin: 7px 0 0; font-size: 12px; line-height: 1.7; color: var(--muted); overflow-wrap: anywhere; }
.settlement-record-note :deep(.el-button) { padding: 5px 0 0; height: auto; }
.settlement-distributable { border-bottom: 2px solid var(--accent); }
.settlement-history { padding: 20px 5px; }
.settlement-history strong { color: var(--ink); font-size: 12px; font-weight: 600; }
.settlement-actions { margin-top: 24px; padding-top: 18px; border-top: 1px solid var(--line); flex-wrap: wrap; }
.settlement-cancel-action { color: var(--muted); }
</style>
