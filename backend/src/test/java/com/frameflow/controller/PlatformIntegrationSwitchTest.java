package com.frameflow.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.frameflow.common.*;
import com.frameflow.config.WebConfig;
import com.frameflow.integration.PlatformSettings;
import com.frameflow.service.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.*;

class PlatformIntegrationSwitchTest {
  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  @Import({PlatformIntegrationController.class, StudioController.class, FinanceController.class, WebConfig.class, Errors.class})
  static class WebApplication implements WebMvcConfigurer {
    @Bean PlatformIntegrationService platformIntegrationService() { return mock(PlatformIntegrationService.class); }
    @Bean PlatformSettings platformSettings(Environment environment) { return new PlatformSettings(environment); }
    @Bean StudioService studioService() { return mock(StudioService.class); }
    @Bean AssetService assetService() { return mock(AssetService.class); }
    @Bean AssetMediaService assetMediaService() { return mock(AssetMediaService.class); }
    @Bean FinanceService financeService() { return mock(FinanceService.class); }
    @Bean RevenueImportService revenueImportService() { return mock(RevenueImportService.class); }
    @Bean AuthService authService() {
      var auth = mock(AuthService.class);
      when(auth.authenticate("test-token")).thenReturn(new Actor(1, "admin", "管理员", List.of("ADMIN"), null));
      return auth;
    }
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
      registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"unset", "false", "true"})
  void switchControlsOnlyIntegrationRoutes(String setting) {
    var runner = new WebApplicationContextRunner().withUserConfiguration(WebApplication.class);
    if (!"unset".equals(setting)) runner = runner.withPropertyValues("PLATFORM_INTEGRATIONS_ENABLED=" + setting);
    runner.run(context -> {
      assertNull(context.getStartupFailure());
      var mvc = MockMvcBuilders.webAppContextSetup(context).build();
      var integration = context.getBean(PlatformIntegrationService.class);
      if ("true".equals(setting)) {
        assertEquals(1, context.getBeansOfType(PlatformIntegrationController.class).size());
        when(integration.integrations()).thenReturn(List.of(Map.of("code", "YOUTUBE", "status", "NOT_CONFIGURED")));
        mvc.perform(get("/api/platform-integrations").header("Authorization", "Bearer test-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].code").value("YOUTUBE"));
        when(integration.authorize("YOUTUBE", null)).thenReturn(Map.of("authorizationUrl", "https://example.test/authorize"));
        mvc.perform(post("/api/platform-integrations/YOUTUBE/authorize").header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.authorizationUrl").value("https://example.test/authorize"));
        when(integration.callback("YOUTUBE", "state", "code", null)).thenReturn("账号关联成功");
        mvc.perform(get("/api/platform-integrations/YOUTUBE/callback").param("state", "state").param("code", "code"))
            .andExpect(status().isSeeOther());
      } else {
        assertTrue(context.getBeansOfType(PlatformIntegrationController.class).isEmpty());
        for (String path : List.of("/api/platform-integrations", "/api/platform-connections", "/api/platform-content", "/api/platform-integrations/YOUTUBE/callback"))
          mvc.perform(get(path).header("Authorization", "Bearer test-token"))
              .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
        mvc.perform(post("/api/platform-integrations/YOUTUBE/authorize").header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/platform-connections/1/sync").header("Authorization", "Bearer test-token")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/platform-connections/1").header("Authorization", "Bearer test-token")).andExpect(status().isNotFound());
        verifyNoInteractions(integration);
      }
      manualBusinessRoutesRemainAvailable(mvc, context.getBean(StudioService.class), context.getBean(FinanceService.class));
    });
  }

  private void manualBusinessRoutesRemainAvailable(MockMvc mvc, StudioService studio, FinanceService finance) throws Exception {
    var page = Map.<String, Object>of("items", List.of(), "total", 0, "page", 1, "pageSize", 20);
    when(studio.accounts(anyMap())).thenReturn(page); when(studio.publications(anyMap())).thenReturn(page); when(studio.revenues(anyMap())).thenReturn(page);
    for (String path : List.of("/api/platform-accounts", "/api/publications", "/api/revenues"))
      mvc.perform(get(path).header("Authorization", "Bearer test-token")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    when(studio.saveAccount(isNull(), any())).thenReturn(21L);
    mvc.perform(post("/api/platform-accounts").header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
        .content("{\"platformId\":1,\"creatorId\":1,\"name\":\"人工账号\",\"externalId\":\"manual\",\"status\":\"ACTIVE\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(21));
    when(studio.publish(any())).thenReturn(22L);
    mvc.perform(post("/api/publications").header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
        .content("{\"workId\":1,\"platformAccountId\":21,\"externalId\":\"manual-work\",\"publishedAt\":\"2026-11-13 12:00:00\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(22));
    when(finance.createRevenue(any())).thenReturn(23L);
    mvc.perform(post("/api/revenues").header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
        .content("{\"publicationId\":22,\"sourceRef\":\"manual-revenue\",\"revenueDate\":\"2026-11-13\",\"kind\":\"INCOME\",\"grossAmount\":100,\"feeAmount\":0}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(23));
    verify(studio).saveAccount(isNull(), any()); verify(studio).publish(any()); verify(finance).createRevenue(any());
  }
}
