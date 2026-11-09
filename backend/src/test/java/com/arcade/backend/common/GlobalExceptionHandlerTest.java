package com.arcade.backend.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// Filters off: this test is about the error body, not about Spring Security.
@WebMvcTest(TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.FixedClockConfig.class})
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

  private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @Autowired private MockMvc mockMvc;

  @Test
  void unexpectedExceptionReturnsGenericInternalServerError() throws Exception {
    String body =
        mockMvc
            .perform(get("/test/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.timestamp").value(NOW.toString()))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.error").value("Internal Server Error"))
            .andExpect(jsonPath("$.message").value("Unexpected error"))
            .andExpect(jsonPath("$.fieldErrors").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).doesNotContain("secret detail");
  }

  @Test
  void invalidBodyReturnsFieldErrors() throws Exception {
    mockMvc
        .perform(
            post("/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.timestamp").value(NOW.toString()))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Bad Request"))
        .andExpect(jsonPath("$.message").value("Validation failed"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
        .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty());
  }

  @Test
  void malformedBodyReturnsBadRequest() throws Exception {
    mockMvc
        .perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{oops"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Malformed request body"))
        .andExpect(jsonPath("$.fieldErrors").doesNotExist());
  }

  @Test
  void responseStatusExceptionKeepsStatusAndReason() throws Exception {
    mockMvc
        .perform(get("/test/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.timestamp").value(NOW.toString()))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.error").value("Not Found"))
        .andExpect(jsonPath("$.message").value("Room not found"));
  }

  @Test
  void unknownPathReturnsNotFound() throws Exception {
    mockMvc
        .perform(get("/no-such-path"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.error").value("Not Found"));
  }

  @Test
  void wrongMethodReturnsMethodNotAllowed() throws Exception {
    mockMvc
        .perform(post("/test/missing"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.status").value(405));
  }
}
