package com.lrj.end.controller;

import com.lrj.end.dto.CreateMessageRequest;
import com.lrj.end.dto.ChatTurnResponse;
import com.lrj.end.dto.CreateSessionRequest;
import com.lrj.end.dto.UpdateSessionRequest;
import com.lrj.end.entity.ChatMessage;
import com.lrj.end.entity.ChatSession;
import com.lrj.end.service.ChatMessageService;
import com.lrj.end.service.ChatTurnService;
import com.lrj.end.service.ChatSessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class ChatSessionController {
    private final ChatSessionService sessionService;
    private final ChatMessageService messageService;
    private final ChatTurnService chatTurnService;

    public ChatSessionController(ChatSessionService sessionService, ChatMessageService messageService, ChatTurnService chatTurnService) {
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.chatTurnService = chatTurnService;
    }

    @GetMapping
    public List<ChatSession> listSessions() {
        return sessionService.listSessions();
    }

    @PostMapping
    public ResponseEntity<ChatSession> createSession(@Valid @RequestBody(required = false) CreateSessionRequest request) {
        String title = request == null ? null : request.title();
        Long modelConfigId = request == null ? null : request.modelConfigId();
        ChatSession session = sessionService.createSession(title, modelConfigId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(session.getId()).toUri();
        return ResponseEntity.created(location).body(session);
    }

    @GetMapping("/{sessionId}")
    public ChatSession getSession(@PathVariable Long sessionId) {
        return sessionService.getSession(sessionId);
    }

    @PatchMapping("/{sessionId}")
    public ChatSession renameSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateSessionRequest request) {
        return sessionService.renameSession(sessionId, request.title());
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable Long sessionId) {
        sessionService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{sessionId}/messages")
    public List<ChatMessage> listMessages(@PathVariable Long sessionId) {
        return messageService.listMessages(sessionId);
    }

    @GetMapping("/{sessionId}/messages/{messageId}")
    public ChatMessage getMessage(@PathVariable Long sessionId, @PathVariable Long messageId) {
        return messageService.getMessage(sessionId, messageId);
    }

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<ChatTurnResponse> addUserMessage(
            @PathVariable Long sessionId,
            @Valid @RequestBody CreateMessageRequest request) {
        ChatTurnResponse response = chatTurnService.sendMessage(sessionId, request.content());
        return ResponseEntity.ok(response);
    }
}
