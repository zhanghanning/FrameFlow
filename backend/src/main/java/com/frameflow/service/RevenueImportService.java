package com.frameflow.service;

import static com.frameflow.common.Values.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.common.*;
import com.frameflow.dto.Requests;
import com.frameflow.mapper.StudioMapper;
import jakarta.validation.Validator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RevenueImportService {
  private final StudioMapper db;
  private final StudioService studio;
  private final FinanceService finance;
  private final AuthService auth;
  private final ObjectMapper json;
  private final Validator validator;

  public RevenueImportService(
      StudioMapper db,
      StudioService studio,
      FinanceService finance,
      AuthService auth,
      ObjectMapper json,
      Validator validator) {
    this.db = db;
    this.studio = studio;
    this.finance = finance;
    this.auth = auth;
    this.json = json;
    this.validator = validator;
  }

  private Requests.Revenue request(Map<String, Object> row) {
    var fields = new HashMap<String, Object>();
    for (String key :
        List.of("publicationId", "sourceRef", "revenueDate", "grossAmount", "feeAmount", "note"))
      fields.put(key, row.get(key));
    fields.put("kind", "INCOME");
    Requests.Revenue request;
    try {
      request = json.convertValue(fields, Requests.Revenue.class);
    } catch (IllegalArgumentException ex) {
      throw BusinessException.bad("日期或金额格式错误");
    }
    var errors = validator.validate(request);
    if (!errors.isEmpty()) throw BusinessException.bad(errors.iterator().next().getMessage());
    return request;
  }

  public Map<String, Object> preview(MultipartFile file) throws IOException {
    Actor.current().require("ADMIN", "FINANCE");
    String content = new String(file.getBytes(), StandardCharsets.UTF_8);
    if (content.startsWith("\uFEFF")) content = content.substring(1);
    var format =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .get();
    var rows = new ArrayList<Map<String, Object>>();
    int valid = 0, invalid = 0, duplicates = 0;
    var seen = new HashSet<String>();
    try (var parser = CSVParser.parse(content, format)) {
      if (!parser
          .getHeaderMap()
          .keySet()
          .containsAll(
              List.of(
                  "publicationId", "sourceRef", "revenueDate", "grossAmount", "feeAmount", "note")))
        throw BusinessException.bad(
            "CSV表头应为 publicationId,sourceRef,revenueDate,grossAmount,feeAmount,note");
      for (var record : parser) {
        var row = new LinkedHashMap<String, Object>();
        row.put("rowNumber", record.getRecordNumber() + 1);
        for (String key :
            List.of(
                "publicationId", "sourceRef", "revenueDate", "grossAmount", "feeAmount", "note"))
          row.put(key, record.isSet(key) ? record.get(key) : "");
        try {
          var request = request(row);
          var data = finance.validateRevenue(request);
          String key = data.get("platformAccountId") + ":" + request.sourceRef();
          if (db.duplicateRevenue(id(data.get("platformAccountId")), request.sourceRef(), "INCOME")
                  != null
              || !seen.add(key)) {
            row.put("valid", false);
            row.put("duplicate", true);
            row.put("error", "平台账号流水号重复");
            duplicates++;
            invalid++;
          } else {
            row.put("valid", true);
            row.put("error", "");
            valid++;
          }
        } catch (BusinessException ex) {
          row.put("valid", false);
          row.put("error", ex.getMessage());
          invalid++;
        }
        rows.add(row);
      }
    } catch (IllegalArgumentException ex) {
      throw BusinessException.bad("CSV列数或格式不正确");
    }
    return Map.of(
        "rows", rows, "validCount", valid, "invalidCount", invalid, "duplicateCount", duplicates);
  }

  @Transactional
  public Map<String, Object> importRows(Requests.ImportRows request) {
    Actor.current().require("ADMIN", "FINANCE");
    var inserts = new ArrayList<Map<String, Object>>();
    int duplicates = 0;
    var seen = new HashSet<String>();
    for (var input : request.rows()) {
      var row = finance.validateRevenue(request(input));
      String key = row.get("platformAccountId") + ":" + row.get("sourceRef");
      if (db.duplicateRevenue(
                  id(row.get("platformAccountId")), row.get("sourceRef").toString(), "INCOME")
              != null
          || !seen.add(key)) {
        duplicates++;
        continue;
      }
      inserts.add(row);
    }
    if (!inserts.isEmpty()) {
      var batch = new HashMap<String, Object>();
      batch.put("filename", "CSV账单导入");
      batch.put("totalRows", request.rows().size());
      batch.put("insertedRows", inserts.size());
      batch.put("actorId", Actor.current().id());
      db.insertImportBatch(batch);
      for (var row : inserts) {
        row.put("importBatchId", batch.get("id"));
        db.insertRevenue(row);
      }
      auth.audit("IMPORT", "REVENUE", id(batch.get("id")), "导入收入 " + inserts.size() + " 条");
    }
    return Map.of("inserted", inserts.size(), "duplicates", duplicates);
  }

  public byte[] export(Map<String, String> filters) throws IOException {
    studio.financeRead();
    var query = studio.financeQuery(filters);
    query.remove("limit");
    query.remove("offset");
    var output = new StringWriter();
    output.write("\uFEFF");
    try (var printer = new CSVPrinter(output, CSVFormat.DEFAULT)) {
      printer.printRecord(
          "流水ID", "平台流水号", "项目", "作品", "创作者", "收入日期", "类型", "收入金额", "平台费用", "净收入", "状态", "结算单ID");
      for (var row : db.listRevenues(query))
        printer.printRecord(
            row.get("id"),
            row.get("sourceRef"),
            row.get("projectName"),
            row.get("workTitle"),
            row.get("creatorName"),
            row.get("revenueDate"),
            row.get("kind"),
            row.get("grossAmount"),
            row.get("feeAmount"),
            row.get("netAmount"),
            row.get("status"),
            row.get("settlementId"));
    }
    return output.toString().getBytes(StandardCharsets.UTF_8);
  }
}
