package com.arcade.backend.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class TestController {

  record Payload(@NotBlank String name) {}

  @GetMapping("/test/boom")
  void boom() {
    throw new RuntimeException("secret detail");
  }

  @GetMapping("/test/missing")
  void missing() {
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
  }

  @PostMapping("/test/validate")
  void validate(@Valid @RequestBody Payload payload) {}
}
