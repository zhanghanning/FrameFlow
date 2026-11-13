<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { Plus, Search, CreditCard } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { api, hasRole, money, date, fail, listItems } from "../lib/api";
import PageHeader from "../components/PageHeader.vue";
import LoadError from "../components/LoadError.vue";
const balances = ref([]),
  payments = ref([]),
  loading = ref(false),
  error = ref(""),
  dialog = ref(false),
  saving = ref(false),
  form = reactive({}),
  formRef = ref(),
  query = reactive({ q: "", page: 1, pageSize: 10 }),
  total = ref(0);
const sums = computed(() =>
    balances.value.reduce(
      (s, x) => ({
        earned: s.earned + Number(x.earned || 0),
        paid: s.paid + Number(x.paid || 0),
        balance: s.balance + Number(x.balance || 0),
      }),
      { earned: 0, paid: 0, balance: 0 },
    ),
  ),
  selected = computed(() =>
    balances.value.find((x) => x.creatorId === form.creatorId),
  );
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const data = await Promise.all([
      api.get("/balances"),
      api.get("/payments", { params: query }),
    ]);
    balances.value = listItems(data[0]);
    payments.value = listItems(data[1]);
    total.value = data[1].total ?? payments.value.length;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
function open(row = null) {
  Object.assign(form, {
    creatorId: row?.creatorId || null,
    amount: 0,
    paidAt: new Date().toISOString().slice(0, 10),
    reference: "",
    note: "",
  });
  dialog.value = true;
}
async function save() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  if (form.amount <= 0 || form.amount > Number(selected.value?.balance || 0))
    return ElMessage.warning("付款金额应大于0且不超过创作者可付余额");
  saving.value = true;
  try {
    await api.post("/payments", form);
    ElMessage.success("线下付款已登记，余额已更新");
    dialog.value = false;
    load();
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
    title="余额与付款"
    subtitle="每一份创作的价值，及时兑现。统一管理创作者应付余额与付款记录。"
    eyebrow="BALANCES & PAYMENTS"
    ><el-button
      v-if="hasRole('ADMIN', 'FINANCE')"
      type="primary"
      :icon="Plus"
      @click="open()"
      >登记线下付款</el-button
    ></PageHeader
  ><LoadError :message="error" @retry="load" />
  <div v-loading="loading">
    <div class="metric-strip balance-metrics">
      <div class="metric-card">
        <div class="metric-top">已批准创作者收益</div>
        <div class="metric-value">
          <span class="currency">¥</span>{{ money(sums.earned) }}
        </div>
        <div class="metric-caption">包含退款等负数结算</div>
      </div>
      <div class="metric-card">
        <div class="metric-top">累计登记付款</div>
        <div class="metric-value">
          <span class="currency">¥</span>{{ money(sums.paid) }}
        </div>
        <div class="metric-caption">已完成的线下付款记录</div>
      </div>
      <div class="metric-card highlight">
        <div class="metric-top">当前应付余额</div>
        <div class="metric-value">
          <span class="currency">¥</span>{{ money(sums.balance) }}
        </div>
        <div class="metric-caption">已批准收益 − 已登记付款</div>
      </div>
    </div>
    <div class="section-heading">
      <h2>创作者余额</h2>
      <span>{{ balances.length }} 位创作者</span>
    </div>
    <div class="balance-grid">
      <article
        v-for="(row, i) in balances"
        :key="row.creatorId"
        class="balance-card"
      >
        <div class="avatar-row">
          <span class="creator-avatar" :class="`tone-${i % 4}`">{{
            row.creatorName?.slice(-2)
          }}</span>
          <div>
            <h3>{{ row.creatorName }}</h3>
            <p>创作者收益账户</p>
          </div>
          <el-button
            v-if="hasRole('ADMIN', 'FINANCE') && row.balance > 0"
            text
            type="primary"
            style="margin-left: auto"
            @click="open(row)"
            >登记付款 ↗</el-button
          >
        </div>
        <span class="small muted">当前应付余额</span>
        <div
          class="balance-amount money"
          :class="{ negative: row.balance < 0 }"
        >
          ¥{{ money(row.balance) }}
        </div>
        <div class="balance-breakdown">
          <div>
            <span>已批准收益</span><b>¥{{ money(row.earned) }}</b>
          </div>
          <div>
            <span>已登记付款</span><b>¥{{ money(row.paid) }}</b>
          </div>
        </div>
      </article>
    </div>
    <el-empty
      v-if="!balances.length && !loading"
      description="暂无创作者余额"
      :image-size="70"
    />
  </div>
  <div class="section-heading">
    <h2>付款记录</h2>
    <span>OFFLINE PAYMENT REGISTER</span>
  </div>
  <div class="panel table-panel">
    <div class="toolbar">
      <el-input
        v-model="query.q"
        class="search-input"
        :prefix-icon="Search"
        placeholder="搜索创作者、付款凭据…"
        clearable
        @clear="
          query.page = 1;
          load();
        "
        @keyup.enter="
          query.page = 1;
          load();
        "
      /><el-button
        @click="
          query.page = 1;
          load();
        "
        >查询</el-button
      ><span class="spacer"></span
      ><span class="result-count">{{ total }} 条付款记录</span>
    </div>
    <el-table :data="payments" empty-text="暂无付款记录"
      ><el-table-column label="创作者" min-width="140"
        ><template #default="{ row }">{{
          row.creatorName ||
          balances.find((x) => x.creatorId === row.creatorId)?.creatorName
        }}</template></el-table-column
      ><el-table-column label="付款日期" width="125"
        ><template #default="{ row }">{{
          date(row.paidAt)
        }}</template></el-table-column
      ><el-table-column
        prop="reference"
        label="付款凭据 / 流水"
        min-width="200" /><el-table-column
        label="付款金额"
        width="145"
        align="right"
        ><template #default="{ row }"
          ><span class="money">¥{{ money(row.amount) }}</span></template
        ></el-table-column
      ><el-table-column prop="note" label="备注" min-width="180"
    /></el-table>
    <div class="pagination">
      <span>共 {{ total }} 条付款记录</span
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
    title="登记线下付款"
    width="570px"
    destroy-on-close
    ><div class="form-note">
      用于记录已经完成的银行转账或其他线下付款。登记后扣减创作者应付余额。
    </div>
    <el-form ref="formRef" :model="form" label-position="top"
      ><el-form-item
        label="收款创作者"
        prop="creatorId"
        :rules="[{ required: true, message: '请选择创作者' }]"
        ><el-select v-model="form.creatorId" filterable
          ><el-option
            v-for="row in balances"
            :key="row.creatorId"
            :label="`${row.creatorName} · 可付 ¥${money(row.balance)}`"
            :value="row.creatorId"
            :disabled="row.balance <= 0" /></el-select
      ></el-form-item>
      <div class="form-grid">
        <el-form-item label="付款金额（元）"
          ><el-input-number
            v-model="form.amount"
            :min="0"
            :max="Number(selected?.balance || 0)"
            :precision="2"
            :controls="false"
            class="full-width" /></el-form-item
        ><el-form-item
          label="实际付款日期"
          prop="paidAt"
          :rules="[{ required: true, message: '请选择付款日期' }]"
          ><el-date-picker
            v-model="form.paidAt"
            value-format="YYYY-MM-DD"
            class="full-width" /></el-form-item
        ><el-form-item
          label="付款凭据 / 唯一流水号"
          prop="reference"
          class="wide"
          :rules="[{ required: true, message: '请输入付款凭据流水号' }]"
          ><el-input
            v-model="form.reference"
            placeholder="银行回单或支付流水号" /></el-form-item
        ><el-form-item label="付款备注" class="wide"
          ><el-input v-model="form.note" type="textarea" :rows="2"
        /></el-form-item></div></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button
        type="primary"
        :loading="saving"
        :icon="CreditCard"
        @click="save"
        >确认登记付款</el-button
      ></template
    ></el-dialog
  >
</template>
