import { createRouter, createWebHistory } from "vue-router";
import {
  session,
  restoreSession,
  hasRole,
  homePath,
  onPermissionsChanged,
} from "./lib/api";
const all = ["ADMIN", "PRODUCER", "FINANCE", "REVIEWER", "CREATOR"];
export const navigation = [
  {
    path: "/",
    title: "工作台",
    icon: "DataBoard",
    group: "概览",
    roles: all,
    component: () => import("./views/Dashboard.vue"),
  },
  {
    path: "/projects",
    title: "创作项目",
    icon: "FolderOpened",
    group: "创作管理",
    roles: all,
    component: () => import("./views/Projects.vue"),
  },
  {
    path: "/assets",
    title: "素材资产",
    icon: "Picture",
    group: "创作管理",
    roles: all,
    component: () => import("./views/Assets.vue"),
  },
  {
    path: "/works",
    title: "作品与发布",
    icon: "VideoCamera",
    group: "创作管理",
    roles: all,
    component: () => import("./views/Works.vue"),
  },
  {
    path: "/revenues",
    title: "收入与对账",
    icon: "TrendCharts",
    group: "财务管理",
    roles: ["ADMIN", "FINANCE", "REVIEWER", "CREATOR"],
    component: () => import("./views/Revenues.vue"),
  },
  {
    path: "/costs",
    title: "项目成本",
    icon: "Tickets",
    group: "财务管理",
    roles: ["ADMIN", "PRODUCER", "FINANCE", "REVIEWER"],
    resource: "costs",
  },
  {
    path: "/contracts",
    title: "分成合同",
    icon: "DocumentChecked",
    group: "财务管理",
    roles: ["ADMIN", "FINANCE", "REVIEWER", "CREATOR"],
    component: () => import("./views/Contracts.vue"),
  },
  {
    path: "/settlements",
    title: "收益结算",
    icon: "Wallet",
    group: "财务管理",
    roles: ["ADMIN", "FINANCE", "REVIEWER", "CREATOR"],
    component: () => import("./views/Settlements.vue"),
  },
  {
    path: "/balances",
    title: "余额与付款",
    icon: "CreditCard",
    group: "财务管理",
    roles: ["ADMIN", "FINANCE", "REVIEWER", "CREATOR"],
    component: () => import("./views/Balances.vue"),
  },
  {
    path: "/creators",
    title: "创作者",
    icon: "User",
    group: "工作室",
    roles: all,
    resource: "creators",
  },
  {
    path: "/platform-accounts",
    title: "平台账号",
    icon: "Connection",
    group: "工作室",
    roles: all,
    component: () => import("./views/PlatformAccounts.vue"),
  },
  {
    path: "/users",
    title: "团队权限",
    icon: "UserFilled",
    group: "工作室",
    roles: ["ADMIN"],
    resource: "users",
  },
  {
    path: "/role-applications",
    title: "身份申请",
    icon: "DocumentChecked",
    group: "账号中心",
    roles: all,
    component: () => import("./views/RoleApplications.vue"),
  },
  {
    path: "/audit-logs",
    title: "操作日志",
    icon: "Clock",
    group: "工作室",
    roles: ["ADMIN", "REVIEWER"],
    resource: "audit-logs",
  },
];
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      component: () => import("./views/Login.vue"),
      meta: { title: "登录", public: true },
    },
    {
      path: "/register",
      component: () => import("./views/Register.vue"),
      meta: { title: "注册", public: true },
    },
    {
      path: "/pending-access",
      redirect: "/",
    },
    ...navigation.map((item) => ({
      path: item.path,
      component: item.component || (() => import("./views/Resource.vue")),
      props: item.resource ? { resource: item.resource } : undefined,
      meta: { title: item.title, roles: item.roles },
    })),
    {
      path: "/profile",
      component: () => import("./views/Profile.vue"),
      meta: { title: "账号与密码" },
    },
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
});
router.beforeEach(async (to) => {
  document.title = `${to.meta.title || "工作台"} · 帧序 FrameFlow`;
  if (to.meta.public) return true;
  if (!session.token) return "/login";
  try {
    await restoreSession();
  } catch {
    return "/login";
  }
  if (to.meta.roles && !hasRole(...to.meta.roles)) return homePath();
});
onPermissionsChanged(async () => {
  const current = router.currentRoute.value;
  if (current.meta.public) return;
  const hasAccess = !current.meta.roles || hasRole(...current.meta.roles);
  if (!hasAccess) {
    await router.replace(homePath());
  }
});
export default router;
