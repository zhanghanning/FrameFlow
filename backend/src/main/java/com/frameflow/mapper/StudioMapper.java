package com.frameflow.mapper;

import java.util.*;
import org.apache.ibatis.annotations.Param;

public interface StudioMapper {
  List<Map<String, Object>> listCreators(Map<String, Object> query);

  long countCreators(Map<String, Object> query);

  List<Map<String, Object>> listAccounts(Map<String, Object> query);

  long countAccounts(Map<String, Object> query);

  List<Map<String, Object>> listProjects(Map<String, Object> query);

  long countProjects(Map<String, Object> query);

  List<Map<String, Object>> listWorks(Map<String, Object> query);

  long countWorks(Map<String, Object> query);

  List<Map<String, Object>> listAssets(Map<String, Object> query);

  long countAssets(Map<String, Object> query);

  List<Map<String, Object>> listPublications(Map<String, Object> query);

  long countPublications(Map<String, Object> query);

  List<Map<String, Object>> listRevenues(Map<String, Object> query);

  long countRevenues(Map<String, Object> query);

  List<Map<String, Object>> listCosts(Map<String, Object> query);

  long countCosts(Map<String, Object> query);

  List<Map<String, Object>> listContracts(Map<String, Object> query);

  long countContracts(Map<String, Object> query);

  List<Map<String, Object>> listSettlements(Map<String, Object> query);

  long countSettlements(Map<String, Object> query);

  List<Map<String, Object>> listPayments(Map<String, Object> query);

  long countPayments(Map<String, Object> query);

  List<Map<String, Object>> listAuditLogs(Map<String, Object> query);

  long countAuditLogs(Map<String, Object> query);

  List<Long> assetProjects(long assetId);

  Map<String, Object> lockAsset(long id);

  long assetProjectReferences(@Param("assetId") long assetId, @Param("projectId") long projectId);

  void shareAsset(@Param("projectId") long projectId, @Param("assetId") long assetId,
      @Param("actorId") long actorId);

  void removeAssetProject(@Param("assetId") long assetId, @Param("projectId") long projectId);

  long projectOwnerEligible(long userId);

  long workPublicationCount(long id);

  long accountPublicationCount(long id);

  long accountConnectionCount(long id);

  Map<String, Object> creator(long id);

  Map<String, Object> account(long id);

  Map<String, Object> project(long id);

  Map<String, Object> work(long id);

  Map<String, Object> asset(long id);

  Map<String, Object> publication(long id);

  Map<String, Object> revenue(long id);

  Map<String, Object> cost(long id);

  Map<String, Object> contract(long id);

  Map<String, Object> settlement(long id);

  List<Map<String, Object>> projectMembers(long id);

  List<Map<String, Object>> workAssets(long id);

  List<Map<String, Object>> contractParticipants(long id);

  List<Map<String, Object>> settlementLines(long id);

  List<Map<String, Object>> settlementRevenues(long id);

  List<Map<String, Object>> settlementCosts(long id);

  List<Map<String, Object>> settlementHistory(long id);

  List<Map<String, Object>> balances(@Param("creatorId") Long creatorId);

  List<Map<String, Object>> platforms();

  List<Map<String, Object>> userOptions();

  void insertCreator(Map<String, Object> data);

  void updateCreator(Map<String, Object> data);

  void insertAccount(Map<String, Object> data);

  void updateAccount(Map<String, Object> data);

  void insertProject(Map<String, Object> data);

  void updateProject(Map<String, Object> data);

  void deleteMembers(long id);

  void addMember(@Param("projectId") long projectId, @Param("creatorId") long creatorId);

  void insertWork(Map<String, Object> data);

  void updateWork(Map<String, Object> data);

  void deleteWorkAssets(long id);

  void addWorkAsset(@Param("workId") long workId, @Param("assetId") long assetId);

  void insertAsset(Map<String, Object> data);

  void updateAsset(Map<String, Object> data);

  void deleteAssetPlatforms(long id);

  void addAssetPlatform(@Param("assetId") long assetId, @Param("code") String code);

  void deleteTags(long id);

  void insertTag(String name);

  void addAssetTag(@Param("assetId") long assetId, @Param("name") String name);

  void insertPublication(Map<String, Object> data);

  void insertRevenue(Map<String, Object> data);

  void confirmRevenue(
      @Param("id") long id, @Param("contractId") long contractId, @Param("actorId") long actorId);

  Map<String, Object> matchingContract(
      @Param("projectId") long projectId, @Param("date") Object date);

  Map<String, Object> duplicateRevenue(
      @Param("accountId") long accountId,
      @Param("sourceRef") String sourceRef,
      @Param("kind") String kind);

  java.math.BigDecimal refundedAmount(long id);

  void insertImportBatch(Map<String, Object> data);

  void insertCost(Map<String, Object> data);

  void approveCost(
      @Param("id") long id, @Param("contractId") long contractId, @Param("actorId") long actorId);

  void insertContract(Map<String, Object> data);

  void addParticipant(
      @Param("contractId") long contractId,
      @Param("creatorId") long creatorId,
      @Param("rate") java.math.BigDecimal rate);

  long overlappingContracts(Map<String, Object> data);

  void activateContract(long id);

  void generateSettlement(Map<String, Object> data);

  void calculateSettlement(Map<String, Object> data);

  void insertSettlementRevision(Map<String, Object> data);

  List<Map<String, Object>> settlementRevisions(long id);

  void releaseSettlementRevenues(long id);

  void releaseSettlementCosts(long id);

  void cancelSettlement(@Param("id") long id, @Param("actorId") long actorId, @Param("reason") String reason);

  void submitSettlement(@Param("id") long id, @Param("actorId") long actorId);

  void approveSettlement(@Param("id") long id, @Param("actorId") long actorId);

  void rejectSettlement(
      @Param("id") long id, @Param("actorId") long actorId, @Param("reason") String reason);

  Map<String, Object> lockCreator(long id);

  Map<String, Object> lockProject(long id);

  Map<String, Object> lockContract(long id);

  Map<String, Object> lockRevenue(long id);

  Map<String, Object> lockCost(long id);

  Map<String, Object> lockSettlement(long id);

  Map<String, Object> balance(long id);

  void insertPayment(Map<String, Object> data);

  List<Map<String, Object>> dashboardTrend(@Param("creatorId") Long creatorId);

  List<Map<String, Object>> dashboardPlatforms(@Param("creatorId") Long creatorId);

  Map<String, Object> dashboardRevenue(@Param("creatorId") Long creatorId);
}
