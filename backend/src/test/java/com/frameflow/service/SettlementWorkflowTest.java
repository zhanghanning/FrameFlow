package com.frameflow.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.common.*;
import com.frameflow.dto.Requests;
import com.frameflow.mapper.StudioMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class SettlementWorkflowTest {
  StudioMapper db;
  StudioService studio;
  AuthService auth;
  ObjectMapper json;
  FinanceService finance;
  Map<String, Object> draft;
  @BeforeEach void setup() {
    db = mock(StudioMapper.class); studio = mock(StudioService.class); auth = mock(AuthService.class);
    json = new ObjectMapper().findAndRegisterModules();
    finance = new FinanceService(db, studio, auth, json);
    Actor.set(new Actor(2, "finance", "财务", List.of("FINANCE"), null));
    draft = new LinkedHashMap<>(Map.of("id", 12L, "settlementNo", "JS-KEEP", "contractId", 9L, "projectId", 4L,
        "status", "DRAFT", "periodStart", LocalDate.of(2026, 10, 1), "periodEnd", LocalDate.of(2026, 10, 31),
        "totalIncome", new BigDecimal("100.00"), "totalCost", new BigDecimal("10.00")));
    when(db.settlement(12)).thenAnswer(invocation -> new LinkedHashMap<>(draft));
    when(db.lockContract(anyLong())).thenAnswer(invocation -> Map.of("id", invocation.getArgument(0)));
    when(db.lockSettlement(12)).thenAnswer(invocation -> Map.of("id", 12L, "contractId", draft.get("contractId"), "status", draft.get("status")));
    when(db.settlementLines(12)).thenReturn(List.of(Map.of("creatorId", 1L, "rate", new BigDecimal("70"), "amount", new BigDecimal("63.00"))));
    when(db.settlementRevenues(12)).thenReturn(List.of(Map.of("id", 7L, "sourceRef", "original", "netAmount", new BigDecimal("100.00"))));
    when(db.settlementCosts(12)).thenReturn(List.of(Map.of("id", 8L, "description", "成本", "amount", new BigDecimal("10.00"))));
  }
  @AfterEach void cleanup() { Actor.clear(); }

  @Test void editingLocksContractsInOrderAndSavesOriginalBasisBeforeRecalculation() throws Exception {
    finance.editSettlement(12, new Requests.EditSettlement(3L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "切换合同"));
    var order = inOrder(db);
    order.verify(db).settlement(12); order.verify(db).lockContract(3); order.verify(db).lockContract(9); order.verify(db).lockSettlement(12);
    var captured = ArgumentCaptor.forClass(Map.class);
    verify(db).insertSettlementRevision(captured.capture());
    var revision = captured.getValue(); var snapshot = json.readTree(revision.get("snapshotJson").toString());
    assertEquals("EDIT", revision.get("action")); assertEquals("切换合同", revision.get("reason"));
    assertEquals("JS-KEEP", snapshot.path("settlementNo").asText()); assertEquals(9, snapshot.path("contractId").asLong());
    assertEquals(new BigDecimal("100.0"), snapshot.path("totalIncome").decimalValue());
    assertEquals("original", snapshot.path("revenues").get(0).path("sourceRef").asText());
    assertEquals(8, snapshot.path("costs").get(0).path("id").asLong());
    order.verify(db).settlement(12); order.verify(db).settlementLines(12); order.verify(db).settlementRevenues(12); order.verify(db).settlementCosts(12);
    order.verify(db).insertSettlementRevision(any());
    order.verify(db).calculateSettlement(argThat(row -> Long.valueOf(12).equals(row.get("id")) && Long.valueOf(3).equals(row.get("contractId"))));
    verify(db, never()).generateSettlement(any());
  }
  @Test void recalculationUsesTheCurrentContractAndPeriod() {
    finance.recalculate(12, "纳入补录流水");
    verify(db).calculateSettlement(argThat(row -> Long.valueOf(9).equals(row.get("contractId"))
        && LocalDate.of(2026, 10, 1).equals(row.get("periodStart")) && LocalDate.of(2026, 10, 31).equals(row.get("periodEnd"))));
    verify(db).insertSettlementRevision(argThat(row -> "RECALCULATE".equals(row.get("action"))));
  }
  @ParameterizedTest @ValueSource(strings = {"SUBMITTED", "APPROVED", "CANCELLED"})
  void immutableStatusesRejectAllDraftMutations(String status) {
    draft.put("status", status);
    assertEquals(409, assertThrows(BusinessException.class, () -> finance.recalculate(12, "重算")).status());
    assertEquals(409, assertThrows(BusinessException.class, () -> finance.cancel(12, "撤销")).status());
    assertEquals(409, assertThrows(BusinessException.class, () -> finance.editSettlement(12,
        new Requests.EditSettlement(9L, LocalDate.of(2026,10,1), LocalDate.of(2026,10,31), "修改"))).status());
    verify(db, never()).insertSettlementRevision(any()); verify(db, never()).calculateSettlement(any());
    verify(db, never()).releaseSettlementRevenues(anyLong()); verify(db, never()).cancelSettlement(anyLong(), anyLong(), anyString());
  }
  @Test void cancellationSavesEvidenceBeforeReleasingAllocationsAndKeepsBalanceData() {
    finance.cancel(12, "取消重复期间");
    var order = inOrder(db);
    order.verify(db).insertSettlementRevision(argThat(row -> "CANCEL".equals(row.get("action"))));
    order.verify(db).releaseSettlementRevenues(12); order.verify(db).releaseSettlementCosts(12);
    order.verify(db).cancelSettlement(12, 2, "取消重复期间");
    verify(db, never()).calculateSettlement(any()); verify(db, never()).insertPayment(any()); verify(db, never()).approveSettlement(anyLong(), anyLong());
  }
  @Test void failedCalculationPropagatesForTransactionRollbackWithoutSuccessAudit() {
    doThrow(BusinessException.conflict("期间没有待结算来源")).when(db).calculateSettlement(any());
    assertEquals(409, assertThrows(BusinessException.class, () -> finance.recalculate(12, "重算")).status());
    verify(auth, never()).audit(anyString(), anyString(), anyLong(), anyString());
  }
  @Test void cancelledDetailUsesPreservedSourcesAndScopesHistoricalCreatorLines() throws Exception {
    draft.put("status", "CANCELLED");
    var original = new LinkedHashMap<>(draft); original.put("status", "DRAFT");
    original.put("lines", List.of(Map.of("creatorId", 1L, "amount", new BigDecimal("63.00")), Map.of("creatorId", 2L, "amount", new BigDecimal("18.00"))));
    original.put("revenues", List.of(Map.of("id", 7L, "sourceRef", "preserved"))); original.put("costs", List.of(Map.of("id", 8L)));
    when(db.settlementRevisions(12)).thenReturn(List.of(new HashMap<>(Map.of("id", 6L, "action", "CANCEL", "snapshotJson", json.writeValueAsString(original)))));
    when(studio.financeScope()).thenReturn(1L); when(studio.financeAllQuery()).thenReturn(Map.of("creatorId", 1L));
    var result = finance.settlement(12);
    var rows = (List<Map<String,Object>>)result.get("revisions");
    var snapshot = (Map<String,Object>)rows.getFirst().get("snapshot");
    var lines = (List<Map<String,Object>>)snapshot.get("lines");
    assertEquals(1, lines.size()); assertEquals(1, ((Number)lines.getFirst().get("creatorId")).intValue());
    assertEquals("preserved", ((List<Map<String,Object>>)result.get("revenues")).getFirst().get("sourceRef"));
    assertFalse(rows.getFirst().containsKey("snapshotJson"));
    verify(db, never()).settlementRevenues(12); verify(db, never()).settlementCosts(12);
  }
  @Test void creatorsCannotModifyAndConcurrentContractReplacementRequiresRefresh() {
    Actor.set(new Actor(5, "creator", "本人", List.of("CREATOR"), 1L));
    assertEquals(403, assertThrows(BusinessException.class, () -> finance.cancel(12, "撤销")).status());
    verify(db, never()).settlement(anyLong());
    Actor.set(new Actor(2, "finance", "财务", List.of("FINANCE"), null));
    when(db.lockSettlement(12)).thenReturn(Map.of("id", 12L, "contractId", 10L, "status", "DRAFT"));
    assertEquals(409, assertThrows(BusinessException.class, () -> finance.recalculate(12, "重算")).status());
    verify(db, never()).insertSettlementRevision(any());
  }
}
