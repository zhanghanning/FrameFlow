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
        <span class="frame-mark" aria-hidden="true"><i></i><i></i><i></i><i></i><b></b></span>
        <span><strong>帧序</strong><small>FRAMEFLOW STUDIO</small></span>
      </div>
      <div class="login-art-copy">
        <small>THE CREATOR'S WORKING ARCHIVE</small>
        <h1>创作有序。<br /><em>每一帧，</em><br />都有来处。</h1>
        <p>从素材到成片，从协作到分账。<br />把工作室的日常，收进一本清楚的制作档案。</p>
      </div>
      <div class="auth-slate" aria-hidden="true"><span>素材 / 制作 / 分账</span><div><b>FRAME</b><b>FLOW</b></div><small>CREATIVE STUDIO · WORKING ARCHIVE</small></div>
      <div class="login-art-footer">
        <span>帧序工作室</span><span>EST. FOR CREATORS</span>
      </div>
    </section>
    <section class="login-pane">
      <div class="login-form">
        <div class="eyebrow">01 / ENTER THE STUDIO</div>
        <h2>回到工作室</h2>
        <p>登录账号，接续你的素材、项目与收益记录。</p>
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
            >进入工作室 <span class="auth-submit-arrow">→</span></el-button
          ></el-form
        >
        <p class="auth-switch">
          还没有账号？<router-link to="/register">创建创作者账号</router-link>
        </p>
        <details class="demo-access">
        <summary>使用演示账号 <span>＋</span></summary>
        <p class="demo-title">选择角色，查看对应工作空间</p>
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
        </details>
        <div class="login-bottom">帧序 FRAMEFLOW / 创作者工作室管理</div>
      </div>
    </section>
  </div>
</template>
