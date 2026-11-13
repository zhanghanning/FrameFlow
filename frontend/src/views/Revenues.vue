<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import {
  Plus,
  Search,
  Upload,
  Download,
  Document,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  api,
  hasRole,
  money,
  date,
  label,
  fail,
  listItems,
  download,
} from "../lib/api";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import LoadError from "../components/LoadError.vue";
const query = reactive({
    q: "",
    status: "",
    projectId: "",
    page: 1,
    pageSize: 10,
  }),
  rows = ref([]),
  total = ref(0),
  options = ref({}),
  publications = ref([]),
  loading = ref(false),
  error = ref(""),
  dialog = ref(false),
  saving = ref(false),
  form = reactive({}),
  formRef = ref(),
  importDialog = ref(false),
  importBusy = ref(false),
  preview = ref(null),
  csvInput = ref(),
  csvFile = ref(null),
  exportBusy = ref(false);
const net = computed(() =>
  form.kind === "REFUND"
    ? -Number(form.grossAmount || 0)
    : Number(form.grossAmount || 0) - Number(form.feeAmount || 0),
);
const publicationName = (row) =>
  `${row.workTitle || row.title || options.value.works?.find((x) => x.id === row.workId)?.name || "作品"} · ${row.accountName || options.value.platformAccounts?.find((x) => x.id === row.platformAccountId)?.name || row.externalId}`;
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const data = await api.get("/revenues", { params: query });
    rows.value = listItems(data);
    total.value = data.total ?? rows.value.length;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
