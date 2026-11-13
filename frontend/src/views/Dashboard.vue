<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Plus, RefreshRight, FolderOpened, Picture, TrendCharts, Wallet, VideoCamera, Bell, CircleCheck, ArrowRight } from "@element-plus/icons-vue";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import LoadError from "../components/LoadError.vue";
import { api, money, date, session, hasRole } from "../lib/api";

const router = useRouter(), loading = ref(true), error = ref("");
const dashboard = ref({ metrics: {}, trend: [], platforms: [], recentProjects: [], recentRevenues: [], expiringAssets: [], pendingSettlements: [] });
const canReadFinance = computed(() => hasRole("ADMIN", "FINANCE", "REVIEWER", "CREATOR"));
const personalFinance = computed(() => !hasRole("ADMIN", "FINANCE", "REVIEWER"));
const newCreator = computed(() => !loading.value && !error.value && hasRole("CREATOR") && !hasRole("ADMIN", "PRODUCER", "FINANCE", "REVIEWER") && !dashboard.value.recentProjects?.length && !dashboard.value.metrics.assetCount);
const metrics = computed(() => [
  { name: "进行中的项目", value: dashboard.value.metrics.activeProjects, caption: "计划、制作与审核中的项目", path: "/projects", icon: FolderOpened, tone: "sand" },
  { name: "可用素材", value: dashboard.value.metrics.assetCount, caption: "当前权限范围内的素材", path: "/assets", icon: Picture, tone: "blue" },
  ...(canReadFinance.value ? [
    { name: "累计确认净收入", value: dashboard.value.metrics.revenue, money: true, caption: personalFinance.value ? "本人平台账号的已确认收入" : "已确认收入与退款合计", path: "/revenues", icon: TrendCharts, tone: "peach" },
    { name: "待结算净收入", value: dashboard.value.metrics.unsettled, money: true, caption: "尚未分配至结算单", path: "/settlements", icon: Wallet, tone: "ink" },
  ] : []),
]);
const trend = computed(() => (dashboard.value.trend || []).slice(-6));
const maximum = computed(() => Math.max(...trend.value.flatMap((x) => [Number(x.revenue), Number(x.cost)]), 1) * 1.15);
const minimum = computed(() => Math.min(...trend.value.flatMap((x) => [Number(x.revenue), Number(x.cost)]), 0) * 1.15);
const chartY = (value) => 158 - ((Number(value || 0) - minimum.value) / (maximum.value - minimum.value)) * 132;
const chartX = (i) => 56 + i * 500 / Math.max(trend.value.length - 1, 1);
const points = (key) => trend.value.map((x, i) => chartX(i) + "," + chartY(x[key])).join(" ");
const recentRevenue = computed(() => trend.value.reduce((sum, item) => sum + Number(item.revenue || 0), 0));
const recentCost = computed(() => trend.value.reduce((sum, item) => sum + Number(item.cost || 0), 0));
const today = new Date().toLocaleDateString("en-CA");
const todayLabel = new Date().toLocaleDateString("zh-CN", { month: "long", day: "numeric", weekday: "long" });
const reminders = computed(() => [
  ...(dashboard.value.pendingSettlements || []).map((item) => ({
    id: "settlement-" + item.id, type: "结算待审核", title: item.projectName || item.contractName,
    note: item.settlementNo, path: "/settlements?detail=" + item.id,
  })),
  ...(dashboard.value.expiringAssets || []).map((item) => ({
    id: "asset-" + item.id, type: String(item.licenseEnd).slice(0, 10) < today ? "授权已到期" : "授权即将到期",
    title: item.name, note: date(item.licenseEnd) + " 到期", path: "/assets?detail=" + item.id,
  })),
]);
async function load() {
  loading.value = true;
  error.value = "";
  try { dashboard.value = await api.get("/dashboard"); }
  catch (e) { error.value = e.message; }
  finally { loading.value = false; }
}
onMounted(load);
</script>
<template>
  <div class="studio-overview" v-loading="loading">
    <PageHeader title="工作室总览" subtitle="你的制作进展与收益记录，都在这里。" eyebrow="">
      <el-button :icon="RefreshRight" @click="load">刷新概览</el-button>
    </PageHeader>
    <LoadError :message="error" @retry="load" />
    <section class="overview-welcome">
      <div class="welcome-copy">
        <span class="welcome-date">{{ todayLabel }}</span>
        <h2>{{ session.user?.displayName || "你好" }}，欢迎回到工作室。</h2>
        <p>{{ newCreator ? "先整理你的第一份素材、完善创作者资料，再联系项目统筹加入制作。" : "接续一个项目，整理一份素材，让创作与协作继续向前。" }}</p>
        <div class="welcome-actions">
          <el-button v-if="hasRole('ADMIN', 'PRODUCER')" type="primary" :icon="Plus" @click="router.push('/projects?create=1')">创建项目</el-button>
          <el-button v-else-if="hasRole('CREATOR')" type="primary" :icon="Plus" @click="router.push('/assets?upload=1')">上传素材</el-button>
          <el-button v-else-if="canReadFinance" type="primary" @click="router.push('/settlements')">查看收益结算</el-button>
          <router-link :to="newCreator ? '/creators?edit=mine' : '/projects'">{{ newCreator ? "完善创作者资料" : "进入创作项目" }} <el-icon><ArrowRight /></el-icon></router-link>
        </div>
        <router-link v-if="newCreator" class="welcome-role-link" to="/role-applications">需要更多协作岗位？前往身份申请</router-link>
      </div>
      <div class="welcome-illustration" aria-hidden="true">
        <div class="illustration-glow"></div>
        <svg viewBox="0 0 330 220" focusable="false">
          <defs>
            <pattern id="slate-stripes" width="36" height="26" patternUnits="userSpaceOnUse" patternTransform="skewX(-24)">
              <rect width="18" height="26" fill="#e9d3b9" />
            </pattern>
          </defs>
          <g transform="translate(205 47) rotate(13)">
            <rect width="95" height="122" rx="12" fill="#d9c2a6" />
            <rect x="9" y="9" width="77" height="104" rx="7" fill="#f9efe1" />
            <path d="M18 82 42 54 61 72 82 44V101H18Z" fill="#d5bda2" />
            <circle cx="33" cy="34" r="10" fill="#e3a176" />
          </g>
          <g transform="translate(52 73) rotate(-7)">
            <rect y="0" width="190" height="123" rx="14" fill="#202735" />
            <rect x="14" y="35" width="162" height="74" rx="7" fill="#354154" />
            <path d="M34 58H85M34 73H71M34 89H104" stroke="#b8c0c9" stroke-width="5" stroke-linecap="round" />
            <circle cx="141" cy="71" r="23" fill="#c65536" />
            <path d="M136 59 153 71 136 83Z" fill="#fff4e6" />
            <rect x="0" y="-10" width="190" height="26" rx="5" fill="#202735" transform="rotate(-10)" />
            <rect x="5" y="-8" width="180" height="22" rx="3" fill="url(#slate-stripes)" transform="rotate(-10)" />
          </g>
          <g transform="translate(27 154) rotate(12)">
            <rect width="58" height="46" rx="9" fill="#c65536" />
            <path d="M19 14H39V32H19Z" fill="none" stroke="#f9e2d2" stroke-width="2.5" />
            <path d="M13 18V13H18M40 13H45V18M45 28V33H40M18 33H13V28" fill="none" stroke="#f9e2d2" stroke-width="2" />
          </g>
        </svg>
      </div>
    </section>
    <section class="overview-numbers" :class="{ 'is-production-only': !canReadFinance }" aria-label="工作室关键数据">
      <router-link v-for="metric in metrics" :key="metric.name" :to="metric.path" class="overview-number" :class="'tone-' + metric.tone">
        <div class="number-label"><span>{{ metric.name }}</span><span class="number-icon"><el-icon><component :is="metric.icon" /></el-icon></span></div>
        <strong :class="{ negative: metric.value < 0 }"><small v-if="metric.money">¥</small>{{ metric.money ? money(metric.value) : metric.value || 0 }}</strong>
        <p>{{ metric.caption }} <span>↗</span></p>
      </router-link>
    </section>
    <div class="overview-production">
      <section class="production-register">
        <div class="editorial-heading"><div><h2>最近创作项目</h2><p>查看状态，接续团队的制作进度</p></div><router-link to="/projects">全部项目 <span>↗</span></router-link></div>
        <div v-if="dashboard.recentProjects?.length" class="project-register">
          <router-link v-for="project in dashboard.recentProjects" :key="project.id" :to="'/projects?detail=' + project.id" class="project-register-row">
            <span class="project-register-icon"><el-icon><VideoCamera /></el-icon></span>
            <div class="project-register-title"><h3>{{ project.name }}</h3><p>{{ project.clientName || "工作室原创" }} <span>/</span> {{ project.ownerName || "团队项目" }}</p></div>
            <StatusBadge :value="project.status" />
            <div class="project-register-date"><small>计划完成</small><time>{{ date(project.dueDate) }}</time></div>
            <span class="row-arrow" aria-hidden="true">↗</span>
          </router-link>
        </div>
        <div v-else class="editorial-empty"><span>尚未开始的故事</span><p>{{ hasRole("ADMIN", "PRODUCER") ? "创建项目，安排成员与制作计划。" : "联系项目统筹加入项目，作品将在这里展开。" }}</p><router-link to="/projects">前往创作项目 →</router-link></div>
      </section>
      <aside class="studio-agenda">
        <div class="editorial-heading"><div><h2><el-icon><Bell /></el-icon> 待办与提醒</h2><p>先处理这些需要留意的事项</p></div><span class="agenda-count">{{ reminders.length }} 项</span></div>
        <router-link v-for="item in reminders.slice(0, 5)" :key="item.id" :to="item.path" class="agenda-item">
          <span class="agenda-type">{{ item.type }}</span><h3>{{ item.title }}</h3><p>{{ item.note }} <span>↗</span></p>
        </router-link>
        <div v-if="!reminders.length" class="agenda-empty"><span class="agenda-check"><el-icon><CircleCheck /></el-icon></span><h3>当前没有待办</h3><p>待审结算与素材授权提醒<br />会汇集在这里。</p></div>
        <p v-if="reminders.length > 5" class="agenda-more">另有 {{ reminders.length - 5 }} 项，请在结算与素材页面查看。</p>
      </aside>
    </div>
    <section v-if="canReadFinance" class="overview-finance">
      <div class="editorial-heading"><div><h2>{{ personalFinance ? "我的收入记录" : "财务记录" }}</h2><p>收入、成本与分配，保持清楚</p></div><router-link to="/settlements">查看收益结算 <span>↗</span></router-link></div>
      <div class="finance-spread">
        <div class="finance-chart-block">
          <div class="finance-chart-title"><h3>收入与成本趋势</h3><span>最近 {{ trend.length }} 个有记录月份</span></div>
          <div v-if="trend.length" class="ledger-chart">
            <svg viewBox="0 0 590 200" role="img" aria-label="按月已确认净收入和已批准成本趋势">
              <g v-for="n in 4" :key="n">
                <line x1="48" x2="574" :y1="26 + (n - 1) * 44" :y2="26 + (n - 1) * 44" class="chart-grid-line" />
                <text x="0" :y="30 + (n - 1) * 44" class="axis-text">{{ ((minimum + (maximum - minimum) * (4 - n) / 3) / 1000).toFixed(1) }}k</text>
              </g>
              <polygon :points="chartX(0) + ',' + chartY(0) + ' ' + points('revenue') + ' ' + chartX(trend.length - 1) + ',' + chartY(0)" class="revenue-area" />
              <polyline :points="points('cost')" class="cost-line" />
              <polyline :points="points('revenue')" class="revenue-line" />
              <g v-for="(item, index) in trend" :key="item.month">
                <circle :cx="chartX(index)" :cy="chartY(item.revenue)" r="3" class="revenue-point"><title>{{ item.month }} 净收入 ¥{{ money(item.revenue) }} / 成本 ¥{{ money(item.cost) }}</title></circle>
                <text :x="chartX(index)" y="190" text-anchor="middle" class="axis-text">{{ item.month }}</text>
              </g>
            </svg>
          </div>
          <div v-else class="editorial-empty chart-empty"><p>确认收入或批准成本后，按月记录将在这里呈现。</p></div>
          <div class="ledger-chart-key"><span><i></i>净收入 <b>¥{{ money(recentRevenue) }}</b></span><span><i class="cost"></i>成本 <b>¥{{ money(recentCost) }}</b></span></div>
          <p v-if="personalFinance" class="ledger-scope-note">收入来自本人平台账号；成本来自参与项目的已批准成本。</p>
        </div>
        <div class="platform-ledger">
          <h3>平台净收入</h3><p>已确认收入与退款累计</p>
          <div v-for="item in dashboard.platforms" :key="item.name" class="platform-ledger-row"><span><i class="platform-dot"></i>{{ item.name }}</span><strong class="money" :class="{ negative: item.amount < 0 }">¥{{ money(item.amount) }}</strong></div>
          <p v-if="!dashboard.platforms?.length" class="empty-ledger">暂无平台收入记录</p>
          <router-link to="/revenues" class="ledger-link">打开收入与对账 <span>↗</span></router-link>
        </div>
      </div>
      <div class="recent-ledger">
        <div class="finance-chart-title"><h3>最近收入流水</h3><router-link to="/revenues">全部流水 →</router-link></div>
        <el-table :data="dashboard.recentRevenues || []" empty-text="暂无收入记录">
          <el-table-column label="作品 / 流水" min-width="180"><template #default="{ row }"><span class="table-primary">{{ row.workTitle || row.sourceRef }}</span><span class="table-secondary">{{ row.sourceRef }}</span></template></el-table-column>
          <el-table-column label="平台" min-width="110"><template #default="{ row }">{{ row.platformName || row.accountName }}</template></el-table-column>
          <el-table-column label="发生日期" width="130"><template #default="{ row }">{{ date(row.revenueDate) }}</template></el-table-column>
          <el-table-column label="净收入" width="145" align="right"><template #default="{ row }"><span class="money" :class="{ negative: row.netAmount < 0 }">¥{{ money(row.netAmount) }}</span></template></el-table-column>
          <el-table-column label="状态" width="115" align="right"><template #default="{ row }"><StatusBadge :value="row.status" /></template></el-table-column>
        </el-table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.studio-overview { min-width: 0; font-size: 14px; }
