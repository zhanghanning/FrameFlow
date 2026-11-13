<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import {
  Plus,
  ArrowRight,
  TrendCharts,
  Wallet,
  Folder,
  Picture,
  Timer,
  DocumentChecked,
} from "@element-plus/icons-vue";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import LoadError from "../components/LoadError.vue";
import { api, money, date, session, hasRole } from "../lib/api";
const router = useRouter(),
  loading = ref(true),
  error = ref(""),
  dashboard = ref({
    metrics: {},
    trend: [],
    platforms: [],
    recentProjects: [],
    recentRevenues: [],
    expiringAssets: [],
    pendingSettlements: [],
  });
const newCreator = computed(
  () =>
    !loading.value &&
    !error.value &&
    hasRole("CREATOR") &&
    !hasRole("ADMIN", "PRODUCER", "FINANCE", "REVIEWER") &&
    !dashboard.value.recentProjects?.length &&
    !dashboard.value.metrics.assetCount,
);
const metrics = computed(() => [
  {
    name: "累计确认收入",
    value: dashboard.value.metrics.revenue,
    money: true,
    icon: TrendCharts,
    caption: "按已确认净收入统计",
    highlight: true,
  },
  {
    name: "待结算净收入",
    value: dashboard.value.metrics.unsettled,
    money: true,
    icon: Wallet,
    caption: "尚未分配至结算单",
  },
  {
    name: "进行中的项目",
    value: dashboard.value.metrics.activeProjects,
    icon: Folder,
    caption: "创意正在生长",
  },
  {
    name: "可用素材",
    value: dashboard.value.metrics.assetCount,
    icon: Picture,
    caption: "创作资产持续积累",
  },
]);
const trend = computed(() => dashboard.value.trend || []);
const maximum = computed(
  () =>
    Math.max(
      ...trend.value.flatMap((x) => [
        Number(x.revenue || 0),
        Number(x.cost || 0),
      ]),
      1,
    ) * 1.2,
);
const minimum = computed(
  () =>
    Math.min(
      ...trend.value.flatMap((x) => [
        Number(x.revenue || 0),
        Number(x.cost || 0),
      ]),
      0,
    ) * 1.3,
);
const chartY = (value) =>
  174 -
  ((Number(value || 0) - minimum.value) / (maximum.value - minimum.value)) *
    145;
const points = (key) =>
  trend.value
    .map(
      (x, i) =>
        `${50 + (i * 530) / Math.max(trend.value.length - 1, 1)},${chartY(x[key])}`,
    )
    .join(" ");
const colors = ["#6d9460", "#b9ca94", "#ead8ae", "#d3a58a", "#adc5bd"];
const platformTotal = computed(() =>
  (dashboard.value.platforms || []).reduce(
    (s, x) => s + Number(x.amount || 0),
    0,
  ),
);
const donutStyle = computed(() => {
  let p = 0;
  return {
    background:
      platformTotal.value > 0
        ? `conic-gradient(${dashboard.value.platforms
            .map((x, i) => {
              const start = p;
              p += (Number(x.amount) / platformTotal.value) * 100;
              return `${colors[i % colors.length]} ${start}% ${p}%`;
            })
            .join(",")})`
        : "#e8eee0",
  };
});
const revenueTotal = computed(() =>
  trend.value.reduce((s, x) => s + Number(x.revenue || 0), 0),
);
const costTotal = computed(() =>
  trend.value.reduce((s, x) => s + Number(x.cost || 0), 0),
);
const progress = (status) =>
  ({ PLANNING: 15, PRODUCING: 48, REVIEW: 80, COMPLETED: 100, ARCHIVED: 100 })[
    status
  ] || 10;
