package com.frameflow.service;

import static com.frameflow.common.Values.*;

import com.frameflow.common.*;
import com.frameflow.dto.Requests;
import com.frameflow.mapper.StudioMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceService {
  private final StudioMapper db;
  private final StudioService studio;
  private final AuthService auth;

  public FinanceService(StudioMapper db, StudioService studio, AuthService auth) {
    this.db = db;
    this.studio = studio;
    this.auth = auth;
  }

  @Transactional
  public long createRevenue(Requests.Revenue request) {
    Actor.current().require("ADMIN", "FINANCE");
    var row = validateRevenue(request);
    db.insertRevenue(row);
    long id = id(row.get("id"));
    auth.audit("CREATE", "REVENUE", id, request.sourceRef());
    return id;
  }

  public Map<String, Object> validateRevenue(Requests.Revenue request) {
    var publication = studio.publicationAccess(request.publicationId());
    oneOf(request.kind(), "INCOME", "REFUND");
    if (request.grossAmount().scale() > 2 || request.feeAmount().scale() > 2)
      throw BusinessException.bad("金额最多保留两位小数");
    if (request.grossAmount().signum() <= 0 || request.feeAmount().signum() < 0)
      throw BusinessException.bad("收入金额须大于0，费用须大于等于0");
    if (request.sourceRef() == null || request.sourceRef().isBlank())
      throw BusinessException.bad("平台流水号不能为空");
    if (request.feeAmount().compareTo(request.grossAmount()) > 0)
      throw BusinessException.bad("平台费用不能超过收入金额");
    var row = studio.data(request);
    row.put("platformAccountId", publication.get("platformAccountId"));
    row.put("contractId", null);
    row.put("importBatchId", null);
    if ("REFUND".equals(request.kind())) {
      if (request.originalRevenueId() == null) throw BusinessException.bad("退款必须关联原始收入");
      required(db.lockRevenue(request.originalRevenueId()), "原始收入");
      var original = required(db.revenue(request.originalRevenueId()), "原始收入");
      if (!"INCOME".equals(original.get("kind")) || !"CONFIRMED".equals(original.get("status")))
        throw BusinessException.bad("退款须关联已确认的收入");
      if (id(original.get("publicationId")) != request.publicationId())
        throw BusinessException.bad("退款须与原收入关联相同发布记录");
      if (request.revenueDate().isBefore(LocalDate.parse(original.get("revenueDate").toString())))
        throw BusinessException.bad("退款日期应不早于原收入日期");
      if (request.feeAmount().signum() != 0) throw BusinessException.bad("退款的平台费用应为0");
      if (db.refundedAmount(request.originalRevenueId())
              .add(request.grossAmount())
              .compareTo(money(original.get("netAmount")))
          > 0) throw BusinessException.bad("累计退款金额不能超过原收入净额");
      row.put("contractId", original.get("contractId"));
    } else if (request.originalRevenueId() != null) throw BusinessException.bad("普通收入无需关联原始收入");
    return row;
  }

  @Transactional
  public void confirmRevenue(long revenueId) {
    Actor.current().require("ADMIN", "FINANCE");
    required(db.lockRevenue(revenueId), "收入流水");
    var row = required(db.revenue(revenueId), "收入流水");
    status(row, "DRAFT");
    long contractId;
    if ("REFUND".equals(row.get("kind"))) contractId = id(row.get("contractId"));
    else {
      var contract = db.matchingContract(id(row.get("projectId")), row.get("revenueDate"));
      if (contract == null) throw BusinessException.conflict("收入日期尚无生效分成合同，请先激活合同");
      contractId = id(contract.get("id"));
    }
    db.confirmRevenue(revenueId, contractId, Actor.current().id());
    auth.audit("CONFIRM", "REVENUE", revenueId, "确认收入并锁定分成合同");
  }

  @Transactional
  public long createCost(Requests.Cost request) {
    Actor.current().require("ADMIN", "FINANCE", "PRODUCER");
    if (Actor.current().has("ADMIN", "FINANCE")) studio.projectAccess(request.projectId());
    else studio.projectManageAccess(request.projectId());
    if (request.amount().scale() > 2) throw BusinessException.bad("金额最多保留两位小数");
    var row = studio.data(request);
    db.insertCost(row);
    long id = id(row.get("id"));
    auth.audit("CREATE", "COST", id, request.description());
    return id;
  }

  @Transactional
  public void approveCost(long costId) {
    Actor.current().require("ADMIN", "REVIEWER");
    required(db.lockCost(costId), "成本");
    var row = required(db.cost(costId), "成本");
    status(row, "DRAFT");
    if (id(row.get("createdBy")) == Actor.current().id())
      throw BusinessException.conflict("成本录入人和审核人必须不同");
    var contract = db.matchingContract(id(row.get("projectId")), row.get("costDate"));
    if (contract == null) throw BusinessException.conflict("成本日期尚无生效分成合同");
    db.approveCost(costId, id(contract.get("id")), Actor.current().id());
    auth.audit("APPROVE", "COST", costId, "审核项目成本");
  }

  @Transactional
  public long createContract(Requests.Contract request) {
    Actor.current().require("ADMIN", "FINANCE");
    required(db.lockProject(request.projectId()), "项目");
    studio.projectAccess(request.projectId());
    if (request.endDate().isBefore(request.startDate()))
      throw BusinessException.bad("合同结束日期应不早于开始日期");
    if (request.studioRate().scale() > 4) throw BusinessException.bad("分成比例最多保留四位小数");
    BigDecimal total = request.studioRate();
    var creators = new HashSet<Long>();
    var members =
        db.projectMembers(request.projectId()).stream().map(x -> id(x.get("id"))).toList();
    for (var participant : request.participants()) {
      if (!creators.add(participant.creatorId())) throw BusinessException.bad("同一创作者只能填写一次");
      if (!members.contains(participant.creatorId())) throw BusinessException.bad("合同参与人必须是项目成员");
      if (participant.rate().scale() > 4) throw BusinessException.bad("分成比例最多保留四位小数");
      total = total.add(participant.rate());
    }
    if (total.compareTo(new BigDecimal("100")) != 0)
      throw BusinessException.bad("工作室与创作者分成比例合计必须为100%");
    var row = studio.data(request);
    db.insertContract(row);
    long id = id(row.get("id"));
    for (var participant : request.participants())
      db.addParticipant(id, participant.creatorId(), participant.rate());
    auth.audit("CREATE", "CONTRACT", id, request.name());
    return id;
  }

  @Transactional
  public void activateContract(long contractId) {
    Actor.current().require("ADMIN", "FINANCE");
    var row = required(db.lockContract(contractId), "合同");
    required(db.lockProject(id(row.get("projectId"))), "项目");
    row = required(db.contract(contractId), "合同");
    status(row, "DRAFT");
    var members =
        db.projectMembers(id(row.get("projectId"))).stream().map(x -> id(x.get("id"))).toList();
    for (var participant : db.contractParticipants(contractId))
      if (!members.contains(id(participant.get("creatorId"))))
        throw BusinessException.conflict("合同参与者必须在当前项目成员中");
    if (db.overlappingContracts(row) > 0) throw BusinessException.conflict("同项目已有生效合同覆盖该日期区间");
    db.activateContract(contractId);
    auth.audit("ACTIVATE", "CONTRACT", contractId, "激活合同并固定分成比例");
  }

  @Transactional
  public long generate(Requests.Settlement request) {
    Actor.current().require("ADMIN", "FINANCE");
    if (request.periodEnd().isBefore(request.periodStart()))
      throw BusinessException.bad("结算结束日期应不早于开始日期");
    var row = studio.data(request);
    db.generateSettlement(row);
    long id = id(row.get("id"));
    auth.audit("GENERATE", "SETTLEMENT", id, "生成结算快照");
    return id;
  }

  public Map<String, Object> settlement(long settlementId) {
    studio.financeRead();
    var row = required(db.settlement(settlementId), "结算单");
    var lines = db.settlementLines(settlementId);
    Long scope = studio.financeScope();
    if (scope != null && lines.stream().noneMatch(x -> id(x.get("creatorId")) == scope))
      throw new BusinessException(403, "仅可查看自己的结算单");
    row.put(
        "lines",
        lines.stream().filter(x -> scope == null || id(x.get("creatorId")) == scope).toList());
    row.put("revenues", db.settlementRevenues(settlementId));
    row.put("costs", db.settlementCosts(settlementId));
    row.put("history", db.settlementHistory(settlementId));
    var q = studio.financeAllQuery();
    var ids = lines.stream().map(x -> id(x.get("creatorId"))).toList();
    row.put(
        "payments",
        db.listPayments(q).stream().filter(x -> ids.contains(id(x.get("creatorId")))).toList());
    return row;
  }

  @Transactional
  public void submit(long settlementId) {
    Actor.current().require("ADMIN", "FINANCE");
    required(db.lockSettlement(settlementId), "结算单");
    status(required(db.settlement(settlementId), "结算单"), "DRAFT");
    db.submitSettlement(settlementId, Actor.current().id());
    auth.audit("SUBMIT", "SETTLEMENT", settlementId, "提交结算审核");
  }

  @Transactional
  public void approve(long settlementId) {
    Actor.current().require("ADMIN", "REVIEWER");
    required(db.lockSettlement(settlementId), "结算单");
    var row = required(db.settlement(settlementId), "结算单");
    status(row, "SUBMITTED");
    if (id(row.get("submittedBy")) == Actor.current().id())
      throw BusinessException.conflict("结算提交人与审核人必须不同");
    // 付款与结算审批共同锁定创作者，余额变更保持顺序一致。
    db.settlementLines(settlementId).stream()
        .map(x -> id(x.get("creatorId")))
        .sorted()
        .forEach(db::lockCreator);
    db.approveSettlement(settlementId, Actor.current().id());
    auth.audit("APPROVE", "SETTLEMENT", settlementId, "批准结算，计入创作者应付余额");
  }

  @Transactional
  public void reject(long settlementId, String reason) {
    Actor.current().require("ADMIN", "REVIEWER");
    required(db.lockSettlement(settlementId), "结算单");
    var row = required(db.settlement(settlementId), "结算单");
    status(row, "SUBMITTED");
    if (id(row.get("submittedBy")) == Actor.current().id())
      throw BusinessException.conflict("提交人与审核人必须不同");
    db.rejectSettlement(settlementId, Actor.current().id(), reason);
    auth.audit("REJECT", "SETTLEMENT", settlementId, reason);
  }

  @Transactional
  public long payment(Requests.Payment request) {
    Actor.current().require("ADMIN", "FINANCE");
    if (request.amount().scale() > 2) throw BusinessException.bad("金额最多保留两位小数");
    required(db.lockCreator(request.creatorId()), "创作者");
    BigDecimal balance = money(db.balance(request.creatorId()).get("balance"));
    if (request.amount().compareTo(balance) > 0)
      throw BusinessException.conflict("付款金额超过当前可付余额：" + balance);
    var row = studio.data(request);
    row.put("paidAt", studio.timestamp(request.paidAt()));
    db.insertPayment(row);
    long id = id(row.get("id"));
    auth.audit("PAYMENT", "PAYMENT", id, "登记线下付款 " + request.amount());
    return id;
  }
}
