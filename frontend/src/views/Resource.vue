<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { Plus, Search, Refresh } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { resources } from "../lib/resources";
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
  businessRoles,
  roleDescriptions,
} from "../lib/api";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import LoadError from "../components/LoadError.vue";
import FinanceGuide from "../components/FinanceGuide.vue";
const props = defineProps({ resource: String }),
  config = computed(() => resources[props.resource]);
const query = reactive({
    q: "",
    status: "",
    page: 1,
    pageSize: 10,
  }),
  rows = ref([]),
  total = ref(0),
  loading = ref(false),
  error = ref(""),
  options = ref({}),
  dialog = ref(false),
  saving = ref(false),
  form = reactive({}),
  formRef = ref(),
  editing = ref(null),
  originalCreatorId = ref(null);
const route = useRoute();
const canWrite = computed(() => hasRole(...config.value.writeRoles)),
  canCreate = computed(() =>
    props.resource === "creators" ? hasRole("ADMIN") : canWrite.value,
  ),
  fields = computed(() =>
    config.value.fields.filter(
      (f) =>
        !(
          editing.value &&
          props.resource === "users" &&
          ["username", "password"].includes(f.key)
        ),
    ),
  );
const rules = computed(() =>
  Object.fromEntries(
    fields.value.map((f) => [
      f.key,
      [
        {
          required:
            props.resource === "users" && f.key === "creatorId"
              ? form.roles?.includes("CREATOR")
              : f.required,
          message: `请${["lookup", "enum", "date"].includes(f.type) ? "选择" : "填写"}${f.label}`,
          trigger: "change",
        },
        ...(f.type === "email"
          ? [{ type: "email", message: "请输入有效邮箱地址", trigger: "blur" }]
          : []),
      ],
    ]),
  ),
);
function canEditRow(row) {
  if (!canWrite.value) return false;
  if (hasRole("ADMIN")) return true;
  if (props.resource === "creators") return row.id === session.user?.creatorId;
  if (props.resource === "platform-accounts")
    return row.creatorId === session.user?.creatorId;
  return true;
}
function lookupOptions(field) {
  const values = options.value[field.source] || [];
  if (
    props.resource === "platform-accounts" &&
    field.key === "creatorId" &&
    !hasRole("ADMIN")
  )
    return values.filter((creator) => creator.id === session.user?.creatorId);
  if (
    props.resource === "costs" &&
    field.key === "projectId" &&
    !hasRole("ADMIN", "FINANCE")
  )
    return values.filter(
      (project) => hasRole("PRODUCER") && project.ownerId === session.user?.id,
    );
  return values;
}
function changeRoles(values) {
  form.roles = originalCreatorId.value
    ? [...new Set(["CREATOR", ...values])]
    : values;
  if (!form.roles.includes("CREATOR")) form.creatorId = null;
}
function display(row, column) {
  if (column.key === "entityType" && row[column.key] === "USER")
    return "团队账号";
  if (["action", "entityType", "category"].includes(column.key))
    return label(row[column.key]);
  if (column.source)
    return (
      options.value[column.source]?.find((x) => x.id === row[column.key])
        ?.name ||
      row[column.key.replace("Id", "Name")] ||
      "—"
    );
  return (
    row[column.key] ||
    (column.key === "userName" ? row.displayName || row.username : "") ||
    "—"
  );
}
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const data = await api.get(`/${props.resource}`, { params: query });
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
  Object.keys(form).forEach((k) => delete form[k]);
  config.value.fields.forEach(
    (f) =>
      (form[f.key] =
        row?.[f.key] ??
        structuredClone(
          f.default ??
            (f.multiple
              ? []
              : f.type === "date"
                ? new Date().toISOString().slice(0, 10)
                : f.type === "money"
                  ? 0
                  : ""),
        )),
  );
  if (props.resource === "users" && Array.isArray(form.roles))
    form.roles = form.roles.map((x) => (typeof x === "string" ? x : x.code));
  if (props.resource === "users") {
    originalCreatorId.value = row?.creatorId || null;
    if (originalCreatorId.value)
      form.roles = [...new Set(["CREATOR", ...form.roles])];
  }
  if (props.resource === "platform-accounts" && !row && !hasRole("ADMIN"))
    form.creatorId = session.user?.creatorId;
  dialog.value = true;
}
async function save() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  saving.value = true;
  try {
    const payload = { ...form };
    if (props.resource === "users" && originalCreatorId.value)
      payload.roles = [...new Set(["CREATOR", ...payload.roles])];
    if (!payload.creatorId) payload.creatorId = null;
    if (editing.value)
      await api.put(`/${props.resource}/${editing.value}`, payload);
    else await api.post(`/${props.resource}`, payload);
    ElMessage.success(
      `${config.value.singular}已${editing.value ? "更新" : "创建"}`,
    );
    dialog.value = false;
    await load();
    options.value = await api.get("/options");
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function approve(row) {
  try {
    await ElMessageBox.confirm(
      `批准「${row.description}」成本 ¥${money(row.amount)}？批准后将按发生日期关联分成合同。`,
      "审批项目成本",
      { confirmButtonText: "批准", cancelButtonText: "取消", type: "warning" },
    );
    await api.post(`/costs/${row.id}/approve`);
    ElMessage.success("成本已批准");
    load();
  } catch (e) {
    if (e instanceof Error) fail(e);
  }
}
onMounted(async () => {
  await load();
  try {
    options.value = await api.get("/options");
    if (props.resource === "creators" && route.query.edit === "mine") {
      const own = options.value.creators?.find(
        (creator) => creator.id === session.user?.creatorId,
      );
      if (own) open(own);
    }
  } catch (e) {
    fail(e);
  }
});
</script>
<template>
  <PageHeader :title="config.title" :subtitle="config.subtitle"
    ><el-button :icon="Refresh" @click="load">刷新</el-button
    ><el-button v-if="canCreate" type="primary" :icon="Plus" @click="open()"
      >新增{{ config.singular }}</el-button
    ></PageHeader
  ><FinanceGuide v-if="resource === 'costs'" stage="costs" /><LoadError :message="error" @retry="load" />
  <div class="panel table-panel">
    <div class="toolbar">
      <el-input
        v-model="query.q"
        class="search-input"
        :prefix-icon="Search"
        placeholder="搜索名称、关键词…"
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
        v-if="resource !== 'audit-logs'"
        v-model="query.status"
        class="filter-select"
        placeholder="全部状态"
        clearable
        @change="
          query.page = 1;
          load();
        "
        ><el-option
          v-for="status in resource === 'costs'
            ? ['DRAFT', 'APPROVED']
            : ['ACTIVE', 'INACTIVE']"
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
      ><span class="result-count">共 {{ total }} 条记录</span>
    </div>
    <el-table v-loading="loading" :data="rows" empty-text="暂无符合条件的记录"
      ><el-table-column
        v-for="column in config.columns"
        :key="column.key"
        :label="column.label"
        :min-width="column.width || 150"
        :align="column.type === 'money' ? 'right' : 'left'"
        ><template #default="{ row, $index }"
          ><StatusBadge
            v-if="column.type === 'status'"
            :value="row[column.key]"
          /><span v-else-if="column.type === 'money'" class="money"
            >¥{{ money(row[column.key]) }}</span
          ><span v-else-if="column.type === 'date'">{{
            date(row[column.key])
          }}</span
          ><span v-else-if="column.type === 'datetime'">{{
            datetime(row[column.key])
          }}</span>
          <div v-else-if="column.type === 'roles'">
            <span
              v-for="role in row[column.key]?.length ? row[column.key] : []"
              :key="typeof role === 'string' ? role : role.code"
              class="role-tag"
              >{{ label(typeof role === "string" ? role : role.code) }}</span
            >
          </div>
          <div v-else-if="column.type === 'creator'" class="avatar-row">
            <span class="creator-avatar" :class="`tone-${$index % 4}`">{{
              row[column.key]?.slice(-2)
            }}</span
            ><span class="table-primary">{{ row[column.key] }}</span>
          </div>
          <span v-else>{{ display(row, column) }}</span></template
        ></el-table-column
      ><el-table-column
        v-if="
          (canWrite && config.edit) ||
          (resource === 'costs' && hasRole('ADMIN', 'REVIEWER'))
        "
        label="操作"
        width="110"
        fixed="right"
        ><template #default="{ row }"
          ><el-button
            v-if="config.edit && canEditRow(row)"
            text
            type="primary"
            @click="open(row)"
            >{{ resource === "users" ? "调整岗位" : "编辑" }}</el-button
          ><el-button
            v-if="
              resource === 'costs' &&
              hasRole('ADMIN', 'REVIEWER') &&
              row.status === 'DRAFT'
            "
            text
            type="primary"
            @click="approve(row)"
            >审批</el-button
          ></template
        ></el-table-column
      ></el-table
    >
    <div class="pagination">
      <span>共 {{ total }} 条记录</span
      ><el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="sizes, prev, pager, next"
        @change="load"
      />
    </div>
  </div>
  <el-dialog
    v-model="dialog"
    :title="
      resource === 'users' && editing
        ? '调整账号岗位'
        : `${editing ? '编辑' : '新增'}${config.singular}`
    "
    width="650px"
    destroy-on-close
    ><el-form ref="formRef" :model="form" :rules="rules" label-position="top"
      ><div class="form-grid">
        <el-form-item
          v-for="field in fields"
          :key="field.key"
          :label="field.label"
          :prop="field.key"
          :class="{ wide: field.wide }"
          ><el-select
            v-if="field.type === 'lookup'"
            v-model="form[field.key]"
            filterable
            clearable
            class="full-width"
            :placeholder="`选择${field.label}`"
            :disabled="
              resource === 'users' &&
              field.key === 'creatorId' &&
              (originalCreatorId || !form.roles?.includes('CREATOR'))
            "
            ><el-option
              v-for="option in lookupOptions(field)"
              :key="option.id"
              :label="option.name"
              :value="option.id" /></el-select
          ><el-select
            v-else-if="field.type === 'enum'"
            v-model="form[field.key]"
            :multiple="field.multiple"
            class="full-width"
            @change="(values) => field.key === 'roles' && changeRoles(values)"
            ><el-option
              v-for="value in field.values"
              :key="value"
              :label="label(value)"
              :disabled="
                field.key === 'roles' &&
                value === 'CREATOR' &&
                !!originalCreatorId
              "
              :value="value" /></el-select
          ><el-date-picker
            v-else-if="field.type === 'date'"
            v-model="form[field.key]"
            value-format="YYYY-MM-DD"
            class="full-width" /><el-input-number
            v-else-if="field.type === 'money'"
            v-model="form[field.key]"
            :precision="2"
            :min="0.01"
            :max="999999999"
            :controls="false"
            class="full-width" /><el-input
            v-else
            v-model="form[field.key]"
            :type="
              field.type === 'textarea'
                ? 'textarea'
                : field.type === 'password'
                  ? 'password'
                  : 'text'
            "
            :rows="3"
            :show-password="field.type === 'password'"
            :maxlength="field.type === 'textarea' ? 1000 : 200"
        /></el-form-item>
      </div>
      <div v-if="resource === 'users'" class="role-guidance">
        <h4>身份与权限范围</h4>
        <p v-for="role in businessRoles" :key="role">
          <b>{{ label(role) }}</b
          ><span>{{ roleDescriptions[role] }}</span>
        </p>
      </div>
      <p v-if="resource === 'users'" class="form-note">
        岗位按职责分配。清空附加岗位后保留创作者身份；尚无创作者档案的账号会自动建立个人档案。已有档案与历史作品保持关联。
      </p></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save"
        >保存{{ config.singular }}</el-button
      ></template
    ></el-dialog
  >
</template>
