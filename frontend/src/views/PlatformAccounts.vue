<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Plus, Refresh, Search, Connection } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  api,
  session,
  hasRole,
  fail,
  label,
  datetime,
  listItems,
} from "../lib/api";
import { platformIntegrationsEnabled } from "../lib/features";
import PageHeader from "../components/PageHeader.vue";
import LoadError from "../components/LoadError.vue";
import PlatformLogo from "../components/PlatformLogo.vue";
import StatusBadge from "../components/StatusBadge.vue";
const route = useRoute(),
  router = useRouter();
const tab = ref(
    platformIntegrationsEnabled && hasRole("ADMIN", "CREATOR")
      ? "connections"
      : "accounts",
  ),
  integrations = ref([]),
  connections = ref([]),
  options = ref({}),
  loading = ref(false),
  error = ref("");
const query = reactive({ q: "", status: "", page: 1, pageSize: 10 });
const rows = ref([]),
  total = ref(0),
  accountLoading = ref(false),
  accountError = ref("");
const dialog = ref(false),
  editing = ref(null),
  saving = ref(false),
  formRef = ref();
const form = reactive({
  name: "",
  platformId: null,
  creatorId: null,
  externalId: "",
  status: "ACTIVE",
});
const integrationDialog = ref(false),
  selectedIntegration = ref(null),
  authorizing = ref(false),
  authorizationOwner = ref(null),
  syncing = ref(null),
  disconnecting = ref(null);
