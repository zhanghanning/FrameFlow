<script setup>
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  DataBoard,
  FolderOpened,
  Picture,
  VideoCamera,
  TrendCharts,
  Tickets,
  DocumentChecked,
  Wallet,
  CreditCard,
  User,
  Connection,
  UserFilled,
  Clock,
  Setting,
  Menu,
} from "@element-plus/icons-vue";
import { navigation } from "./router";
import {
  api,
  session,
  clearSession,
  hasRole,
  label,
  fail,
  homePath,
} from "./lib/api";
const icons = {
  DataBoard,
  FolderOpened,
  Picture,
  VideoCamera,
  TrendCharts,
  Tickets,
  DocumentChecked,
  Wallet,
  CreditCard,
  User,
  Connection,
  UserFilled,
  Clock,
  Setting,
  Menu,
};
const route = useRoute(),
  router = useRouter(),
  mobileOpen = ref(false);
const groups = computed(() =>
  ["概览", "创作管理", "财务管理", "工作室", "账号中心"]
    .map((name) => ({
      name,
      links: [
        ...navigation.filter(
          (item) => item.group === name && hasRole(...item.roles),
        ),
        ...(name === "账号中心"
          ? [{ path: "/profile", title: "账号与密码", icon: "Setting" }]
          : []),
      ],
    }))
    .filter((group) => group.links.length),
);
const initials = computed(() => session.user?.displayName?.slice(-2) || "帧序");
async function logout() {
  try {
    await api.post("/auth/logout");
    clearSession();
    router.push("/login");
  } catch (e) {
    fail(e);
  }
}
</script>
<template>
  <router-view v-if="route.meta.public" />
  <div v-else class="app-shell">
    <div
      v-if="mobileOpen"
      class="sidebar-backdrop"
      @click="mobileOpen = false"
    ></div>
    <aside class="sidebar" :class="{ open: mobileOpen }">
      <router-link :to="homePath()" class="brand"
        ><span class="brand-symbol">F<span>↗</span></span
        ><span><strong>帧序</strong><small>FRAMEFLOW</small></span></router-link
      >
      <div class="workspace-chip">
        <span class="workspace-avatar">F</span>
        <div><b>帧序创意工作室</b><small>Creator Studio</small></div>
        <span class="workspace-live"></span>
      </div>
      <nav>
        <div v-for="group in groups" :key="group.name" class="nav-group">
          <div class="nav-caption">{{ group.name }}</div>
          <router-link
            v-for="item in group.links"
            :key="item.path"
            :to="item.path"
            :class="['nav-item', { active: route.path === item.path }]"
            @click="mobileOpen = false"
            ><el-icon><component :is="icons[item.icon]" /></el-icon
            ><span>{{ item.title }}</span
            ><span v-if="route.path === item.path" class="active-dot"></span
          ></router-link>
        </div>
      </nav>
      <div class="sidebar-note">
        <div class="note-orbit">✦</div>
        <strong>让每一份创作<br />都有迹可循。</strong
        ><span>从第一帧，到每一笔收益</span>
      </div>
      <router-link to="/profile" class="sidebar-user"
        ><span class="avatar">{{ initials }}</span
        ><span
          ><b>{{ session.user?.displayName }}</b
          ><small>{{
            session.user?.roles?.length
              ? session.user.roles.map(label).join(" · ")
              : "创作者"
          }}</small></span
        ><el-icon><icons.Setting /></el-icon
      ></router-link>
    </aside>
    <div class="workspace-main">
      <header class="topbar">
        <div class="breadcrumb">
          <el-button
            class="mobile-toggle"
            text
            :icon="icons.Menu"
            @click="mobileOpen = !mobileOpen"
          /><span>工作空间</span><span class="slash">/</span
          ><strong>{{ route.meta.title }}</strong>
        </div>
        <div class="topbar-right">
          <span class="environment-pill"><i></i> 工作室空间</span
          ><span class="topbar-date">{{
            new Date().toLocaleDateString("zh-CN", {
              month: "long",
              day: "numeric",
              weekday: "short",
            })
          }}</span
          ><el-dropdown trigger="click"
            ><span class="topbar-avatar" tabindex="0">{{ initials }}</span
            ><template #dropdown
              ><el-dropdown-menu
                ><el-dropdown-item @click="router.push('/profile')"
                  >账号与密码</el-dropdown-item
                ><el-dropdown-item @click="router.push('/role-applications')"
                  >身份申请</el-dropdown-item
                ><el-dropdown-item divided @click="logout"
                  >退出登录</el-dropdown-item
                ></el-dropdown-menu
              ></template
            ></el-dropdown
          >
        </div>
      </header>
      <main class="page-main"><router-view :key="route.path" /></main>
      <footer class="app-footer">
        <span>帧序 FrameFlow</span><span>创作有序 · 收益有据</span>
      </footer>
    </div>
  </div>
</template>
