package com.arcade.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  // Stateless bearer tokens in the Authorization header, no cookies, so CSRF protection and
  // sessions are not needed.
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JsonAuthenticationEntryPoint entryPoint) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(requests ->
            requests
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login")
                .permitAll()
                .requestMatchers("/actuator/health", "/error")
                .permitAll()
                .anyRequest()
                .authenticated())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(Customizer.withDefaults()).authenticationEntryPoint(entryPoint))
        .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
        .build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
