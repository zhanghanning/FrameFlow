<script setup>
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowDown, Odometer, VideoCamera, Wallet, UserFilled, User } from "@element-plus/icons-vue";
import { navigation } from "./router";
import { api, session, clearSession, hasRole, label, fail, homePath } from "./lib/api";

const route = useRoute(), router = useRouter();
const sections = [
  { name: "概览", title: "概览", icon: Odometer },
  { name: "创作管理", title: "创作", icon: VideoCamera },
  { name: "财务管理", title: "财务", icon: Wallet },
  { name: "工作室", title: "工作室", icon: UserFilled },
  { name: "账号中心", title: "账号", icon: User },
];
const financeOrder = ["/contracts", "/revenues", "/costs", "/settlements", "/balances"];
const groups = computed(() => sections.map((section) => {
  const links = navigation.filter((item) => item.group === section.name && hasRole(...item.roles));
  if (section.name === "账号中心") links.push({ path: "/profile", title: "账号与密码" });
  if (section.name === "财务管理") links.sort((a, b) => financeOrder.indexOf(a.path) - financeOrder.indexOf(b.path));
  return { ...section, links };
}).filter((section) => section.links.length));
const activeGroup = computed(() => groups.value.find((group) => group.links.some((item) => item.path === route.path)) || groups.value[0]);
const initials = computed(() => session.user?.displayName?.slice(-2) || "帧序");
const roles = computed(() => session.user?.roles?.map(label).join(" / ") || "创作者");
async function logout() {
  try {
    await api.post("/auth/logout");
    clearSession();
    router.push("/login");
  } catch (e) { fail(e); }
}
</script>
<template>
  <router-view v-if="route.meta.public" />
  <div v-else class="app-shell">
    <a href="#workspace-content" class="skip-link">跳至页面内容</a>
    <header class="studio-header">
      <div class="studio-masthead">
        <router-link :to="homePath()" class="brand" aria-label="帧序工作室首页">
          <span class="frame-mark" aria-hidden="true"><i></i><i></i><i></i><i></i><b></b></span>
          <span><strong>帧序</strong><small>FRAMEFLOW STUDIO</small></span>
        </router-link>
        <nav class="primary-nav" aria-label="工作空间分区">
          <router-link v-for="group in groups" :key="group.name" :to="group.links[0].path"
            :class="{ active: activeGroup?.name === group.name }"
            :aria-current="activeGroup?.name === group.name ? 'true' : undefined">
            <el-icon aria-hidden="true"><component :is="group.icon" /></el-icon><span>{{ group.title }}</span>
          </router-link>
        </nav>
        <el-dropdown trigger="click" class="account-menu">
          <button class="account-trigger" aria-label="打开账号菜单">
            <span class="avatar">{{ initials }}</span>
            <span class="account-copy"><b>{{ session.user?.displayName }}</b><small>{{ roles }}</small></span>
            <el-icon><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="router.push('/profile')">账号与密码</el-dropdown-item>
              <el-dropdown-item @click="router.push('/role-applications')">身份申请</el-dropdown-item>
              <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <div class="studio-subnav">
        <span class="subnav-caption">{{ activeGroup?.name }}</span>
        <nav aria-label="当前分区页面">
          <router-link v-for="item in activeGroup?.links" :key="item.path" :to="item.path"
            :class="{ active: route.path === item.path }" :aria-current="route.path === item.path ? 'page' : undefined">
            {{ item.title }}
          </router-link>
        </nav>
        <time class="studio-date">{{ new Date().toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit", weekday: "short" }) }}</time>
      </div>
    </header>
    <main id="workspace-content" class="page-main" tabindex="-1"><router-view :key="route.path" /></main>
    <footer class="app-footer"><span>帧序 / FRAMEFLOW STUDIO</span><span>素材 · 制作 · 分账</span></footer>
  </div>
</template>
