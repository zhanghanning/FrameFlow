package com.frameflow.controller;

import com.frameflow.common.*;
import com.frameflow.integration.PlatformSettings;
import com.frameflow.service.PlatformIntegrationService;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@ConditionalOnProperty(name = "PLATFORM_INTEGRATIONS_ENABLED", havingValue = "true", matchIfMissing = false)
@RequestMapping("/api")
public class PlatformIntegrationController {
  private final PlatformIntegrationService service;
  private final PlatformSettings settings;
  public PlatformIntegrationController(PlatformIntegrationService service, PlatformSettings settings) { this.service = service; this.settings = settings; }
  public record Authorization(Long creatorId) {}

  @GetMapping("/platform-integrations")
  public Api<?> integrations() { return Api.ok(service.integrations()); }
  @GetMapping("/platform-connections")
  public Api<?> connections() { return Api.ok(service.connections()); }
  @PostMapping("/platform-integrations/{code}/authorize")
  public Api<?> authorize(@PathVariable String code, @RequestBody(required=false) Authorization request) {
    return Api.ok(service.authorize(code, request == null ? null : request.creatorId()));
  }
  @GetMapping("/platform-integrations/{platform}/callback")
  public ResponseEntity<Void> callback(@PathVariable String platform,
      @RequestParam(required=false) String state, @RequestParam(required=false) String code,
      @RequestParam(required=false) String error) {
    String result = "success", message;
    try { message = service.callback(platform, state, code, error); }
    catch (BusinessException ex) { result = "error"; message = ex.getMessage(); }
    catch (Exception ex) { result = "error"; message = "平台关联未完成，请重新发起或联系管理员检查配置"; }
    var location = UriComponentsBuilder.fromUriString(settings.frontendUrl() + "/platform-accounts")
        .queryParam("connection", result).queryParam("message", message).build().encode().toUri();
    return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).header(HttpHeaders.CACHE_CONTROL, "private, no-store").build();
  }
  @PostMapping("/platform-connections/{id}/sync")
  public Api<?> sync(@PathVariable long id) { return Api.ok(service.sync(id)); }
  @DeleteMapping("/platform-connections/{id}")
  public Api<?> disconnect(@PathVariable long id) { service.disconnect(id); return Api.ok(null); }
  @GetMapping("/platform-content")
  public Api<?> content(@RequestParam Map<String, String> query) { return Api.ok(service.content(query)); }
}
