<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { api, session, label, fail, refreshSession } from "../lib/api";
import PageHeader from "../components/PageHeader.vue";
const form = reactive({ oldPassword: "", newPassword: "", confirm: "" }),
  saving = ref(false),
  formRef = ref(),
  refreshing = ref(false);
async function refreshIdentity() {
  refreshing.value = true;
  try {
    await refreshSession();
    ElMessage.success("身份状态已更新");
  } catch (e) {
    fail(e);
  } finally {
    refreshing.value = false;
  }
}
async function save() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  saving.value = true;
  try {
    await api.put("/auth/password", {
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    });
    ElMessage.success("密码已更新，其他登录会话已退出");
    Object.assign(form, { oldPassword: "", newPassword: "", confirm: "" });
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
</script>
<template>
  <PageHeader
    title="账号与密码"
    subtitle="管理登录信息，查看当前身份与申请进度。"
    ><el-button :loading="refreshing" @click="refreshIdentity"
      >刷新身份</el-button
    ><el-button type="primary" @click="$router.push('/role-applications')"
      >身份申请</el-button
    ></PageHeader
  >
  <div class="profile-layout">
    <section class="panel profile-card">
      <span class="avatar">{{ session.user?.displayName?.slice(-2) }}</span>
      <h2>{{ session.user?.displayName }}</h2>
      <p>@{{ session.user?.username }}</p>
      <span
        v-for="role in session.user?.roles?.length
          ? session.user.roles
          : ['CREATOR']"
        :key="role"
        class="role-tag"
        >{{ label(role) }}</span
      >
      <p style="margin-top: 25px">帧序创意工作室</p>
      <router-link
        v-if="session.user?.creatorId"
        to="/creators?edit=mine"
        class="profile-link"
        >完善创作者资料 ↗</router-link
      >
    </section>
    <section class="panel">
      <div class="panel-title">
        <div>
          <h2>修改登录密码</h2>
          <p>更新后，其他设备的登录会话将退出</p>
        </div>
      </div>
      <el-form ref="formRef" :model="form" label-position="top"
        ><el-form-item
          label="当前密码"
          prop="oldPassword"
          :rules="[{ required: true, message: '请输入当前密码' }]"
          ><el-input
            v-model="form.oldPassword"
            type="password"
            show-password
            autocomplete="current-password" /></el-form-item
        ><el-form-item
          label="新密码"
          prop="newPassword"
          :rules="[
            { required: true, message: '请输入新密码' },
            { min: 8, message: '新密码至少8位' },
          ]"
          ><el-input
            v-model="form.newPassword"
            type="password"
            show-password
            autocomplete="new-password" /></el-form-item
        ><el-form-item
          label="确认新密码"
          prop="confirm"
          :rules="[
            {
              validator: (rule, value, callback) =>
                value === form.newPassword
                  ? callback()
                  : callback(new Error('两次密码输入不一致')),
              trigger: 'blur',
            },
          ]"
          ><el-input
            v-model="form.confirm"
            type="password"
            show-password
            autocomplete="new-password" /></el-form-item
        ><el-button type="primary" :loading="saving" @click="save"
          >更新密码</el-button
        ></el-form
      >
    </section>
  </div>
</template>