.studio-overview :deep(.eyebrow:empty) { display: none; }
.studio-overview :deep(.page-heading) { margin-bottom: 22px; }
.overview-welcome { position: relative; display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 28px 34px; margin-bottom: 22px; border-radius: 22px; overflow: hidden; background: linear-gradient(115deg, #eee0cf, #f6ecdf 65%, #ead8c3); box-shadow: var(--shadow-soft, 0 7px 24px #20273508); }
.welcome-copy { position: relative; z-index: 1; max-width: 690px; }
.welcome-date { display: inline-flex; padding: 6px 11px; background: #fffaf1a8; color: #765b42; font-size: 12px; border-radius: 8px; }
.welcome-copy h2 { font-size: clamp(23px, 2.2vw, 31px); font-weight: 600; line-height: 1.45; letter-spacing: -.5px; margin: 15px 0 10px; color: var(--ink); }
.welcome-copy > p { font-size: 14px; color: #756553; line-height: 1.8; max-width: 610px; }
.welcome-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 22px; margin-top: 22px; }
.welcome-actions :deep(.el-button) { height: 39px; padding: 0 19px; border-radius: 9px; font-size: 14px; }
.welcome-actions > a { display: inline-flex; align-items: center; gap: 10px; font-size: 13px; color: #665744; font-weight: 500; }
.welcome-role-link { display: inline-block; font-size: 12px; color: #795f49; margin-top: 15px; }
.welcome-illustration { width: 290px; height: 195px; position: relative; flex: 0 0 290px; margin: -10px -5px -5px 0; }
.welcome-illustration svg { display: block; position: relative; width: 100%; height: 100%; filter: drop-shadow(0 10px 10px #73563a15); }
.illustration-glow { width: 200px; height: 160px; background: #f8f0e4a8; border-radius: 50%; position: absolute; top: 15px; left: 50px; }
.overview-numbers { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 17px; margin-bottom: 24px; }
.overview-numbers.is-production-only { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.overview-number { min-width: 0; display: block; padding: 20px 23px; background: var(--surface); border: 1px solid #e9e4db; border-radius: 16px; box-shadow: var(--shadow-soft, 0 7px 24px #20273508); transition: border-color 160ms, transform 160ms; }
.overview-number:hover { border-color: #d4bca6; transform: translateY(-2px); }
.overview-number.tone-peach { background: #fff6ed; border-color: #efe1d2; }
.overview-number.tone-ink { background: #edf0f4; border-color: #e1e5ea; }
.number-label { display: flex; align-items: center; justify-content: space-between; gap: 10px; color: var(--muted); font-size: 13px; line-height: 1.5; }
.number-icon { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 11px; background: #efe5d5; color: #9a754c; flex-shrink: 0; }
.number-icon .el-icon { font-size: 18px; }
.tone-blue .number-icon { background: #e5ecf0; color: #597c91; }
.tone-peach .number-icon { background: #f5e0cf; color: #bb6744; }
.tone-ink .number-icon { background: #dfe5ed; color: #536578; }
.overview-number > strong { display: block; color: var(--ink); font-family: "DM Sans", sans-serif; font-size: clamp(26px, 2.45vw, 36px); font-weight: 600; font-variant-numeric: tabular-nums; letter-spacing: -1px; line-height: 1.2; margin: 17px 0 10px; white-space: nowrap; }
.overview-number > strong.negative { color: #b2563c; }
.overview-number > strong > small { font-size: 19px; margin-right: 4px; font-weight: 400; }
.overview-number > p { font-size: 12px; color: var(--muted); display: flex; justify-content: space-between; gap: 8px; line-height: 1.65; }
.overview-number > p > span { color: var(--accent); font-size: 17px; flex-shrink: 0; }
.overview-production { display: grid; grid-template-columns: minmax(0, 1.65fr) minmax(280px, 1fr); gap: 22px; margin-bottom: 24px; align-items: start; }
.production-register, .studio-agenda, .overview-finance { min-width: 0; padding: 25px; background: var(--surface); border: 1px solid #e7e2d9; border-radius: 18px; box-shadow: var(--shadow-soft, 0 7px 24px #20273508); }
.editorial-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.editorial-heading h2 { color: var(--ink); font-size: 19px; font-weight: 600; line-height: 1.5; }
.editorial-heading h2 .el-icon { font-size: 19px; vertical-align: -3px; color: #a96b46; }
.editorial-heading p { color: var(--muted); font-size: 13px; line-height: 1.7; margin-top: 5px; }
.editorial-heading > a { display: inline-flex; align-items: center; gap: 8px; font-size: 13px; color: var(--accent); white-space: nowrap; }
.project-register { display: grid; gap: 10px; }
.project-register-row { display: flex; align-items: center; gap: 14px; padding: 16px 15px; border-radius: 12px; background: #f6f4ef; transition: background 150ms; }
.project-register-row:hover { background: #eee7dc; }
.project-register-icon { display: grid; place-items: center; background: #e9e0d2; color: #9a7654; width: 42px; height: 42px; border-radius: 11px; flex-shrink: 0; }
.project-register-icon .el-icon { font-size: 21px; }
.project-register-row:nth-child(2n) .project-register-icon { background: #e1e7ea; color: #638090; }
.project-register-title { min-width: 0; flex: 1; }
.project-register-title h3 { font-size: 14px; font-weight: 600; color: var(--ink); line-height: 1.6; margin-bottom: 5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.project-register-title p { color: var(--muted); font-size: 12px; line-height: 1.6; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.project-register-title p > span { padding: 0 6px; opacity: .6; }
.project-register-date { text-align: right; flex-shrink: 0; }
.project-register-date small { display: block; color: var(--muted); font-size: 11px; margin-bottom: 6px; }
.project-register-date time { color: #55606d; font-size: 12px; font-variant-numeric: tabular-nums; }
.row-arrow { color: #a3896c; font-size: 18px; }
.editorial-empty { padding: 30px 22px; text-align: center; border-radius: 13px; background: #f6f3ed; }
.editorial-empty > span { color: var(--ink); font-size: 19px; font-weight: 500; }
.editorial-empty p { margin: 12px 0; color: var(--muted); font-size: 13px; line-height: 1.8; }
.editorial-empty a { display: inline-block; color: var(--accent); font-size: 13px; margin-top: 5px; }
.studio-agenda { background: #f2e9dc; border-color: #e9ddcc; }
.agenda-count { display: inline-flex; align-items: center; justify-content: center; padding: 6px 10px; border-radius: 8px; background: #e8dbc7; color: #8d6c45; font-size: 12px; white-space: nowrap; }
.agenda-item { display: block; padding: 16px 17px; margin-top: 10px; border-radius: 12px; background: #fffcf5d9; transition: background 150ms; }
.agenda-item:hover { background: #fffdf8; }
.agenda-type { display: inline-block; padding: 3px 7px; border-radius: 5px; font-size: 11px; color: #a36841; background: #f4e9d7; }
.agenda-item h3 { font-size: 14px; line-height: 1.6; font-weight: 500; margin: 8px 0 6px; color: var(--ink); }
.agenda-item p { display: flex; justify-content: space-between; gap: 12px; font-size: 12px; line-height: 1.6; color: var(--muted); overflow-wrap: anywhere; }
.agenda-item p > span { color: var(--accent); flex-shrink: 0; }
.agenda-empty { padding: 32px 14px; text-align: center; border-radius: 12px; background: #fffcf58c; }
.agenda-check { display: grid; place-items: center; margin: 0 auto 16px; width: 50px; height: 50px; border-radius: 16px; color: #8d9c85; background: #e9eddf; }
.agenda-check .el-icon { font-size: 28px; }
.agenda-empty h3 { font-size: 15px; font-weight: 500; color: var(--ink); }
.agenda-empty p { color: var(--muted); font-size: 13px; line-height: 1.8; margin-top: 10px; }
.agenda-more { font-size: 12px; color: #7a6b58; line-height: 1.7; margin-top: 15px; }
.overview-finance { padding: 26px; }
.finance-spread { display: grid; grid-template-columns: minmax(0, 1.6fr) minmax(260px, 1fr); gap: 20px; }
.finance-chart-block, .platform-ledger { min-width: 0; padding: 22px; border-radius: 14px; background: #f7f4ee; }
.platform-ledger { background: #f0f2f4; }
.finance-chart-title { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 18px; }
.finance-chart-title h3, .platform-ledger h3 { font-size: 15px; font-weight: 600; color: var(--ink); }
.finance-chart-title > span, .finance-chart-title > a { font-size: 12px; color: var(--muted); line-height: 1.6; }
.finance-chart-title > a { color: var(--accent); }
.ledger-chart { height: 210px; }
.ledger-chart svg { width: 100%; height: 100%; display: block; }
.chart-grid-line { stroke: #e1dcd2; stroke-width: .8; stroke-dasharray: 3 5; }
.axis-text { fill: var(--muted); font-size: 11px; }
.revenue-area { fill: #c6553610; }
.revenue-line { fill: none; stroke: var(--accent); stroke-width: 2.5; stroke-linejoin: round; stroke-linecap: round; }
.cost-line { fill: none; stroke: #8b9aa9; stroke-width: 2; stroke-dasharray: 5 4; }
.revenue-point { fill: #fffdf8; stroke: var(--accent); stroke-width: 2; }
.chart-empty { min-height: 210px; display: grid; place-items: center; padding: 20px; }
.ledger-chart-key { display: flex; flex-wrap: wrap; gap: 15px 24px; padding-top: 14px; }
.ledger-chart-key > span { display: flex; align-items: center; gap: 7px; font-size: 12px; color: var(--muted); }
.ledger-chart-key i { width: 10px; height: 10px; border-radius: 3px; background: var(--accent); flex-shrink: 0; }
.ledger-chart-key i.cost { background: #8b9aa9; }
.ledger-chart-key b { font-size: 14px; color: var(--ink); font-weight: 600; font-variant-numeric: tabular-nums; margin-left: 2px; }
.ledger-scope-note { margin-top: 14px; font-size: 12px; color: var(--muted); line-height: 1.8; }
.platform-ledger > p { color: var(--muted); font-size: 12px; margin-top: 8px; margin-bottom: 16px; line-height: 1.7; }
.platform-ledger-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 0; }
.platform-ledger-row + .platform-ledger-row { border-top: 1px solid #e0e5e8; }
.platform-ledger-row > span { display: inline-flex; align-items: center; gap: 10px; font-size: 14px; color: #556373; }
.platform-dot { display: inline-block; width: 8px; height: 8px; border-radius: 3px; background: #a2b3c3; flex-shrink: 0; }
.platform-ledger-row:nth-of-type(2n) .platform-dot { background: #d3a888; }
.platform-ledger-row strong { font-size: 16px; font-weight: 600; font-variant-numeric: tabular-nums; }
.ledger-link { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 11px 13px; border-radius: 9px; color: #657487; background: #e5e9ed; font-size: 13px; margin-top: 17px; }
.empty-ledger { padding: 20px 0; }
.recent-ledger { margin-top: 27px; }
.recent-ledger :deep(.el-table) { font-size: 14px; --el-table-header-bg-color: #f4f0e8; --el-table-row-hover-bg-color: #f8f5ef; }
.recent-ledger :deep(.el-table th.el-table__cell) { font-size: 12px; }
.recent-ledger :deep(.table-secondary) { font-size: 12px; }
.recent-ledger :deep(.el-table td.el-table__cell) { padding: 15px 0; }
@media (max-width: 1200px) {
  .overview-welcome { padding: 26px 29px; }
  .welcome-illustration { flex-basis: 245px; width: 245px; height: 180px; }
  .overview-number { padding: 18px; }
  .number-label { font-size: 12px; }
  .number-icon { width: 31px; height: 31px; border-radius: 9px; }
  .overview-number > strong { font-size: 29px; }
  .overview-production { grid-template-columns: minmax(0, 1.5fr) minmax(270px, 1fr); }
  .project-register-date { display: none; }
  .production-register, .studio-agenda, .overview-finance { padding: 22px; }
  .finance-spread { grid-template-columns: minmax(0, 1.4fr) minmax(230px, 1fr); }
  .finance-chart-block, .platform-ledger { padding: 19px; }
}
@media (max-width: 980px) {
  .overview-numbers { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 15px; }
  .overview-number > strong { font-size: 35px; }
  .number-label { font-size: 14px; }
  .welcome-illustration { flex-basis: 200px; width: 200px; height: 160px; margin-right: -14px; }
  .overview-welcome { gap: 10px; }
  .overview-production { grid-template-columns: 1fr; gap: 20px; }
  .project-register-date { display: block; }
  .studio-agenda { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
  .studio-agenda .editorial-heading, .agenda-empty, .agenda-more { grid-column: 1 / -1; }
  .studio-agenda .editorial-heading { margin-bottom: 4px; }
  .agenda-item { margin: 0; }
  .finance-spread { grid-template-columns: 1fr; }
  .platform-ledger-row { padding: 12px 0; }
}
@media (max-width: 720px) {
  .studio-overview :deep(.page-heading) { margin-bottom: 19px; }
  .overview-welcome { padding: 24px; border-radius: 18px; margin-bottom: 18px; min-height: 225px; }
  .welcome-copy { max-width: 100%; }
  .welcome-copy h2 { font-size: 24px; margin-top: 14px; max-width: 330px; }
  .welcome-copy > p { font-size: 13px; padding-right: 10px; max-width: 410px; }
  .welcome-date { font-size: 12px; }
  .welcome-actions { gap: 16px; margin-top: 19px; }
  .welcome-actions :deep(.el-button) { font-size: 13px; padding: 0 15px; }
  .welcome-actions > a { font-size: 13px; gap: 6px; }
  .welcome-illustration { position: absolute; right: -45px; bottom: -30px; width: 200px; height: 150px; opacity: .13; }
  .overview-numbers { gap: 12px; margin-bottom: 20px; }
  .overview-number { padding: 16px; border-radius: 14px; }
  .number-label { font-size: 12px; gap: 5px; }
  .number-icon { width: 28px; height: 28px; border-radius: 8px; }
  .number-icon .el-icon { font-size: 15px; }
  .overview-number > strong { font-size: 29px; margin: 14px 0 9px; }
  .overview-number > strong > small { font-size: 17px; margin-right: 2px; }
  .overview-number > p { font-size: 11px; line-height: 1.7; }
  .overview-production { margin-bottom: 20px; gap: 20px; }
  .production-register, .studio-agenda, .overview-finance { padding: 20px; border-radius: 16px; }
  .editorial-heading { align-items: flex-start; gap: 12px; margin-bottom: 17px; }
  .editorial-heading h2 { font-size: 18px; }
  .editorial-heading p { font-size: 12px; }
  .editorial-heading > a { font-size: 12px; margin-top: 5px; gap: 4px; }
  .project-register-row { flex-wrap: wrap; padding: 14px 12px; gap: 10px; }
  .project-register-icon { width: 34px; height: 34px; border-radius: 9px; }
  .project-register-icon .el-icon { font-size: 18px; }
  .project-register-title h3 { font-size: 14px; }
  .project-register-title p { font-size: 11px; }
  .project-register-date { display: none; }
  .project-register-row :deep(.status-badge) { font-size: 10px; padding: 6px; }
  .row-arrow { display: none; }
  .studio-agenda { display: block; }
  .studio-agenda .editorial-heading { margin-bottom: 17px; }
  .agenda-item { margin-top: 10px; padding: 15px; }
  .agenda-item h3 { font-size: 14px; }
  .agenda-type { font-size: 11px; }
  .agenda-item p { font-size: 12px; }
  .agenda-empty { padding: 27px 15px; }
  .finance-spread { gap: 15px; }
  .finance-chart-block, .platform-ledger { padding: 17px; border-radius: 12px; }
  .finance-chart-title { margin-bottom: 14px; }
  .finance-chart-title h3, .platform-ledger h3 { font-size: 14px; }
  .finance-chart-title > span, .finance-chart-title > a { font-size: 11px; }
  .ledger-chart { height: auto; margin: 0 -7px; }
  .ledger-chart svg { height: auto; min-height: 150px; }
  .ledger-chart-key { gap: 12px; padding-top: 6px; }
  .ledger-chart-key > span { font-size: 11px; gap: 5px; }
  .ledger-chart-key b { font-size: 13px; }
  .ledger-scope-note { font-size: 11px; }
  .platform-ledger-row > span { font-size: 13px; }
  .platform-ledger-row strong { font-size: 15px; }
  .recent-ledger { margin-top: 23px; }
  .recent-ledger :deep(.el-table) { font-size: 13px; }
}
@media (max-width: 420px) {
  .overview-number { padding: 14px 12px; }
  .overview-number > strong { font-size: 25px; }
  .number-icon { width: 25px; height: 25px; }
  .number-label { font-size: 11px; }
  .welcome-copy h2 { font-size: 22px; }
  .welcome-actions { gap: 12px; }
  .production-register, .studio-agenda, .overview-finance { padding: 17px; }
  .project-register-icon { display: none; }
}
@media (prefers-reduced-motion: reduce) {
  .overview-number, .project-register-row, .agenda-item { transition: none; }
  .overview-number:hover { transform: none; }
}
</style>
