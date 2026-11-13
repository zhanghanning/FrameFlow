package com.frameflow.service;

import static com.frameflow.common.Values.*;

import com.frameflow.common.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.dto.Requests;
import com.frameflow.mapper.StudioMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class FinanceService {
  private final StudioMapper db;
  private final StudioService studio;
  private final AuthService auth;
  private final ObjectMapper json;

  public FinanceService(StudioMapper db, StudioService studio, AuthService auth, ObjectMapper json) {
    this.db = db;
    this.studio = studio;
    this.auth = auth;
    this.json = json;
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

  @Transactional(isolation = Isolation.READ_COMMITTED)
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

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void editSettlement(long settlementId, Requests.EditSettlement request) {
    Actor.current().require("ADMIN", "FINANCE");
    if (request.periodEnd().isBefore(request.periodStart()))
      throw BusinessException.bad("结算结束日期应不早于开始日期");
    var current = lockDraft(settlementId, request.contractId());
    revise(settlementId, current, "EDIT", request.reason());
    calculate(settlementId, request.contractId(), request.periodStart(), request.periodEnd());
    auth.audit("EDIT_SETTLEMENT", "SETTLEMENT", settlementId, request.reason().trim());
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void recalculate(long settlementId, String reason) {
    Actor.current().require("ADMIN", "FINANCE");
    var current = lockDraft(settlementId, null);
    revise(settlementId, current, "RECALCULATE", reason);
    calculate(settlementId, id(current.get("contractId")),
        LocalDate.parse(current.get("periodStart").toString()), LocalDate.parse(current.get("periodEnd").toString()));
    auth.audit("RECALCULATE", "SETTLEMENT", settlementId, reason.trim());
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void cancel(long settlementId, String reason) {
    Actor.current().require("ADMIN", "FINANCE");
    var current = lockDraft(settlementId, null);
    revise(settlementId, current, "CANCEL", reason);
    db.releaseSettlementRevenues(settlementId);
    db.releaseSettlementCosts(settlementId);
    db.cancelSettlement(settlementId, Actor.current().id(), reason.trim());
    auth.audit("CANCEL_SETTLEMENT", "SETTLEMENT", settlementId, reason.trim());
  }

  // 合同按编号升序先锁，再锁结算；同合同生成、重算和撤销顺序执行。
  private Map<String, Object> lockDraft(long settlementId, Long targetContractId) {
    var previous = required(db.settlement(settlementId), "结算单");
    long originalContractId = id(previous.get("contractId"));
    var contracts = new TreeSet<Long>(); contracts.add(originalContractId);
    if (targetContractId != null) contracts.add(targetContractId);
    for (long contractId : contracts) required(db.lockContract(contractId), "合同");
    var locked = required(db.lockSettlement(settlementId), "结算单");
    if (id(locked.get("contractId")) != originalContractId)
      throw BusinessException.conflict("结算合同已被其他操作修改，请刷新后重试");
    status(locked, "DRAFT");
    return required(db.settlement(settlementId), "结算单");
  }

  private void calculate(long settlementId, long contractId, LocalDate start, LocalDate end) {
    db.calculateSettlement(Map.of("id", settlementId, "contractId", contractId,
        "periodStart", start, "periodEnd", end, "actorId", Actor.current().id()));
  }

  private void revise(long settlementId, Map<String, Object> current, String action, String reason) {
    var snapshot = new LinkedHashMap<>(current);
    snapshot.put("lines", db.settlementLines(settlementId));
    snapshot.put("revenues", db.settlementRevenues(settlementId));
    snapshot.put("costs", db.settlementCosts(settlementId));
    try {
      db.insertSettlementRevision(Map.of("settlementId", settlementId, "action", action,
          "reason", reason.trim(), "actorId", Actor.current().id(), "snapshotJson", json.writeValueAsString(snapshot)));
    } catch (JsonProcessingException ex) { throw new IllegalStateException("结算历史快照保存失败", ex); }
  }

  private List<Map<String, Object>> revisions(long settlementId, Long scope) {
    var revisions = db.settlementRevisions(settlementId);
    for (var revision : revisions) {
      try {
        Map<String, Object> snapshot = json.readerFor(new TypeReference<Map<String, Object>>() {})
            .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(revision.remove("snapshotJson").toString());
        var lines = (List<Map<String, Object>>) snapshot.get("lines");
        snapshot.put("lines", lines.stream().filter(line -> scope == null || id(line.get("creatorId")) == scope).toList());
        revision.put("snapshot", snapshot);
      } catch (JsonProcessingException ex) { throw new IllegalStateException("结算历史快照读取失败", ex); }
    }
    return revisions;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
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
    var revisions = revisions(settlementId, scope);
    row.put("revisions", revisions);
    if ("CANCELLED".equals(row.get("status"))) {
      var cancelled = revisions.stream().filter(revision -> "CANCEL".equals(revision.get("action"))).findFirst().orElseThrow();
      var snapshot = (Map<String, Object>) cancelled.get("snapshot");
      row.put("revenues", snapshot.get("revenues"));
      row.put("costs", snapshot.get("costs"));
    } else {
      row.put("revenues", db.settlementRevenues(settlementId));
      row.put("costs", db.settlementCosts(settlementId));
    }
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
