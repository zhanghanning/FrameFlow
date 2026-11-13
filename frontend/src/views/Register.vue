<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { api } from "../lib/api";
const router = useRouter();
const formRef = ref(),
  busy = ref(false),
  error = ref("");
const form = reactive({
  username: "",
  displayName: "",
  password: "",
  confirmPassword: "",
});
const rules = {
  username: [
    { required: true, message: "请输入登录账号", trigger: "blur" },
    {
      pattern: /^[A-Za-z0-9_]{4,32}$/,
      message: "账号为4至32位英文、数字或下划线",
      trigger: "blur",
    },
  ],
  displayName: [
    { required: true, message: "请输入姓名或昵称", trigger: "blur" },
    { min: 1, max: 80, message: "姓名或昵称最长80字", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请设置登录密码", trigger: "blur" },
    { min: 8, max: 72, message: "密码长度为8至72位", trigger: "blur" },
  ],
  confirmPassword: [
    { required: true, message: "请再次输入密码", trigger: "blur" },
    {
      validator: (rule, value, callback) =>
        value === form.password
          ? callback()
          : callback(new Error("两次密码输入不一致")),
      trigger: "blur",
    },
  ],
};
async function register() {
  form.username = form.username.trim();
  form.displayName = form.displayName.trim();
  if (!(await formRef.value.validate().catch(() => false))) return;
  busy.value = true;
  error.value = "";
  try {
    const user = await api.post("/auth/register", form);
    sessionStorage.setItem("frameflow-registered-username", user.username);
    await router.replace("/login");
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="login-page registration-page">
    <section class="login-art">
      <router-link to="/login" class="brand"
        ><span class="brand-symbol">F<span>↗</span></span
        ><span><strong>帧序</strong><small>FRAMEFLOW</small></span></router-link
      >
      <div class="login-art-copy">
        <small>YOUR NEXT CHAPTER.</small>
        <h1>每一份才华，<br />都有自己的位置。</h1>
        <p>从一个账号开始，<br />连接你的创作与协作。</p>
      </div>
      <div class="login-ring"></div>
      <div class="login-star">✦</div>
      <div class="login-art-footer">
        <span>CREATION, IN FLOW.</span><span>帧序创意工作室</span>
      </div>
    </section>
    <section class="login-pane">
      <div class="login-form register-form">
        <div class="eyebrow">JOIN THE STUDIO</div>
        <h2>创建你的账号</h2>
        <p>注册即成为创作者，开启你的素材与作品空间。</p>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="register"
        >
          <el-form-item label="登录账号" prop="username"
            ><el-input
              v-model="form.username"
              maxlength="32"
              placeholder="4–32位英文、数字或下划线"
              autocomplete="username"
          /></el-form-item>
          <el-form-item label="姓名 / 昵称" prop="displayName"
            ><el-input
              v-model="form.displayName"
              maxlength="80"
              placeholder="你的创作者名称"
              autocomplete="name"
          /></el-form-item>
          <el-form-item label="设置密码" prop="password"
            ><el-input
              v-model="form.password"
              type="password"
              show-password
              maxlength="72"
              placeholder="至少8位"
              autocomplete="new-password"
          /></el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword"
            ><el-input
              v-model="form.confirmPassword"
              type="password"
              show-password
              maxlength="72"
              placeholder="再次输入密码"
              autocomplete="new-password"
          /></el-form-item>
          <div v-if="error" class="error-panel">{{ error }}</div>
          <div class="registration-note">
            <span class="registration-dot"></span
            ><span
              >注册后自动建立创作者档案。更多协作岗位可在账号中心提交身份申请。</span
            >
          </div>
          <el-button type="primary" :loading="busy" native-type="submit"
            >创建创作者账号 <span style="margin-left: auto">↗</span></el-button
          >
        </el-form>
        <p class="auth-switch">
          已有账号？<router-link to="/login">返回登录</router-link>
        </p>
      </div>
    </section>
  </div>
</template>
