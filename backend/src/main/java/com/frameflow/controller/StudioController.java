package com.frameflow.controller;

import com.frameflow.common.*;
import com.frameflow.dto.Requests;
import com.frameflow.service.*;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class StudioController {
  private final StudioService service;
  private final AssetService assets;
  private final AssetMediaService media;

  public StudioController(StudioService service, AssetService assets, AssetMediaService media) {
    this.service = service;
    this.assets = assets;
    this.media = media;
  }

  @GetMapping("/options")
  public Api<?> options() {
    return Api.ok(service.options());
  }

  @GetMapping("/dashboard")
  public Api<?> dashboard() {
    return Api.ok(service.dashboard());
  }

  @GetMapping("/creators")
  public Api<?> creators(@RequestParam Map<String, String> query) {
    return Api.ok(service.creators(query));
  }

  @PostMapping("/creators")
  public Api<?> createCreator(@Valid @RequestBody Requests.Creator request) {
    return Api.ok(Map.of("id", service.saveCreator(null, request)));
  }

  @PutMapping("/creators/{id}")
  public Api<?> editCreator(@PathVariable long id, @Valid @RequestBody Requests.Creator request) {
    return Api.ok(Map.of("id", service.saveCreator(id, request)));
  }

  @GetMapping("/platform-accounts")
  public Api<?> accounts(@RequestParam Map<String, String> query) {
    return Api.ok(service.accounts(query));
  }

  @PostMapping("/platform-accounts")
  public Api<?> createAccount(@Valid @RequestBody Requests.Account request) {
    return Api.ok(Map.of("id", service.saveAccount(null, request)));
  }

  @PutMapping("/platform-accounts/{id}")
  public Api<?> editAccount(@PathVariable long id, @Valid @RequestBody Requests.Account request) {
    return Api.ok(Map.of("id", service.saveAccount(id, request)));
  }

  @GetMapping("/projects")
  public Api<?> projects(@RequestParam Map<String, String> query) {
    return Api.ok(service.projects(query));
  }

  @GetMapping("/projects/{id}")
  public Api<?> project(@PathVariable long id) {
    return Api.ok(service.project(id));
  }

  @PostMapping("/projects")
  public Api<?> createProject(@Valid @RequestBody Requests.Project request) {
    return Api.ok(Map.of("id", service.saveProject(null, request)));
  }

  @PutMapping("/projects/{id}")
  public Api<?> editProject(@PathVariable long id, @Valid @RequestBody Requests.Project request) {
    return Api.ok(Map.of("id", service.saveProject(id, request)));
  }

  @GetMapping("/works")
  public Api<?> works(@RequestParam Map<String, String> query) {
    return Api.ok(service.works(query));
  }

  @GetMapping("/works/{id}")
  public Api<?> work(@PathVariable long id) {
    return Api.ok(service.work(id));
  }

  @PostMapping("/works")
  public Api<?> createWork(@Valid @RequestBody Requests.Work request) {
    return Api.ok(Map.of("id", service.saveWork(null, request)));
  }

  @PutMapping("/works/{id}")
  public Api<?> editWork(@PathVariable long id, @Valid @RequestBody Requests.Work request) {
    return Api.ok(Map.of("id", service.saveWork(id, request)));
  }

  @GetMapping("/assets")
  public Api<?> assets(@RequestParam Map<String, String> query) {
    return Api.ok(service.assets(query));
  }

  @GetMapping("/assets/{id}")
  public Api<?> asset(@PathVariable long id) {
    return Api.ok(assets.details(id));
  }

  @PostMapping("/assets")
  public Api<?> upload(
      @RequestPart("file") MultipartFile file, @RequestParam Map<String, String> fields)
      throws IOException {
    return Api.ok(Map.of("id", assets.upload(file, fields)));
  }

  @PutMapping("/assets/{id}")
  public Api<?> updateAsset(@PathVariable long id, @RequestBody Map<String, Object> fields) {
    assets.update(id, fields);
    return Api.ok(null);
  }

  @PutMapping("/assets/{id}/projects")
  public Api<?> shareAsset(@PathVariable long id, @RequestBody Map<String, List<Long>> fields) {
    service.shareAsset(id, fields.get("projectIds"));
    return Api.ok(null);
  }

  @GetMapping("/assets/{id}/media")
  public ResponseEntity<Api<?>> media(@PathVariable long id) {
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .body(Api.ok(media.metadata(id)));
  }

  @GetMapping("/assets/{id}/poster")
  public ResponseEntity<FileSystemResource> poster(@PathVariable long id) throws IOException {
    return mediaResponse(media.poster(id), MediaType.IMAGE_JPEG);
  }

  @GetMapping("/assets/{id}/preview")
  public ResponseEntity<FileSystemResource> preview(@PathVariable long id) throws IOException {
    return mediaResponse(media.preview(id), MediaType.parseMediaType("video/mp4"));
  }

  private ResponseEntity<FileSystemResource> mediaResponse(java.nio.file.Path path, MediaType type)
      throws IOException {
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .contentType(type).contentLength(java.nio.file.Files.size(path))
        .body(new FileSystemResource(path));
  }

  @GetMapping("/assets/{id}/file")
  public ResponseEntity<FileSystemResource> file(@PathVariable long id) throws IOException {
    var details = assets.details(id);
    var path = assets.file(id);
    var resource = new FileSystemResource(path);
    String filename = details.get("originalFilename").toString();
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString())
        .contentType(MediaType.parseMediaType(details.get("mimeType").toString()))
        .contentLength(java.nio.file.Files.size(path))
        .body(resource);
  }

  @GetMapping("/publications")
  public Api<?> publications(@RequestParam Map<String, String> query) {
    return Api.ok(service.publications(query));
  }

  @PostMapping("/publications")
  public Api<?> publish(@Valid @RequestBody Requests.Publication request) {
    return Api.ok(Map.of("id", service.publish(request)));
  }

  @GetMapping("/audit-logs")
  public Api<?> audits(@RequestParam Map<String, String> query) {
    return Api.ok(service.audits(query));
  }
}
