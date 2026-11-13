package com.frameflow.service;

import static com.frameflow.common.Values.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.common.*;
import com.frameflow.dto.Requests;
import com.frameflow.mapper.StudioMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudioService {
  private final StudioMapper db;
  private final AuthService auth;
  private final ObjectMapper json;

  public StudioService(StudioMapper db, AuthService auth, ObjectMapper json) {
    this.db = db;
    this.auth = auth;
    this.json = json;
  }

  public Map<String, Object> data(Object request) {
    var map = json.convertValue(request, new TypeReference<Map<String, Object>>() {});
    map.put("actorId", Actor.current().id());
    return map;
  }

  private boolean fullProductionRead() {
    return Actor.current().has("ADMIN", "FINANCE", "REVIEWER");
  }

  public Long productionReadScope() {
    return !fullProductionRead() && Actor.current().has("CREATOR")
        ? Actor.current().creatorId() : null;
  }

  public Long financeScope() {
    return Actor.current().has("ADMIN", "FINANCE", "REVIEWER")
        ? null : Actor.current().creatorId();
  }

  private boolean ownsCreator(long creatorId) {
    return Actor.current().has("CREATOR") && Objects.equals(Actor.current().creatorId(), creatorId);
  }

  private boolean coordinates(Map<String, Object> project) {
    return Actor.current().has("PRODUCER") && id(project.get("ownerId")) == Actor.current().id();
  }

  private boolean projectMember(long projectId) {
    return Actor.current().has("CREATOR")
        && db.projectMembers(projectId).stream()
            .anyMatch(member -> Objects.equals(id(member.get("id")), Actor.current().creatorId()));
  }

  public void financeRead() {
    Actor.current().require("ADMIN", "FINANCE", "REVIEWER", "CREATOR");
  }

  public void productionWrite() {
    Actor.current().require("ADMIN", "PRODUCER", "CREATOR");
  }

  public Map<String, Object> query(Map<String, String> input) {
    var q = allQuery();
    for (String key : List.of("q", "status", "projectId"))
      if (input.get(key) != null && !input.get(key).isBlank()) q.put(key, input.get(key));
    int page = parsePage(input.getOrDefault("page", "1")),
        size = Math.min(parsePage(input.getOrDefault("pageSize", "20")), 100);
    q.put("page", page);
    q.put("pageSize", size);
    q.put("limit", size);
    q.put("offset", (page - 1) * size);
    return q;
  }

  private int parsePage(String value) {
    try {
      int page = Integer.parseInt(value);
      if (page < 1) throw new NumberFormatException();
      return page;
    } catch (NumberFormatException ex) {
      throw BusinessException.bad("页码和每页数量须为正整数");
    }
  }

  public Map<String, Object> allQuery() {
    var q = new HashMap<String, Object>();
    q.put("creatorId", productionReadScope());
    q.put("productionOwnerId", !fullProductionRead() && Actor.current().has("PRODUCER")
        ? Actor.current().id() : null);
    return q;
  }

  public Map<String, Object> financeQuery(Map<String, String> input) {
    var q = query(input);
    q.put("creatorId", financeScope());
    return q;
  }

  public Map<String, Object> financeAllQuery() {
    var q = allQuery();
    q.put("creatorId", financeScope());
    return q;
  }

  private Map<String, Object> costScope(Map<String, Object> q) {
    q.put("creatorId", null);
    if (!Actor.current().has("ADMIN", "FINANCE", "REVIEWER")) {
      if (Actor.current().has("CREATOR")) q.put("creatorId", Actor.current().creatorId());
      if (Actor.current().has("PRODUCER")) q.put("costActorId", Actor.current().id());
    }
    return q;
  }

  private Map<String, Object> page(
      Map<String, String> input,
      Function<Map<String, Object>, List<Map<String, Object>>> list,
      ToLongFunction<Map<String, Object>> count) {
    return pageResult(query(input), list, count);
  }

  private Map<String, Object> pageResult(
      Map<String, Object> q,
      Function<Map<String, Object>, List<Map<String, Object>>> list,
      ToLongFunction<Map<String, Object>> count) {
    return Map.of(
        "items",
        list.apply(q),
        "total",
        count.applyAsLong(q),
        "page",
        q.get("page"),
        "pageSize",
        q.get("pageSize"));
  }

  private Map<String, Object> creatorDirectory(Map<String, Object> q) {
    if (Actor.current().has("PRODUCER")) q.put("creatorId", null);
    return q;
  }

  private List<Map<String, Object>> creatorDirectoryRows(List<Map<String, Object>> rows) {
    if (Actor.current().has("PRODUCER") && !fullProductionRead()) {
      var fields = Set.of("id", "name", "handle", "status");
      for (var row : rows)
        if (!ownsCreator(id(row.get("id")))) row.keySet().retainAll(fields);
    }
    return rows;
  }

  public Map<String, Object> creators(Map<String, String> q) {
    return pageResult(creatorDirectory(query(q)),
        query -> creatorDirectoryRows(db.listCreators(query)), db::countCreators);
  }

  public Map<String, Object> accounts(Map<String, String> q) {
    return page(q, db::listAccounts, db::countAccounts);
  }

  public Map<String, Object> projects(Map<String, String> q) {
    var result = page(q, db::listProjects, db::countProjects);
    for (var row : (List<Map<String, Object>>) result.get("items"))
      row.put(
          "creatorIds",
          db.projectMembers(id(row.get("id"))).stream().map(x -> x.get("id")).toList());
    return result;
  }

  public Map<String, Object> works(Map<String, String> q) {
    return page(q, db::listWorks, db::countWorks);
  }

  public Map<String, Object> assets(Map<String, String> q) {
    var result = page(q, db::listAssets, db::countAssets);
    sharedProjects((List<Map<String, Object>>) result.get("items"));
    return result;
  }

  private List<Map<String, Object>> sharedProjects(List<Map<String, Object>> assets) {
    for (var asset : assets) asset.put("sharedProjectIds", db.assetProjects(id(asset.get("id"))));
    return assets;
  }

  public Map<String, Object> publications(Map<String, String> q) {
    return page(q, db::listPublications, db::countPublications);
  }

  public Map<String, Object> revenues(Map<String, String> q) {
    financeRead();
    return pageResult(financeQuery(q), db::listRevenues, db::countRevenues);
  }

  public Map<String, Object> costs(Map<String, String> q) {
    Actor.current().require("ADMIN", "FINANCE", "REVIEWER", "CREATOR", "PRODUCER");
    return pageResult(costScope(query(q)), db::listCosts, db::countCosts);
  }

  public Map<String, Object> contracts(Map<String, String> q) {
    financeRead();
    var result = pageResult(financeQuery(q), db::listContracts, db::countContracts);
    for (var row : (List<Map<String, Object>>) result.get("items"))
      row.put("participants", participants(id(row.get("id"))));
    return result;
  }

  public Map<String, Object> settlements(Map<String, String> q) {
    financeRead();
    return pageResult(financeQuery(q), db::listSettlements, db::countSettlements);
  }

  public Map<String, Object> payments(Map<String, String> q) {
    financeRead();
    return pageResult(financeQuery(q), db::listPayments, db::countPayments);
  }

  public Map<String, Object> audits(Map<String, String> q) {
    Actor.current().require("ADMIN", "REVIEWER");
    return page(q, db::listAuditLogs, db::countAuditLogs);
  }

  public List<Map<String, Object>> balances() {
    financeRead();
    return db.balances(financeScope());
  }

  public void creatorWriteAccess(long creatorId) {
    Actor.current().require("ADMIN", "CREATOR");
    if (!Actor.current().has("ADMIN") && !ownsCreator(creatorId))
      throw new BusinessException(403, "仅可修改本人的创作者资料、素材与平台账号");
  }

  public Map<String, Object> projectManageAccess(long projectId) {
    var row = required(db.project(projectId), "项目");
    if (!Actor.current().has("ADMIN") && !coordinates(row))
      throw new BusinessException(403, "仅项目负责人或管理员可管理项目");
    return row;
  }

  private Map<String, Object> projectWriteAccess(long projectId) {
    var row = required(db.project(projectId), "项目");
    if (!Actor.current().has("ADMIN") && !coordinates(row) && !projectMember(projectId))
      throw new BusinessException(403, "仅可在自己负责或参与的项目中创作");
    return row;
  }

  private Map<String, Object> workWriteAccess(long workId) {
    var row = required(db.work(workId), "作品");
    projectWriteAccess(id(row.get("projectId")));
    return row;
  }

  private Map<String, Object> assetReferenceAccess(long assetId, long projectId) {
    required(db.lockAsset(assetId), "素材");
    var row = required(db.asset(assetId), "素材");
    boolean shared = db.assetProjects(assetId).contains(projectId);
    if (!shared && ownsCreator(id(row.get("creatorId")))) {
      var project = required(db.project(projectId), "项目");
      if (!Actor.current().has("ADMIN") && !coordinates(project) && !projectMember(projectId))
        throw new BusinessException(403, "仅可向本人负责或参与的项目授权素材");
      db.shareAsset(projectId, assetId, Actor.current().id());
      shared = true;
    }
    if (!shared) throw new BusinessException(403, "素材尚未授权给当前项目，请先取得项目授权");
    return row;
  }

  public Map<String, Object> projectAccess(long projectId) {
    var row = required(db.project(projectId), "项目");
    if (!fullProductionRead() && !coordinates(row) && !projectMember(projectId))
      throw new BusinessException(403, "仅可访问自己负责或参与的项目");
    return row;
  }

  public Map<String, Object> assetAccess(long assetId) {
    var row = required(db.asset(assetId), "素材");
    var q = allQuery();
    q.put("assetId", assetId);
    if (db.countAssets(q) == 0)
      throw new BusinessException(403, "仅可读取本人素材或负责、参与项目已获授权的素材");
    row.put("sharedProjectIds", db.assetProjects(assetId));
    return row;
  }

  public Map<String, Object> assetWriteAccess(long assetId) {
    var row = required(db.asset(assetId), "素材");
    creatorWriteAccess(id(row.get("creatorId")));
    return row;
  }

  @Transactional
  public void shareAsset(long assetId, List<Long> projectIds) {
    if (projectIds == null || projectIds.stream().anyMatch(Objects::isNull))
      throw BusinessException.bad("请提交授权项目列表");
    required(db.lockAsset(assetId), "素材");
    assetWriteAccess(assetId);
    var requested = new LinkedHashSet<>(projectIds);
    var existing = db.assetProjects(assetId);
    for (long projectId : requested) {
      var project = required(db.project(projectId), "项目");
      if (!existing.contains(projectId) && !Actor.current().has("ADMIN")
          && !coordinates(project) && !projectMember(projectId))
        throw new BusinessException(403, "仅可向本人负责或参与的项目授权素材");
    }
    for (long projectId : existing) {
      if (!requested.contains(projectId)) {
        if (db.assetProjectReferences(assetId, projectId) > 0)
          throw BusinessException.conflict("项目中的作品已引用该素材，请先移除引用再撤回授权");
        db.removeAssetProject(assetId, projectId);
      }
    }
    for (long projectId : requested)
      if (!existing.contains(projectId)) db.shareAsset(projectId, assetId, Actor.current().id());
    auth.audit("UPDATE", "ASSET", assetId, "调整素材项目授权");
  }

  public Map<String, Object> workAccess(long workId) {
    var row = required(db.work(workId), "作品");
    projectAccess(id(row.get("projectId")));
    return row;
  }

  public Map<String, Object> publicationAccess(long publicationId) {
    var row = required(db.publication(publicationId), "发布记录");
    projectAccess(id(row.get("projectId")));
    return row;
  }

  public Map<String, Object> project(long projectId) {
    var row = projectAccess(projectId);
    var q = allQuery();
    q.put("projectId", projectId);
    row.put("members", db.projectMembers(projectId));
    row.put("creatorIds", db.projectMembers(projectId).stream().map(x -> x.get("id")).toList());
    row.put("works", db.listWorks(q));
    boolean finance = Actor.current().has("ADMIN", "FINANCE", "REVIEWER", "CREATOR");
    var costQuery = costScope(allQuery());
    costQuery.put("projectId", projectId);
    row.put("costs", db.listCosts(costQuery));
    var contractQuery = financeAllQuery();
    contractQuery.put("projectId", projectId);
    row.put("contracts", finance ? db.listContracts(contractQuery) : List.of());
    return row;
  }

  public Map<String, Object> work(long workId) {
    var row = workAccess(workId);
    row.put("assets", sharedProjects(db.workAssets(workId)));
    row.put("assetIds", db.workAssets(workId).stream().map(x -> x.get("id")).toList());
    return row;
  }

  public List<Map<String, Object>> participants(long contractId) {
    return db.contractParticipants(contractId).stream()
        .filter(x -> financeScope() == null || id(x.get("creatorId")) == financeScope())
        .toList();
  }

  public Map<String, Object> options() {
    var q = allQuery();
    var map = new LinkedHashMap<String, Object>();
    map.put("creators", creatorDirectoryRows(db.listCreators(creatorDirectory(allQuery()))));
    var projects = db.listProjects(q);
    for (var project : projects)
      project.put(
          "creatorIds",
          db.projectMembers(id(project.get("id"))).stream().map(member -> member.get("id")).toList());
    map.put("projects", projects);
    map.put("works", db.listWorks(q));
    map.put("assets", sharedProjects(db.listAssets(q)));
    map.put("platformAccounts", db.listAccounts(q));
    map.put("platforms", db.platforms());
    map.put("users", db.userOptions());
    map.put(
        "contracts",
        Actor.current().has("ADMIN", "FINANCE", "REVIEWER", "CREATOR")
            ? db.listContracts(financeAllQuery())
            : List.of());
    return map;
  }

  public Map<String, Object> dashboard() {
    var q = allQuery();
    var projectRows = db.listProjects(q);
    var assetRows = sharedProjects(db.listAssets(q));
    boolean finance = Actor.current().has("ADMIN", "FINANCE", "REVIEWER", "CREATOR");
    var metrics = finance ? db.dashboardRevenue(financeScope()) : new HashMap<String, Object>();
    metrics.putIfAbsent("revenue", BigDecimal.ZERO);
    metrics.putIfAbsent("unsettled", BigDecimal.ZERO);
    metrics.put(
        "activeProjects",
        projectRows.stream()
            .filter(x -> !List.of("COMPLETED", "ARCHIVED").contains(x.get("status")))
            .count());
    metrics.put("assetCount", assetRows.size());
    var result = new LinkedHashMap<String, Object>();
    result.put("metrics", metrics);
    result.put("trend", finance ? db.dashboardTrend(financeScope()) : List.of());
    result.put("platforms", finance ? db.dashboardPlatforms(financeScope()) : List.of());
    result.put("recentProjects", projectRows.stream().limit(5).toList());
    var financeQuery = financeAllQuery();
    result.put(
        "recentRevenues", finance ? db.listRevenues(financeQuery).stream().limit(5).toList() : List.of());
    result.put(
        "expiringAssets",
        assetRows.stream()
            .filter(
                x ->
                    x.get("licenseEnd") != null
                        && !LocalDate.parse(x.get("licenseEnd").toString())
                            .isAfter(LocalDate.now().plusDays(30)))
            .limit(6)
            .toList());
    financeQuery.put("status", "SUBMITTED");
    result.put(
        "pendingSettlements",
        finance ? db.listSettlements(financeQuery).stream().limit(5).toList() : List.of());
    return result;
  }

  @Transactional
  public long saveCreator(Long key, Requests.Creator request) {
    if (key == null) Actor.current().require("ADMIN");
    else creatorWriteAccess(key);
    oneOf(request.status(), "ACTIVE", "INACTIVE");
    var row = data(request);
    if (key == null) db.insertCreator(row);
    else {
      required(db.creator(key), "创作者");
      row.put("id", key);
      db.updateCreator(row);
    }
    long id = id(row.get("id"));
    auth.audit(key == null ? "CREATE" : "UPDATE", "CREATOR", id, request.name());
    return id;
  }

  @Transactional
  public long saveAccount(Long key, Requests.Account request) {
    creatorWriteAccess(request.creatorId());
    oneOf(request.status(), "ACTIVE", "INACTIVE");
    required(db.creator(request.creatorId()), "创作者");
    var row = data(request);
    if (key == null) db.insertAccount(row);
    else {
      var old = required(db.account(key), "平台账号");
      creatorWriteAccess(id(old.get("creatorId")));
      if (db.accountPublicationCount(key) > 0
          && (id(old.get("creatorId")) != request.creatorId()
              || id(old.get("platformId")) != request.platformId()
              || !Objects.equals(old.get("externalId"), request.externalId())))
        throw BusinessException.conflict("已有发布记录的账号归属、平台和外部账号ID保持固定");
      row.put("id", key);
      db.updateAccount(row);
    }
    long id = id(row.get("id"));
    auth.audit(key == null ? "CREATE" : "UPDATE", "PLATFORM_ACCOUNT", id, request.name());
    return id;
  }

  @Transactional
  public long saveProject(Long key, Requests.Project request) {
    Actor.current().require("ADMIN", "PRODUCER");
    oneOf(request.status(), "PLANNING", "PRODUCING", "REVIEW", "COMPLETED", "ARCHIVED");
    if (request.dueDate().isBefore(request.startDate()))
      throw BusinessException.bad("截止日期应不早于开始日期");
    var row = data(request);
    long ownerId = Actor.current().has("ADMIN") ? request.ownerId() : Actor.current().id();
    if (db.projectOwnerEligible(ownerId) == 0)
      throw BusinessException.bad("项目负责人须为启用的管理员或项目统筹");
    row.put("ownerId", ownerId);
    if (key == null) db.insertProject(row);
    else {
      required(db.lockProject(key), "项目");
      var old = projectManageAccess(key);
      if (!Actor.current().has("ADMIN") && request.ownerId() != id(old.get("ownerId")))
        throw new BusinessException(403, "仅管理员可调整项目负责人");
      var contractQuery = new HashMap<String, Object>();
      contractQuery.put("projectId", key);
      contractQuery.put("status", "ACTIVE");
      var members = new HashSet<>(request.creatorIds());
      for (var contract : db.listContracts(contractQuery))
        for (var participant : db.contractParticipants(id(contract.get("id"))))
          if (!members.contains(id(participant.get("creatorId"))))
            throw BusinessException.conflict("生效分成合同的参与者须保留在项目成员中");
      row.put("id", key);
      db.updateProject(row);
      db.deleteMembers(key);
    }
    long id = id(row.get("id"));
    for (long creatorId : new LinkedHashSet<>(request.creatorIds())) db.addMember(id, creatorId);
    auth.audit(key == null ? "CREATE" : "UPDATE", "PROJECT", id, request.name());
    return id;
  }

  @Transactional
  public long saveWork(Long key, Requests.Work request) {
    productionWrite();
    required(db.lockProject(request.projectId()), "项目");
    projectWriteAccess(request.projectId());
    oneOf(request.status(), "DRAFT", "EDITING", "REVIEW", "READY");
    var row = data(request);
    for (long assetId : request.assetIds().stream().distinct().sorted().toList()) {
      var asset = assetReferenceAccess(assetId, request.projectId());
      if (!"ACTIVE".equals(asset.get("status"))) throw BusinessException.bad("作品只能引用有效素材");
    }
    if (key == null) db.insertWork(row);
    else {
      workWriteAccess(key);
      if (db.workPublicationCount(key) > 0)
        throw BusinessException.conflict("已发布作品的内容与素材引用已固定，请新建作品");
      row.put("id", key);
      db.updateWork(row);
      db.deleteWorkAssets(key);
    }
    long id = id(row.get("id"));
    for (long assetId : new LinkedHashSet<>(request.assetIds())) db.addWorkAsset(id, assetId);
    auth.audit(key == null ? "CREATE" : "UPDATE", "WORK", id, request.title());
    return id;
  }

  @Transactional
  public long publish(Requests.Publication request) {
    productionWrite();
    var work = workWriteAccess(request.workId());
    status(work, "READY");
    var account = required(db.account(request.platformAccountId()), "平台账号");
    var project = required(db.project(id(work.get("projectId"))), "项目");
    if (!Actor.current().has("ADMIN") && !coordinates(project)
        && !ownsCreator(id(account.get("creatorId"))))
      throw new BusinessException(403, "创作者仅可使用本人的平台账号发布作品");
    status(account, "ACTIVE");
    if (db.projectMembers(id(work.get("projectId"))).stream()
        .noneMatch(x -> id(x.get("id")) == id(account.get("creatorId"))))
      throw BusinessException.bad("发布账号的创作者必须参与当前项目");
    LocalDate date;
    try {
      date = LocalDate.parse(request.publishedAt().substring(0, 10));
    } catch (Exception ex) {
      throw BusinessException.bad("发布时间格式错误");
    }
    var assets = db.workAssets(request.workId());
    if (assets.isEmpty()) throw BusinessException.bad("作品至少需要引用一项素材");
    for (var asset : assets) {
      if (!"ACTIVE".equals(asset.get("status")))
        throw BusinessException.conflict("引用素材已归档：" + asset.get("name"));
      if (asset.get("licenseStart") != null
          && date.isBefore(LocalDate.parse(asset.get("licenseStart").toString())))
        throw BusinessException.conflict("素材授权尚未生效：" + asset.get("name"));
      if (asset.get("licenseEnd") != null
          && date.isAfter(LocalDate.parse(asset.get("licenseEnd").toString())))
        throw BusinessException.conflict("素材授权已到期：" + asset.get("name"));
      var platforms = Arrays.asList(asset.get("allowedPlatforms").toString().split(","));
      if (!platforms.contains("ALL") && !platforms.contains(account.get("platformCode")))
        throw BusinessException.conflict("素材授权未涵盖发布平台：" + asset.get("name"));
    }
    var row = data(request);
    row.put("publishedAt", timestamp(request.publishedAt()));
    db.insertPublication(row);
    long id = id(row.get("id"));
    auth.audit("PUBLISH", "PUBLICATION", id, "发布作品 " + work.get("title"));
    return id;
  }

  public String timestamp(String value) {
    try {
      String text = value.replace('T', ' ');
      if (text.length() == 10) text += " 00:00:00";
      if (text.length() == 16) text += ":00";
      LocalDateTime.parse(text.replace(' ', 'T'));
      return text;
    } catch (Exception ex) {
      throw BusinessException.bad("时间格式应为yyyy-MM-dd HH:mm:ss");
    }
  }
}