async function load() {
  loading.value = true;
  error.value = "";
  try {
    dashboard.value = await api.get("/dashboard");
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div v-loading="loading">
    <PageHeader
      :title="`${session.user?.displayName || '你好'}，创作正当时。`"
      subtitle="创作的每一步，收益的每一笔，都在这里。"
      eyebrow="STUDIO OVERVIEW"
      ><el-button :icon="Timer" @click="load">刷新概览</el-button
      ><el-button
        v-if="hasRole('ADMIN', 'PRODUCER')"
        type="primary"
        :icon="Plus"
        @click="router.push('/projects?create=1')"
        >创建项目</el-button
      ></PageHeader
    ><LoadError :message="error" @retry="load" />
    <section v-if="newCreator" class="creator-welcome">
      <div class="creator-welcome-heading">
        <div>
          <span class="eyebrow">YOUR FIRST FRAME</span>
          <h2>你的创作者空间，准备就绪。</h2>
          <p>从整理素材到参与项目，开始积累属于你的作品与收益。</p>
        </div>
        <span class="creator-welcome-star">✦</span>
      </div>
      <div class="creator-first-steps">
        <article>
          <span class="step-index">01</span>
          <h3>上传第一份素材</h3>
          <p>保存视频、图片与音频，记录版权范围和使用期限。</p>
          <el-button
            text
            type="primary"
            @click="router.push('/assets?upload=1')"
            >开始上传 ↗</el-button
          >
        </article>
        <article>
          <span class="step-index">02</span>
          <h3>完善创作者资料</h3>
          <p>补充个人介绍与联系方式，让团队更容易认识你。</p>
          <el-button
            text
            type="primary"
            @click="router.push('/creators?edit=mine')"
            >完善资料 ↗</el-button
          >
        </article>
        <article>
          <span class="step-index">03</span>
          <h3>加入项目协作</h3>
          <p>联系项目统筹加入项目；准备承担更多职责时，可申请相应岗位。</p>
          <el-button
            text
            type="primary"
            @click="router.push('/role-applications')"
            >查看身份申请 ↗</el-button
          >
        </article>
      </div>
    </section>
    <div class="metric-strip">
      <div
        v-for="metric in metrics"
        :key="metric.name"
        class="metric-card"
        :class="{ highlight: metric.highlight }"
      >
        <div class="metric-top">
          <span>{{ metric.name }}</span
          ><span class="metric-icon"
            ><el-icon><component :is="metric.icon" /></el-icon
          ></span>
        </div>
        <div class="metric-value">
          <span v-if="metric.money" class="currency">¥</span
          >{{ metric.money ? money(metric.value) : metric.value || 0 }}
        </div>
        <div class="metric-caption">
          <span v-if="metric.highlight" class="pill">{{
            hasRole("CREATOR") && !hasRole("ADMIN", "FINANCE", "REVIEWER")
              ? "个人平台账号"
              : "工作室"
          }}</span
          >{{ metric.caption }}
        </div>
      </div>
    </div>
    <div class="chart-grid">
      <section class="panel">
        <div class="panel-title">
          <div>
            <h2>收益趋势</h2>
            <p>每一份投入，都有回响</p>
          </div>
          <div class="chart-legend">
            <span><i></i>净收入</span><span><i class="cost"></i>项目成本</span>
          </div>
        </div>
        <div v-if="trend.length" class="trend-chart">
          <svg viewBox="0 0 620 215" preserveAspectRatio="none">
            <defs>
              <linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#90b178" stop-opacity=".25" />
                <stop offset="100%" stop-color="#90b178" stop-opacity=".01" />
              </linearGradient>
            </defs>
            <g v-for="n in 4" :key="n">
              <line
                x1="45"
                x2="598"
                :y1="28 + (n - 1) * 48"
                :y2="28 + (n - 1) * 48"
                stroke="#eef1e7"
                stroke-dasharray="4 4"
              />
              <text x="0" :y="32 + (n - 1) * 48" class="axis-text">
                {{
                  (
                    (minimum + ((maximum - minimum) * (4 - n)) / 3) /
                    1000
                  ).toFixed(0)
                }}k
              </text>
            </g>
            <polygon
              :points="`50,${chartY(0)} ${points('revenue')} ${50 + ((trend.length - 1) * 530) / Math.max(trend.length - 1, 1)},${chartY(0)}`"
              fill="url(#trendFill)"
            />
            <polyline
              :points="points('cost')"
              fill="none"
              stroke="#c7d6b1"
              stroke-width="2"
              stroke-linecap="round"
              stroke-dasharray="5 5"
            />
            <polyline
              :points="points('revenue')"
              fill="none"
              stroke="#749465"
              stroke-width="2.7"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <g v-for="(item, i) in trend" :key="item.month">
              <circle
                :cx="50 + (i * 530) / Math.max(trend.length - 1, 1)"
                :cy="chartY(item.revenue)"
                r="3.5"
                fill="#fff"
                stroke="#749465"
                stroke-width="2"
              >
                <title>
                  {{ item.month }}：收入 ¥{{ money(item.revenue) }} / 成本 ¥{{
                    money(item.cost)
                  }}
                </title>
              </circle>
              <text
                :x="50 + (i * 530) / Math.max(trend.length - 1, 1)"
                y="204"
                text-anchor="middle"
                class="axis-text"
              >
                {{ String(item.month).slice(5) }}月
              </text>
            </g>
          </svg>
        </div>
        <div v-else class="chart-empty">
          确认第一笔收入后，这里将展示收益趋势
        </div>
        <div class="chart-footer">
          <div>
            期间净收入 <b>¥{{ money(revenueTotal) }}</b>
          </div>
          <div>
            期间成本 <b>¥{{ money(costTotal) }}</b>
          </div>
        </div>
      </section>
      <section class="panel">
        <div class="panel-title">
          <div>
            <h2>平台收入分布</h2>
            <p>内容的价值，抵达更多地方</p>
          </div>
          <span class="muted small">已确认</span>
        </div>
        <div class="ring-layout">
          <div class="donut-chart" :style="donutStyle">
            <div class="donut-hole">
              <small>平台净收入</small><b>¥{{ money(platformTotal) }}</b>
            </div>
          </div>
          <div class="platform-list">
            <div
              v-for="(item, i) in dashboard.platforms"
              :key="item.name"
              class="platform-item"
            >
              <i :style="{ background: colors[i % colors.length] }"></i
              ><span>{{ item.name }}</span
              ><b
                >{{
                  platformTotal
                    ? ((Number(item.amount) / platformTotal) * 100).toFixed(1)
                    : 0
                }}%</b
              >
            </div>
            <span v-if="!dashboard.platforms?.length" class="small muted"
              >暂无平台收入</span
            >
          </div>
        </div>
        <div class="platform-note">来源于已确认的平台收入与退款记录</div>
      </section>
    </div>
    <div class="section-heading">
      <h2>最近的创作项目 <span style="margin-left: 9px">IN PROGRESS</span></h2>
      <router-link to="/projects">全部项目 →</router-link>
    </div>
    <div
      v-if="dashboard.recentProjects?.length"
      class="project-grid dashboard-projects"
    >
      <article
        v-for="(project, i) in dashboard.recentProjects.slice(0, 3)"
        :key="project.id"
        class="project-card"
        @click="router.push(`/projects?detail=${project.id}`)"
      >
        <div class="project-cover" :class="`tone-${i % 6}`">
          <span class="cover-type">FRAMEFLOW / CREATIVE SERIES</span>
          <div class="cover-title">{{ project.name }}</div>
          <span class="cover-star">✳</span>
          <div class="cover-shape"></div>
        </div>
        <div class="project-card-body">
          <div class="project-card-head">
            <h3>{{ project.name }}</h3>
            <StatusBadge :value="project.status" />
          </div>
          <p>{{ project.clientName || "工作室原创项目" }}</p>
          <div class="project-progress">
            <i :style="{ width: `${progress(project.status)}%` }"></i>
          </div>
          <div class="project-card-footer">
            <span>{{ project.ownerName || "团队协作" }}</span
            ><span>截止 {{ date(project.dueDate) }}</span>
          </div>
        </div>
      </article>
    </div>
    <el-empty
      v-else
      :description="
        hasRole('ADMIN', 'PRODUCER')
          ? '开始创建第一个创作项目'
          : '联系项目统筹，加入你的第一个创作项目'
      "
      :image-size="65"
    />
    <div class="dashboard-bottom">
      <section class="panel">
        <div class="panel-title">
          <div>
            <h2>最近收入</h2>
            <p>持续记录每一份创作价值</p>
          </div>
          <router-link
            v-if="hasRole('ADMIN', 'FINANCE', 'REVIEWER', 'CREATOR')"
            to="/revenues"
            >查看全部 →</router-link
          >
        </div>
        <el-table
          :data="dashboard.recentRevenues?.slice(0, 5) || []"
          empty-text="暂无收入记录"
          ><el-table-column label="收入来源" min-width="150"
            ><template #default="{ row }"
              ><span class="table-primary">{{
                row.workTitle || row.title || row.sourceRef
              }}</span
              ><span class="table-secondary">{{
                row.platformName || row.accountName || row.sourceRef
              }}</span></template
            ></el-table-column
          ><el-table-column label="日期" width="105"
            ><template #default="{ row }">{{
              date(row.revenueDate)
            }}</template></el-table-column
          ><el-table-column label="净收入" width="115" align="right"
            ><template #default="{ row }"
              ><span class="money" :class="{ negative: row.netAmount < 0 }"
                >¥{{ money(row.netAmount) }}</span
              ></template
            ></el-table-column
          ><el-table-column label="状态" width="95" align="right"
            ><template #default="{ row }"
              ><StatusBadge :value="row.status" /></template></el-table-column
        ></el-table>
      </section>
      <section class="panel">
        <div class="panel-title">
          <div>
            <h2>待办与提醒</h2>
            <p>让协作，更进一步</p>
          </div>
          <span class="small muted"
            >{{
              (dashboard.expiringAssets?.length || 0) +
              (dashboard.pendingSettlements?.length || 0)
            }}
            项</span
          >
        </div>
        <div
          v-for="item in dashboard.pendingSettlements?.slice(0, 3)"
          :key="`s-${item.id}`"
          class="task-row"
        >
          <span class="task-icon"
            ><el-icon><DocumentChecked /></el-icon
          ></span>
          <div>
            <h4>{{ item.settlementNo || "收益结算单" }} 待处理</h4>
            <p>
              {{ item.projectName || item.contractName }} ·
              {{ date(item.createdAt) }}
            </p>
          </div>
          <el-button
            text
            :icon="ArrowRight"
            aria-label="查看结算"
            @click="router.push(`/settlements?detail=${item.id}`)"
          />
        </div>
        <div
          v-for="item in dashboard.expiringAssets?.slice(0, 3)"
          :key="`a-${item.id}`"
          class="task-row"
        >
          <span class="task-icon"
            ><el-icon><Timer /></el-icon
          ></span>
          <div>
            <h4>
              {{ item.name }}
              {{
                item.licenseEnd && new Date(item.licenseEnd) < new Date()
                  ? "授权已到期"
                  : "授权即将到期"
              }}
            </h4>
            <p>{{ date(item.licenseEnd) }} 到期</p>
          </div>
          <el-button
            text
            :icon="ArrowRight"
            aria-label="查看素材"
            @click="router.push(`/assets?detail=${item.id}`)"
          />
        </div>
        <el-empty
          v-if="
            !dashboard.expiringAssets?.length &&
            !dashboard.pendingSettlements?.length
          "
          description="目前没有待处理事项"
          :image-size="64"
        />
      </section>
    </div>
  </div>
</template>
