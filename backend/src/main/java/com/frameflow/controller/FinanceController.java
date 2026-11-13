package com.frameflow.controller;

import com.frameflow.common.*;
import com.frameflow.dto.Requests;
import com.frameflow.service.*;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class FinanceController {
  private final StudioService studio;
  private final FinanceService finance;
  private final RevenueImportService imports;

  public FinanceController(
      StudioService studio, FinanceService finance, RevenueImportService imports) {
    this.studio = studio;
    this.finance = finance;
    this.imports = imports;
  }

  @GetMapping("/revenues")
  public Api<?> revenues(@RequestParam Map<String, String> query) {
    return Api.ok(studio.revenues(query));
  }

  @PostMapping("/revenues")
  public Api<?> revenue(@Valid @RequestBody Requests.Revenue request) {
    return Api.ok(Map.of("id", finance.createRevenue(request)));
  }

  @PostMapping("/revenues/{id}/confirm")
  public Api<?> confirm(@PathVariable long id) {
    finance.confirmRevenue(id);
    return Api.ok(null);
  }

  @PostMapping("/revenues/import/preview")
  public Api<?> preview(@RequestPart("file") MultipartFile file) throws IOException {
    return Api.ok(imports.preview(file));
  }

  @PostMapping("/revenues/import")
  public Api<?> importRows(@Valid @RequestBody Requests.ImportRows request) {
    return Api.ok(imports.importRows(request));
  }

  @GetMapping("/revenues/export")
  public ResponseEntity<byte[]> export(@RequestParam Map<String, String> query) throws IOException {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=frameflow-revenues.csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(imports.export(query));
  }

  @GetMapping("/costs")
  public Api<?> costs(@RequestParam Map<String, String> query) {
    return Api.ok(studio.costs(query));
  }

  @PostMapping("/costs")
  public Api<?> cost(@Valid @RequestBody Requests.Cost request) {
    return Api.ok(Map.of("id", finance.createCost(request)));
  }

  @PostMapping("/costs/{id}/approve")
  public Api<?> approveCost(@PathVariable long id) {
    finance.approveCost(id);
    return Api.ok(null);
  }

  @GetMapping("/contracts")
  public Api<?> contracts(@RequestParam Map<String, String> query) {
    return Api.ok(studio.contracts(query));
  }

  @PostMapping("/contracts")
  public Api<?> contract(@Valid @RequestBody Requests.Contract request) {
    return Api.ok(Map.of("id", finance.createContract(request)));
  }

  @PostMapping("/contracts/{id}/activate")
  public Api<?> activate(@PathVariable long id) {
    finance.activateContract(id);
    return Api.ok(null);
  }

  @GetMapping("/settlements")
  public Api<?> settlements(@RequestParam Map<String, String> query) {
    return Api.ok(studio.settlements(query));
  }

  @GetMapping("/settlements/{id}")
  public Api<?> settlement(@PathVariable long id) {
    return Api.ok(finance.settlement(id));
  }

  @PostMapping("/settlements")
  public Api<?> generate(@Valid @RequestBody Requests.Settlement request) {
    return Api.ok(Map.of("id", finance.generate(request)));
  }

  @PutMapping("/settlements/{id}")
  public Api<?> editSettlement(@PathVariable long id, @Valid @RequestBody Requests.EditSettlement request) {
    finance.editSettlement(id, request);
    return Api.ok(Map.of("id", id));
  }

  @PostMapping("/settlements/{id}/recalculate")
  public Api<?> recalculate(@PathVariable long id, @Valid @RequestBody Requests.SettlementReason request) {
    finance.recalculate(id, request.reason());
    return Api.ok(Map.of("id", id));
  }

  @PostMapping("/settlements/{id}/cancel")
  public Api<?> cancel(@PathVariable long id, @Valid @RequestBody Requests.SettlementReason request) {
    finance.cancel(id, request.reason());
    return Api.ok(Map.of("id", id));
  }

  @PostMapping("/settlements/{id}/submit")
  public Api<?> submit(@PathVariable long id) {
    finance.submit(id);
    return Api.ok(null);
  }

  @PostMapping("/settlements/{id}/approve")
  public Api<?> approve(@PathVariable long id) {
    finance.approve(id);
    return Api.ok(null);
  }

  @PostMapping("/settlements/{id}/reject")
  public Api<?> reject(@PathVariable long id, @Valid @RequestBody Requests.Reject request) {
    finance.reject(id, request.reason());
    return Api.ok(null);
  }

  @GetMapping("/balances")
  public Api<?> balances() {
    return Api.ok(studio.balances());
  }

  @GetMapping("/payments")
  public Api<?> payments(@RequestParam Map<String, String> query) {
    return Api.ok(studio.payments(query));
  }

  @PostMapping("/payments")
  public Api<?> pay(@Valid @RequestBody Requests.Payment request) {
    return Api.ok(Map.of("id", finance.payment(request)));
  }
}