function open(original = null) {
  Object.assign(form, {
    publicationId: original?.publicationId || null,
    sourceRef: "",
    revenueDate: new Date().toISOString().slice(0, 10),
    kind: original ? "REFUND" : "INCOME",
    grossAmount: 0,
    feeAmount: 0,
    originalRevenueId: original?.id || null,
    note: "",
  });
  dialog.value = true;
}
async function save() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  if (form.grossAmount <= 0) return ElMessage.warning("收入或退款金额需大于0");
  if (form.kind === "INCOME" && form.feeAmount > form.grossAmount)
    return ElMessage.warning("平台费用应小于收入金额");
  saving.value = true;
  try {
    await api.post("/revenues", {
      ...form,
      feeAmount: form.kind === "REFUND" ? 0 : form.feeAmount,
    });
    ElMessage.success("收入记录已保存为草稿");
    dialog.value = false;
    load();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function confirm(row) {
  try {
    await ElMessageBox.confirm(
      `确认${row.kind === "REFUND" ? "退款" : "收入"} ¥${money(row.netAmount)}？系统按发生日期关联分成合同，确认后金额固定。`,
      "确认收入记录",
      {
        confirmButtonText: "确认入账",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
    await api.post(`/revenues/${row.id}/confirm`);
    ElMessage.success("记录已确认");
    load();
  } catch (e) {
    if (e instanceof Error) fail(e);
  }
}
async function previewCsv(event) {
  csvFile.value = event.target.files?.[0];
  if (!csvFile.value) return;
  preview.value = null;
  importBusy.value = true;
  try {
    const data = new FormData();
    data.append("file", csvFile.value);
    preview.value = await api.post("/revenues/import/preview", data);
  } catch (e) {
    fail(e);
  } finally {
    importBusy.value = false;
  }
}
async function importCsv() {
  const valid = preview.value.rows.filter((row) => row.valid);
  if (!valid.length) return;
  importBusy.value = true;
  try {
    const result = await api.post("/revenues/import", { rows: valid });
    ElMessage.success(
      `成功导入 ${result.inserted} 条，重复 ${result.duplicates || 0} 条`,
    );
    importDialog.value = false;
    preview.value = null;
    load();
  } catch (e) {
    fail(e);
  } finally {
    importBusy.value = false;
  }
}
async function exportCsv() {
  exportBusy.value = true;
  try {
    await download(
      `/revenues/export?${new URLSearchParams(query)}`,
      "帧序收入明细.csv",
    );
  } catch (e) {
    fail(e);
  } finally {
    exportBusy.value = false;
  }
}
function template() {
  const content =
    "\uFEFFpublicationId,sourceRef,revenueDate,grossAmount,feeAmount,note\n";
  const url = URL.createObjectURL(
    new Blob([content], { type: "text/csv;charset=utf-8" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = "收入导入模板.csv";
  a.click();
  URL.revokeObjectURL(url);
}
onMounted(async () => {
  await load();
  try {
    const data = await Promise.all([
      api.get("/options"),
      api.get("/publications", { params: { pageSize: 100 } }),
    ]);
    options.value = data[0];
    publications.value = listItems(data[1]);
  } catch (e) {
    fail(e);
  }
});
</script>
<template>
  <PageHeader
    title="收入与对账"
    subtitle="从平台流水到确认入账，让每一笔创作收入清楚可查。"
    eyebrow="REVENUE MANAGEMENT"
    ><el-button :icon="Download" :loading="exportBusy" @click="exportCsv"
      >导出明细</el-button
    ><el-button
      v-if="hasRole('ADMIN', 'FINANCE')"
      :icon="Upload"
      @click="importDialog = true"
      >导入流水</el-button
    ><el-button
      v-if="hasRole('ADMIN', 'FINANCE')"
      type="primary"
      :icon="Plus"
      @click="open()"
      >登记收入</el-button
    ></PageHeader
  ><LoadError :message="error" @retry="load" />
  <div class="notice-banner">
    <el-icon style="font-size: 17px; flex-shrink: 0"><Document /></el-icon
    ><span
      >收入先登记为草稿，核对后确认入账。退款单关联原收入，并按原合同参与后续结算。</span
    >
  </div>
  <div class="panel table-panel">
    <div class="toolbar">
      <el-input
        v-model="query.q"
        class="search-input"
        :prefix-icon="Search"
        placeholder="搜索作品、流水号…"
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
        ><el-option label="待确认" value="DRAFT" /><el-option
          label="已确认"
          value="CONFIRMED" /></el-select
      ><el-select
        v-model="query.projectId"
        class="filter-select"
        placeholder="全部项目"
        clearable
        filterable
        @change="
          query.page = 1;
          load();
        "
        ><el-option
          v-for="item in options.projects"
          :key="item.id"
          :label="item.name"
          :value="item.id" /></el-select
      ><el-button
        @click="
          query.page = 1;
          load();
        "
        >查询</el-button
      ><span class="spacer"></span
      ><span class="result-count">{{ total }} 条记录</span>
    </div>
    <el-table v-loading="loading" :data="rows" empty-text="暂无收入记录"
      ><el-table-column label="作品 / 来源流水" min-width="215"
        ><template #default="{ row }"
          ><span class="table-primary">{{
            row.workTitle ||
            publications.find((x) => x.id === row.publicationId)?.workTitle ||
            row.sourceRef
          }}</span
          ><span class="table-secondary"
            >{{ row.sourceRef }} ·
            {{ row.platformName || row.accountName }}</span
          ></template
        ></el-table-column
      ><el-table-column label="发生日期" width="110"
        ><template #default="{ row }">{{
          date(row.revenueDate)
        }}</template></el-table-column
      ><el-table-column label="类型" width="80"
        ><template #default="{ row }"
          ><span :class="row.kind === 'REFUND' ? 'negative' : 'positive'">{{
            label(row.kind)
          }}</span></template
        ></el-table-column
      ><el-table-column label="原始金额" width="115" align="right"
        ><template #default="{ row }"
          >¥{{ money(row.grossAmount) }}</template
        ></el-table-column
      ><el-table-column label="平台费用" width="105" align="right"
        ><template #default="{ row }"
          >¥{{ money(row.feeAmount) }}</template
        ></el-table-column
      ><el-table-column label="净收入" width="125" align="right"
        ><template #default="{ row }"
          ><span class="money" :class="{ negative: row.netAmount < 0 }"
            >¥{{ money(row.netAmount) }}</span
          ></template
        ></el-table-column
      ><el-table-column label="状态" width="100"
        ><template #default="{ row }"
          ><StatusBadge :value="row.status" /></template></el-table-column
      ><el-table-column
        v-if="hasRole('ADMIN', 'FINANCE')"
        label="操作"
        width="115"
        fixed="right"
        ><template #default="{ row }"
          ><el-button
            v-if="row.status === 'DRAFT'"
            text
            type="primary"
            @click="confirm(row)"
            >确认入账</el-button
          ><el-button v-else-if="row.kind === 'INCOME'" text @click="open(row)"
            >登记退款</el-button
          ><span v-else class="muted small">已入账</span></template
        ></el-table-column
      ></el-table
    >
    <div class="pagination">
      <span>共 {{ total }} 条收入与退款记录</span
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
    :title="form.kind === 'REFUND' ? '登记关联退款' : '登记创作收入'"
    width="660px"
    destroy-on-close
    ><el-form ref="formRef" :model="form" label-position="top"
      ><div class="form-grid">
        <el-form-item
          label="关联发布记录"
          prop="publicationId"
          class="wide"
          :rules="[{ required: true, message: '请选择发布记录' }]"
          ><el-select
            v-model="form.publicationId"
            filterable
            :disabled="form.kind === 'REFUND'"
            ><el-option
              v-for="row in publications"
              :key="row.id"
              :label="publicationName(row)"
              :value="row.id" /></el-select></el-form-item
        ><el-form-item
          label="来源流水号"
          prop="sourceRef"
          :rules="[{ required: true, message: '请输入来源流水号' }]"
          ><el-input
            v-model="form.sourceRef"
            placeholder="平台账单中的唯一流水号" /></el-form-item
        ><el-form-item
          label="发生日期"
          prop="revenueDate"
          :rules="[{ required: true, message: '请选择日期' }]"
          ><el-date-picker
            v-model="form.revenueDate"
            value-format="YYYY-MM-DD"
            class="full-width" /></el-form-item
        ><el-form-item
          :label="
            form.kind === 'REFUND' ? '退款金额（正数）' : '原始收入（元）'
          "
          ><el-input-number
            v-model="form.grossAmount"
            :min="0"
            :precision="2"
            :controls="false"
            class="full-width" /></el-form-item
        ><el-form-item label="平台费用（元）"
          ><el-input-number
            v-model="form.feeAmount"
            :min="0"
            :precision="2"
            :controls="false"
            :disabled="form.kind === 'REFUND'"
            class="full-width" /></el-form-item
        ><el-form-item label="备注" class="wide"
          ><el-input v-model="form.note" type="textarea" :rows="2"
        /></el-form-item>
      </div>
      <div class="rate-summary">
        <span>{{ form.kind === "REFUND" ? "本次退款扣减" : "本次净收入" }}</span
        ><strong class="money" :class="{ negative: net < 0 }"
          >¥{{ money(net) }}</strong
        >
      </div></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save"
        >保存为草稿</el-button
      ></template
    ></el-dialog
  ><el-dialog
    v-model="importDialog"
    title="导入平台收入流水"
    width="920px"
    destroy-on-close
    ><div class="csv-info">
      上传 UTF-8 编码
      CSV，先预览校验，再提交导入。相同账号、相同类型的流水号用于识别重复记录。<code
        >publicationId,sourceRef,revenueDate,grossAmount,feeAmount,note</code
      ><el-button
        size="small"
        text
        type="primary"
        :icon="Download"
        @click="template"
        >下载空白模板</el-button
      >
    </div>
    <input
      ref="csvInput"
      type="file"
      accept=".csv,text/csv"
      style="display: none"
      @change="previewCsv"
    /><el-button
      :icon="Upload"
      :loading="importBusy"
      @click="csvInput.click()"
      >{{ csvFile ? "重新选择 CSV" : "选择 CSV 文件" }}</el-button
    ><span v-if="csvFile" class="small muted" style="margin-left: 12px">{{
      csvFile.name
    }}</span>
    <div v-if="preview">
      <div class="import-counts">
        <span
          >可导入 <b>{{ preview.validCount }}</b> 条</span
        ><span
          >错误 <b>{{ preview.invalidCount }}</b> 条</span
        ><span
          >重复 <b>{{ preview.duplicateCount }}</b> 条</span
        >
      </div>
      <el-table
        :data="preview.rows"
        max-height="320"
        empty-text="文件中没有数据"
        ><el-table-column
          prop="rowNumber"
          label="行"
          width="55"
        /><el-table-column
          prop="publicationId"
          label="发布ID"
          width="75"
        /><el-table-column
          prop="sourceRef"
          label="来源流水号"
          min-width="140"
        /><el-table-column
          prop="revenueDate"
          label="发生日期"
          width="110"
        /><el-table-column
          prop="grossAmount"
          label="原始收入"
          width="100"
        /><el-table-column
          prop="feeAmount"
          label="费用"
          width="80"
        /><el-table-column label="校验结果" min-width="180"
          ><template #default="{ row }"
            ><span :class="row.valid ? 'positive' : 'negative'">{{
              row.valid ? "可以导入" : row.error
            }}</span></template
          ></el-table-column
        ></el-table
      >
    </div>
    <template #footer
      ><el-button @click="importDialog = false">取消</el-button
      ><el-button
        type="primary"
        :loading="importBusy"
        :disabled="!preview?.validCount"
        @click="importCsv"
        >导入 {{ preview?.validCount || 0 }} 条有效记录</el-button
      ></template
    ></el-dialog
  >
</template>
