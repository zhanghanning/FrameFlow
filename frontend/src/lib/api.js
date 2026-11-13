import axios from "axios";
import { reactive } from "vue";
import { ElMessage } from "element-plus";

export const session = reactive({
  token: localStorage.getItem("frameflow-token") || "",
  user: null,
  ready: false,
});
let permissionHandler;
export function onPermissionsChanged(handler) {
  permissionHandler = handler;
}
export const businessRoles = [
  "ADMIN",
  "PRODUCER",
  "FINANCE",
  "REVIEWER",
  "CREATOR",
];
export const api = axios.create({ baseURL: "/api", timeout: 30000 });
api.interceptors.request.use((config) => {
  if (session.token) config.headers.Authorization = `Bearer ${session.token}`;
  return config;
});
api.interceptors.response.use(
  (response) => {
    if (response.config.responseType === "blob") return response.data;
    if (response.data.code !== 0)
      return Promise.reject(new Error(response.data.msg || "请求未完成"));
    return response.data.data;
  },
  async (error) => {
    const status = error.response?.status;
    if (status === 401) {
      clearSession();
      if (!["/login", "/register"].includes(location.pathname))
        location.assign("/login");
    }
    if (status === 403 && session.token && error.config?.url !== "/auth/me") {
      try {
        await refreshSession();
        await permissionHandler?.();
      } catch {
        // 会话失效由上面的401处理，原业务错误仍返回当前页面。
      }
    }
    const failure = new Error(
      error.response?.data?.msg ||
        (error.code === "ECONNABORTED"
          ? "请求耗时较长，请稍后重试"
          : "服务连接失败，请检查后端是否启动"),
    );
    failure.status = status;
    return Promise.reject(failure);
  },
);
export function setSession(data) {
  session.token = data.token;
  session.user = data.user;
  session.ready = true;
  localStorage.setItem("frameflow-token", data.token);
}
export function clearSession() {
  session.token = "";
  session.user = null;
  session.ready = true;
  localStorage.removeItem("frameflow-token");
}
export async function refreshSession() {
  if (session.token) session.user = await api.get("/auth/me");
  session.ready = true;
  return session.user;
}
export async function restoreSession() {
  return refreshSession();
}
export function homePath() {
  return "/";
}
export function hasRole(...roles) {
  return (
    session.user?.roles?.some((role) =>
      roles.includes(typeof role === "string" ? role : role.code),
    ) || false
  );
}
export function fail(error) {
  ElMessage.error(error.message);
}
export async function download(path, filename) {
  const blob = await api.get(path, { responseType: "blob" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export const money = (value) =>
  Number(value || 0).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
export const date = (value) =>
  value ? String(value).replace("T", " ").slice(0, 10) : "—";
export const datetime = (value) =>
  value ? String(value).replace("T", " ").slice(0, 16) : "—";
export const labels = {
  ACTIVE: "有效",
  INACTIVE: "停用",
  PLANNING: "筹备中",
  PRODUCING: "制作中",
  REVIEW: "待审核",
  COMPLETED: "已完成",
  ARCHIVED: "已归档",
  DRAFT: "草稿",
  EDITING: "剪辑中",
  READY: "可发布",
  CONFIRMED: "已确认",
  APPROVED: "已批准",
  SUBMITTED: "待审批",
  INCOME: "收入",
  REFUND: "退款",
  ORIGINAL: "原创",
  LICENSED: "授权素材",
  VIDEO: "视频",
  IMAGE: "图片",
  AUDIO: "音频",
  DOCUMENT: "文档",
  ADMIN: "管理员",
  PRODUCER: "项目统筹",
  FINANCE: "财务",
  REVIEWER: "审核",
  CREATOR: "创作者",
  ALL: "全部平台",
  DOUYIN: "抖音",
  XIAOHONGSHU: "小红书",
  BILIBILI: "哔哩哔哩",
  LOGIN: "登录",
  CHANGE_PASSWORD: "修改密码",
  CREATE: "创建",
  UPDATE: "更新",
  UPLOAD: "上传素材",
  PUBLISH: "登记发布",
  IMPORT: "导入收入",
  CONFIRM: "确认入账",
  APPROVE: "批准",
  ACTIVATE: "激活合同",
  GENERATE: "生成结算",
  SUBMIT: "提交审批",
  REJECT: "退回",
  REGISTER: "用户注册",
  PENDING: "待审核",
  REJECTED: "已拒绝",
  ROLE_APPLICATION: "身份申请",
  APPLY_ROLE: "申请身份",
  APPROVE_ROLE: "批准身份申请",
  REJECT_ROLE: "拒绝身份申请",
  ASSET: "素材",
  PLATFORM_ACCOUNT: "平台账号",
  PROJECT: "创作项目",
  WORK: "作品",
  PUBLICATION: "发布记录",
  REVENUE: "收入记录",
  COST: "项目成本",
  CONTRACT: "分成合同",
  SETTLEMENT: "收益结算",
  PAYMENT: "线下付款",
  PRODUCTION: "制作",
  SHOOTING: "拍摄",
  TRAVEL: "差旅",
  LICENSE: "版权",
  EQUIPMENT: "设备",
  OTHER: "其他",
};
export const label = (value) => labels[value] || value || "—";
export function listItems(value) {
  return Array.isArray(value) ? value : value?.items || [];
}

export const roleDescriptions = {
  ADMIN: "管理团队身份与全工作室业务，配置账号和权限。",
  PRODUCER: "发起并管理自己负责的项目、成员与作品发布，使用获授权素材。",
  FINANCE: "录入及确认收益、管理分成合同、提交结算和登记付款。",
  REVIEWER: "审核成本与结算，查看财务明细和操作日志。",
  CREATOR: "维护本人资料、平台账号和素材，参与项目创作，查看本人收益。",
};
