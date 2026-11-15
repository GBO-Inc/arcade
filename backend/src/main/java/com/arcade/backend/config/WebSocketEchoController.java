package com.arcade.backend.config;

import java.security.Principal;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketEchoController {

  public record EchoMessage(String content) {}

  public record EchoResponse(String sender, String content) {}

  @MessageMapping("/echo")
  @SendTo("/topic/echo")
  public EchoResponse broadcastEcho(@Payload EchoMessage message, Principal principal) {
    return new EchoResponse(principal.getName(), message.content());
  }

  @MessageMapping("/echo/private")
  @SendToUser("/queue/echo")
  public EchoResponse privateEcho(@Payload EchoMessage message, Principal principal) {
    return new EchoResponse(principal.getName(), message.content());
  }
}