const callback = ref(
  platformIntegrationsEnabled && route.query.connection
    ? {
        success: route.query.connection === "success",
        message: String(
          route.query.message ||
            (route.query.connection === "success"
              ? "平台账号已连接"
              : "平台连接未完成，请重试"),
        ),
      }
    : null,
);
const linkedAccount = computed(
  () =>
    !!editing.value &&
    (rows.value.some(
      (item) => item.id === editing.value && Boolean(item.integrationLinked),
    ) || connections.value.some((item) => item.accountId === editing.value)),
);
const canWrite = computed(() => hasRole("ADMIN", "CREATOR"));
const creatorOptions = computed(() =>
  (options.value.creators || []).filter(
    (item) => hasRole("ADMIN") || item.id === session.user?.creatorId,
  ),
);
const integrationStatus = {
  NOT_CONFIGURED: "待配置",
  READY: "可连接",
  APPROVAL_REQUIRED: "待申请接入",
};
const connectionStatus = {
  CONNECTED: "已连接",
  EXPIRED: "授权已过期",
  DISCONNECTED: "已断开",
  ERROR: "同步异常",
};
const managedCount = computed(
  () => connections.value.filter((item) => item.status === "CONNECTED").length,
);
function platform(id) {
  return options.value.platforms?.find((item) => item.id === id);
}
function creator(id) {
  return options.value.creators?.find((item) => item.id === id)?.name || "—";
}
function editable(row) {
  return (
    hasRole("ADMIN") ||
    (hasRole("CREATOR") && row.creatorId === session.user?.creatorId)
  );
}
function safeLink(value) {
  return /^https?:\/\//i.test(value || "") ? value : undefined;
}
function requirementLines(item) {
  const lines = Array.isArray(item?.requirements)
    ? item.requirements
    : item?.requirements
      ? [item.requirements]
      : [];
  return lines.map((line) =>
    !hasRole("ADMIN") && line.includes("PLATFORM_TOKEN_KEY")
      ? "由工作室管理员完成平台应用配置"
      : line,
  );
}
function integrationFor(code) {
  return integrations.value.find((item) => item.code === code);
}
async function load() {
  loading.value = true;
  error.value = "";
  const results = await Promise.allSettled([
    platformIntegrationsEnabled
      ? api.get("/platform-integrations")
      : Promise.resolve([]),
    platformIntegrationsEnabled && canWrite.value
      ? api.get("/platform-connections")
      : Promise.resolve([]),
    api.get("/options"),
  ]);
  if (results[0].status === "fulfilled") integrations.value = results[0].value;
  if (results[1].status === "fulfilled") connections.value = results[1].value;
  if (results[2].status === "fulfilled") options.value = results[2].value;
  const failure = results.find((result) => result.status === "rejected");
  if (failure) error.value = failure.reason.message;
  loading.value = false;
}
async function loadAccounts() {
  accountLoading.value = true;
  accountError.value = "";
  try {
    const data = await api.get("/platform-accounts", { params: query });
    rows.value = listItems(data);
    total.value = data.total ?? rows.value.length;
  } catch (e) {
    accountError.value = e.message;
  } finally {
    accountLoading.value = false;
  }
}
function search() {
  query.page = 1;
  loadAccounts();
}
function openAccount(row = null) {
  editing.value = row?.id || null;
  Object.assign(
    form,
    {
      name: "",
      platformId: null,
      creatorId: hasRole("ADMIN") ? null : session.user?.creatorId,
      externalId: "",
      status: "ACTIVE",
    },
    row || {},
  );
  dialog.value = true;
}
async function saveAccount() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  saving.value = true;
  try {
    const payload = {
      name: form.name.trim(),
      platformId: form.platformId,
      creatorId: form.creatorId,
      externalId: form.externalId.trim(),
      status: form.status,
    };
    await api[editing.value ? "put" : "post"](
      `/platform-accounts${editing.value ? `/${editing.value}` : ""}`,
      payload,
    );
    ElMessage.success("平台账号已保存");
    dialog.value = false;
    await loadAccounts();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
function showIntegration(item, owner = null) {
  selectedIntegration.value = item;
  authorizationOwner.value =
    owner || (hasRole("ADMIN") ? null : session.user?.creatorId);
  integrationDialog.value = true;
}
async function authorize() {
  const item = selectedIntegration.value;
  if (!canWrite.value || !item?.canConnect || !authorizationOwner.value) return;
  authorizing.value = true;
  try {
    const result = await api.post(
      `/platform-integrations/${item.code}/authorize`,
      hasRole("ADMIN") ? { creatorId: authorizationOwner.value } : {},
    );
    if (!safeLink(result.authorizationUrl))
      throw new Error("平台未返回有效授权地址");
    window.location.assign(result.authorizationUrl);
  } catch (e) {
    fail(e);
    authorizing.value = false;
  }
}
async function sync(row) {
  syncing.value = row.id;
  try {
    const result = await api.post(
      `/platform-connections/${row.id}/sync`,
      {},
      { timeout: 180000 },
    );
    ElMessage.success(`已同步 ${result.syncedCount} 条平台作品`);
    await load();
  } catch (e) {
    fail(e);
    await load();
  } finally {
    syncing.value = null;
  }
}
async function disconnect(row) {
  try {
    await ElMessageBox.confirm(
      `断开“${row.accountName}”后将停止同步，已同步作品和账号台账保留。`,
      "断开平台连接",
      { confirmButtonText: "断开连接", cancelButtonText: "取消" },
    );
    disconnecting.value = row.id;
    await api.delete(`/platform-connections/${row.id}`);
    ElMessage.success("平台连接已断开");
    await load();
  } catch (e) {
    if (e instanceof Error) fail(e);
  } finally {
    disconnecting.value = null;
  }
}
function viewContent(row) {
  router.push({
    path: "/works",
    query: { tab: "platform", connectionId: row.id },
  });
}
onMounted(async () => {
  await Promise.all([load(), loadAccounts()]);
  if (route.query.connection) {
    const remaining = { ...route.query };
    delete remaining.connection;
    delete remaining.message;
    await router.replace({ query: remaining });
  }
});
</script>
<template>
  <PageHeader
    title="平台账号"
    :subtitle="platformIntegrationsEnabled
      ? '连接创作平台，同步已发布作品。'
      : '管理平台账号，关联作品发布与收益。'"
    ><el-button
      :icon="Refresh"
      @click="
        load();
        loadAccounts();
      "
      >刷新</el-button
    ><el-button
      v-if="canWrite"
      type="primary"
      :icon="Plus"
      @click="openAccount()"
      >手工登记账号</el-button
    ></PageHeader
  >
  <el-alert
    v-if="callback"
    :title="callback.message"
    :type="callback.success ? 'success' : 'error'"
    show-icon
    class="platform-callback"
    @close="callback = null"
  />
  <LoadError :message="error" @retry="load" />
  <div
    v-if="platformIntegrationsEnabled"
    v-loading="loading"
    class="platform-integration-grid"
    data-testid="platform-integrations"
  >
    <article
      v-for="item in integrations"
      :key="item.code"
      class="platform-integration-card"
      :data-platform="item.code"
    >
      <div class="platform-integration-head">
        <PlatformLogo :code="item.code" :name="item.name" /><span
          :class="['integration-status', item.status.toLowerCase()]"
          >{{ integrationStatus[item.status] || item.status }}</span
        >
      </div>
      <h2>{{ item.name }}</h2>
      <p>{{ item.description }}</p>
      <div class="platform-integration-actions">
        <el-button
          v-if="item.canConnect && canWrite"
          type="primary"
          @click="showIntegration(item)"
          >连接账号</el-button
        ><el-button v-else disabled>{{
          item.canConnect && !canWrite
            ? "由创作者连接"
            : integrationStatus[item.status] || "暂不可连接"
        }}</el-button
        ><el-button text @click="showIntegration(item)">接入要求</el-button>
      </div>
    </article>
  </div>
  <el-tabs
    v-if="platformIntegrationsEnabled"
    v-model="tab"
    class="section-tabs platform-account-tabs"
    ><el-tab-pane
      v-if="canWrite"
      :label="`平台连接 · ${managedCount}`"
      name="connections" /><el-tab-pane label="账号台账" name="accounts"
  /></el-tabs>
  <section
    v-if="platformIntegrationsEnabled && tab === 'connections'"
    class="panel table-panel"
    data-testid="platform-connections"
  >
    <el-table
      :data="connections"
      empty-text="尚未连接平台账号，完成配置后可在上方授权连接。"
    >
      <el-table-column label="平台账号" min-width="210"
        ><template #default="{ row }"
          ><div class="platform-account-cell">
            <PlatformLogo :code="row.platformCode" :name="row.platformName" />
            <div>
              <span class="table-primary">{{ row.accountName }}</span
              ><span class="table-secondary">{{ row.platformName }}</span>
            </div>
          </div></template
        ></el-table-column
      >
      <el-table-column label="状态" width="130"
        ><template #default="{ row }"
          ><span :class="['integration-status', row.status.toLowerCase()]">{{
            connectionStatus[row.status] || row.status
          }}</span></template
        ></el-table-column
      >
      <el-table-column label="作品" width="85"
        ><template #default="{ row }">{{
          row.contentCount || 0
        }}</template></el-table-column
      >
      <el-table-column label="最近同步" min-width="160"
        ><template #default="{ row }"
          >{{ datetime(row.lastSyncedAt)
          }}<span v-if="row.lastError" class="platform-sync-error">{{
            row.lastError
          }}</span></template
        ></el-table-column
      >
      <el-table-column label="操作" min-width="250" fixed="right"
        ><template #default="{ row }"
          ><div class="table-actions">
            <el-button text type="primary" @click="viewContent(row)"
              >查看作品</el-button
            ><el-button
              v-if="
                row.canManage && ['CONNECTED', 'ERROR'].includes(row.status)
              "
              text
              type="primary"
              :loading="syncing === row.id"
              :disabled="syncing !== null && syncing !== row.id"
              @click="sync(row)"
              >同步</el-button
            ><el-button
              v-if="
                row.canManage &&
                ['EXPIRED', 'DISCONNECTED'].includes(row.status)
              "
              text
              :disabled="!integrationFor(row.platformCode)?.canConnect"
              @click="
                showIntegration(integrationFor(row.platformCode), row.creatorId)
              "
              >重新连接</el-button
            ><el-button
              v-if="row.canManage && row.status !== 'DISCONNECTED'"
              text
              :loading="disconnecting === row.id"
              @click="disconnect(row)"
              >断开</el-button
            >
          </div></template
        ></el-table-column
      >
    </el-table>
  </section>
  <section v-else class="panel table-panel">
    <p class="platform-ledger-note">
      账号台账用于关联发布与收益。手工登记后，可继续录入发布记录与导入收入。
    </p>
    <LoadError :message="accountError" @retry="loadAccounts" />
    <div class="toolbar">
      <el-input
        v-model="query.q"
        class="search-input"
        :prefix-icon="Search"
        placeholder="搜索账号、创作者…"
        clearable
        @clear="search"
        @keyup.enter="search"
      /><el-select
        v-model="query.status"
        class="filter-select"
        placeholder="全部状态"
        clearable
        @change="search"
        ><el-option label="有效" value="ACTIVE" /><el-option
          label="停用"
          value="INACTIVE" /></el-select
      ><el-button @click="search">查询</el-button><span class="spacer"></span
      ><span class="result-count">共 {{ total }} 个账号</span>
    </div>
    <el-table v-loading="accountLoading" :data="rows" empty-text="暂无账号记录"
      ><el-table-column label="账号" min-width="200"
        ><template #default="{ row }"
          ><div class="platform-account-cell">
            <PlatformLogo
              :code="row.platformCode || platform(row.platformId)?.code"
              :name="row.platformName || platform(row.platformId)?.name"
            />
            <div>
              <span class="table-primary">{{ row.name }}</span
              ><span class="table-secondary">{{
                row.platformName || platform(row.platformId)?.name
              }}</span>
            </div>
          </div></template
        ></el-table-column
      ><el-table-column label="所属创作者" min-width="130"
        ><template #default="{ row }">{{
          row.creatorName || creator(row.creatorId)
        }}</template></el-table-column
      ><el-table-column
        prop="externalId"
        label="平台账号 ID"
        min-width="180"
      /><el-table-column label="状态" width="100"
        ><template #default="{ row }"
          ><StatusBadge :value="row.status" /></template></el-table-column
      ><el-table-column v-if="canWrite" label="操作" width="100" fixed="right"
        ><template #default="{ row }"
          ><el-button
            v-if="editable(row)"
            text
            type="primary"
            @click="openAccount(row)"
            >编辑</el-button
          ></template
        ></el-table-column
      ></el-table
    >
    <div class="pagination">
      <span>共 {{ total }} 个账号</span
      ><el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="sizes, prev, pager, next"
        @change="loadAccounts"
      />
    </div>
  </section>
  <el-dialog
    v-model="dialog"
    :title="editing ? '编辑平台账号' : '手工登记平台账号'"
    width="650px"
    destroy-on-close
    ><el-form ref="formRef" :model="form" label-position="top"
      ><div class="form-grid">
        <el-form-item
          label="账号名称"
          prop="name"
          :rules="[{ required: true, message: '请输入账号名称' }]"
          ><el-input v-model="form.name" maxlength="100" /></el-form-item
        ><el-form-item
          label="内容平台"
          prop="platformId"
          :rules="[{ required: true, message: '请选择内容平台' }]"
          ><el-select v-model="form.platformId" :disabled="linkedAccount"
            ><el-option
              v-for="item in options.platforms"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select></el-form-item
        ><el-form-item
          label="所属创作者"
          prop="creatorId"
          :rules="[{ required: true, message: '请选择创作者' }]"
          ><el-select
            v-model="form.creatorId"
            filterable
            :disabled="linkedAccount || !hasRole('ADMIN')"
            ><el-option
              v-for="item in creatorOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select></el-form-item
        ><el-form-item
          label="平台账号 ID"
          prop="externalId"
          :rules="[{ required: true, message: '请输入平台账号 ID' }]"
          ><el-input
            v-model="form.externalId"
            maxlength="128"
            :disabled="linkedAccount" /></el-form-item
        ><el-form-item label="状态"
          ><el-select v-model="form.status"
            ><el-option label="有效" value="ACTIVE" /><el-option
              label="停用"
              value="INACTIVE" /></el-select
        ></el-form-item></div></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="saveAccount"
        >保存账号</el-button
      ></template
    ></el-dialog
  >
  <el-dialog
    v-if="platformIntegrationsEnabled"
    v-model="integrationDialog"
    :title="`${selectedIntegration?.name || ''}账号连接`"
    width="620px"
    destroy-on-close
  >
    <template v-if="selectedIntegration"
      ><div class="platform-connect-heading">
        <PlatformLogo
          :code="selectedIntegration.code"
          :name="selectedIntegration.name"
        />
        <div>
          <h3>{{ selectedIntegration.name }}</h3>
          <span
            :class="[
              'integration-status',
              selectedIntegration.status.toLowerCase(),
            ]"
            >{{ integrationStatus[selectedIntegration.status] }}</span
          >
        </div>
      </div>
      <p class="platform-connect-description">
        {{ selectedIntegration.description }}
      </p>
      <ul class="platform-requirements">
        <li
          v-for="(text, index) in requirementLines(selectedIntegration)"
          :key="index"
        >
          {{ text }}
        </li>
      </ul>
      <a
        v-if="safeLink(selectedIntegration.docsUrl)"
        class="platform-docs-link"
        :href="selectedIntegration.docsUrl"
        target="_blank"
        rel="noopener noreferrer"
        >查看平台官方接入说明 ↗</a
      >
      <div
        v-if="hasRole('ADMIN') && selectedIntegration.redirectUri"
        class="platform-redirect"
      >
        <span>应用回调地址</span
        ><code>{{ selectedIntegration.redirectUri }}</code>
      </div>
      <el-form
        v-if="selectedIntegration.canConnect && canWrite"
        label-position="top"
        class="platform-connect-form"
        ><el-form-item label="关联创作者"
          ><el-select
            v-model="authorizationOwner"
            filterable
            :disabled="!hasRole('ADMIN')"
            placeholder="请选择创作者"
            ><el-option
              v-for="item in creatorOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select></el-form-item></el-form
    ></template>
    <template #footer
      ><el-button @click="integrationDialog = false">关闭</el-button
      ><el-button
        type="primary"
        :disabled="
          !canWrite || !selectedIntegration?.canConnect || !authorizationOwner
        "
        :loading="authorizing"
        @click="authorize"
        >前往平台授权</el-button
      ></template
    >
  </el-dialog>
</template>
