<script setup>
import { computed } from "vue";
import { hasRole } from "../lib/api";
const props = defineProps({ stage: { type: String, required: true } });
const financeRead = computed(() => hasRole("ADMIN", "FINANCE", "REVIEWER", "CREATOR"));
const costRead = computed(() => hasRole("ADMIN", "FINANCE", "REVIEWER", "PRODUCER"));
const stages = computed(() => [
  { key: "contracts", title: "约定分成", path: financeRead.value ? "/contracts" : null },
  { key: "entries", title: "确认收支", path: financeRead.value ? "/revenues" : costRead.value ? "/costs" : null },
  { key: "settlements", title: "结算与审核", path: financeRead.value ? "/settlements" : null },
  { key: "payments", title: "登记付款", path: financeRead.value ? "/balances" : null },
]);
const guidance = computed(() => {
  if (props.stage === "contracts")
    return hasRole("ADMIN", "FINANCE")
      ? "先为项目成员约定分成并激活合同；合同日期覆盖收支发生日，分成合计为 100%。"
      : "查看项目的生效合同与分成规则，财务按合同核算收入和成本。";
  if (props.stage === "costs")
    return hasRole("ADMIN", "REVIEWER")
      ? "成本由财务或项目统筹录入，另一位审核人批准后纳入结算；审批时需要覆盖发生日期的生效合同。"
      : "登记项目实际支出，交由另一位审核人批准后纳入结算。项目统筹可登记本人负责项目的成本。";
  if (props.stage === "revenues")
    return hasRole("ADMIN", "FINANCE")
      ? "按发布记录登记或导入平台流水，核对后确认入账；确认时按收入日期匹配生效合同。"
      : "查看收入与退款记录；财务确认入账后，记录才会进入结算。";
  if (props.stage === "settlements") {
    if (hasRole("ADMIN", "FINANCE"))
      return "按合同与期间生成结算，核对后提交另一位审核人。草稿可修改重算或撤销，批准后计入应付余额。";
    if (hasRole("REVIEWER"))
      return "核对待审批结算的分成、收入和成本，批准或填写原因退回；提交人与审核人须为不同账号。";
    return "查看本人分成及结算进度，批准后的分成计入本人应付余额。";
  }
  return hasRole("ADMIN", "FINANCE")
    ? "完成线下转账后，登记付款金额、日期与凭证号；可付余额为已批准分成减已登记付款。"
    : "核对已批准分成、已登记付款与当前应付余额。";
});
const activeStage = computed(() => ["revenues", "costs"].includes(props.stage) ? "entries" : props.stage);
</script>
<template>
  <section class="finance-guide" aria-label="财务业务流程">
    <nav class="finance-guide-steps" aria-label="财务步骤">
      <template v-for="(item, index) in stages" :key="item.key">
        <span v-if="index" class="finance-guide-arrow" aria-hidden="true">→</span>
        <component
          :is="item.path ? 'router-link' : 'span'"
          :to="item.path || undefined"
          :class="['finance-guide-step', { current: activeStage === item.key }]"
          :aria-current="activeStage === item.key ? 'step' : undefined"
        ><b>{{ index + 1 }}</b>{{ item.title }}</component>
      </template>
      <router-link v-if="activeStage === 'entries' && costRead && stage === 'revenues'" to="/costs" class="finance-guide-related">项目成本 ↗</router-link>
      <router-link v-if="stage === 'costs' && financeRead" to="/revenues" class="finance-guide-related">收入与对账 ↗</router-link>
    </nav>
    <p>{{ guidance }}</p>
  </section>
</template>
<style scoped>
.finance-guide { margin: 0 0 24px; padding: 20px 23px; border-radius: 16px; background: #ede5d9; color: var(--ink); }
.finance-guide-steps { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.finance-guide-step { display: inline-flex; align-items: center; gap: 8px; color: #59616a; font-size: 13px; text-decoration: none; padding: 6px 10px 6px 6px; border-radius: 25px; }
.finance-guide-step b { display: grid; place-items: center; width: 27px; height: 27px; border-radius: 50%; background: #fffcf5; font-size: 12px; font-weight: 600; }
.finance-guide-step.current { color: #fffdf8; background: var(--ink); font-weight: 600; padding-right: 15px; }
.finance-guide-step.current b { color: var(--ink); background: #ead5be; }
a.finance-guide-step:hover { background: #e0d4c4; color: var(--ink); }
a.finance-guide-step.current:hover { background: #354252; color: white; }
.finance-guide-arrow { color: #9a8a78; font-size: 13px; }
.finance-guide-related { margin-left: auto; color: #985137; font-size: 13px; text-underline-offset: 3px; }
.finance-guide p { margin: 14px 0 0; color: #59616a; font-size: 13px; line-height: 1.8; }
@media (max-width: 720px) {
  .finance-guide { padding: 16px; }
  .finance-guide-steps { gap: 7px; }
  .finance-guide-arrow { display: none; }
  .finance-guide-step { font-size: 12px; padding-right: 8px; }
  .finance-guide-related { margin: 5px 0; width: 100%; }
}
</style>
