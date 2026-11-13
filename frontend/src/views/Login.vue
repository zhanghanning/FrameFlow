<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { api, setSession, homePath } from "../lib/api";
const router = useRouter(),
  busy = ref(false),
  error = ref(""),
  formRef = ref();
const registeredUsername =
  sessionStorage.getItem("frameflow-registered-username") || "";
sessionStorage.removeItem("frameflow-registered-username");
const form = reactive({ username: registeredUsername, password: "" });
const demos = [
  { username: "admin", name: "管理员" },
  { username: "producer", name: "项目统筹" },
  { username: "finance", name: "财务" },
  { username: "reviewer", name: "审核" },
  { username: "creator", name: "创作者" },
];
async function login() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  busy.value = true;
  error.value = "";
  try {
    setSession(await api.post("/auth/login", form));
    await router.push(homePath());
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="login-page">
    <section class="login-art">
      <div class="brand">
        <span class="brand-symbol">F<span>↗</span></span
        ><span><strong>帧序</strong><small>FRAMEFLOW</small></span>
      </div>
      <div class="login-art-copy">
        <small>MADE FOR CREATORS.</small>
        <h1>让灵感自由生长，<br />让价值清晰可见。</h1>
        <p>连接素材、创作与收益。<br />属于创作者的工作室管理空间。</p>
      </div>
      <div class="login-ring"></div>
      <div class="login-star">✦</div>
      <div class="login-art-footer">
        <span>CREATION, IN FLOW.</span><span>帧序创意工作室</span>
      </div>
    </section>
    <section class="login-pane">
      <div class="login-form">
        <div class="eyebrow">YOUR CREATIVE WORKSPACE</div>
        <h2>欢迎回到帧序</h2>
        <p>登录工作室，开始今天的创作与协作。</p>
        <div v-if="registeredUsername" class="registration-success">
          注册成功，你的创作者空间已准备好。登录后即可上传素材、完善资料。
        </div>
        <el-form
          ref="formRef"
          :model="form"
          label-position="top"
          @submit.prevent="login"
          ><el-form-item
            label="账号"
            prop="username"
            :rules="[
              { required: true, message: '请输入账号', trigger: 'blur' },
            ]"
            ><el-input
              v-model="form.username"
              placeholder="输入工作室账号"
              autocomplete="username" /></el-form-item
          ><el-form-item
            label="密码"
            prop="password"
            :rules="[
              { required: true, message: '请输入密码', trigger: 'blur' },
            ]"
            ><el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="输入登录密码"
              autocomplete="current-password"
              @keyup.enter="login"
          /></el-form-item>
          <div v-if="error" class="error-panel">{{ error }}</div>
          <el-button type="primary" :loading="busy" native-type="submit"
            >进入工作空间 <span style="margin-left: auto">↗</span></el-button
          ></el-form
        >
        <p class="auth-switch">
          还没有账号？<router-link to="/register">创建创作者账号</router-link>
        </p>
        <div class="demo-title">演示账号 · 体验不同工作角色</div>
        <div class="demo-roles">
          <button
            v-for="demo in demos"
            :key="demo.username"
            class="demo-role"
            :class="{ active: form.username === demo.username }"
            @click="
              form.username = demo.username;
              form.password = 'FrameFlow2026!';
            "
          >
            {{ demo.name }}
          </button>
        </div>
        <p class="demo-hint">
          演示密码：FrameFlow2026! · 各角色按实际权限访问业务数据
        </p>
        <div class="login-bottom">
          每一份素材，都值得被妥善管理。<br />每一笔收益，都应该清晰透明。
        </div>
      </div>
    </section>
  </div>
</template>
