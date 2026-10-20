package com.lrj.end.controller;

import com.lrj.end.config.CurrentUser;
import com.lrj.end.dto.CreateMessageRequest;
import com.lrj.end.dto.ChatTurnResponse;
import com.lrj.end.dto.CreateSessionRequest;
import com.lrj.end.dto.UpdateSessionRequest;
import com.lrj.end.entity.ChatMessage;
import com.lrj.end.entity.ChatSession;
import com.lrj.end.entity.SysUser;
import com.lrj.end.service.ChatMessageService;
import com.lrj.end.service.ChatTurnService;
import com.lrj.end.service.ChatSessionService;
import jakarta.servlet.http.HttpServletRequest;
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

/**
 * 聊天会话接口。全部需要登录（AuthInterceptor 拦截 /api/**），
 * 通过 @CurrentUser 注入当前登录用户，业务层做用户数据隔离。
 */
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

    /** 当前用户的会话列表 */
    @GetMapping
    public List<ChatSession> listSessions(@CurrentUser SysUser user) {
        return sessionService.listSessions(user.getId());
    }

    @PostMapping
    public ResponseEntity<ChatSession> createSession(
            @CurrentUser SysUser user,
            @Valid @RequestBody(required = false) CreateSessionRequest request) {
        String title = request == null ? null : request.title();
        ChatSession session = sessionService.createSession(user.getId(), title);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(session.getId()).toUri();
        return ResponseEntity.created(location).body(session);
    }

    @GetMapping("/{sessionId}")
    public ChatSession getSession(@CurrentUser SysUser user, @PathVariable Long sessionId) {
        return sessionService.getSession(user.getId(), sessionId);
    }

    @PatchMapping("/{sessionId}")
    public ChatSession renameSession(
            @CurrentUser SysUser user,
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateSessionRequest request) {
        return sessionService.renameSession(user.getId(), sessionId, request.title());
    }

    /** 逻辑删除会话：数据保留，返回 204 */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(
            @CurrentUser SysUser user,
            @PathVariable Long sessionId,
            HttpServletRequest request) {
        sessionService.deleteSession(user, sessionId, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{sessionId}/messages")
    public List<ChatMessage> listMessages(@CurrentUser SysUser user, @PathVariable Long sessionId) {
        return messageService.listMessages(user.getId(), sessionId);
    }

    @GetMapping("/{sessionId}/messages/{messageId}")
    public ChatMessage getMessage(@CurrentUser SysUser user, @PathVariable Long sessionId, @PathVariable Long messageId) {
        return messageService.getMessage(user.getId(), sessionId, messageId);
    }

    /** 发送一轮对话：敏感词 400 / 超限 429 / AI 失败时返回 failed 状态的 assistant 消息 */
    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<ChatTurnResponse> addUserMessage(
            @CurrentUser SysUser user,
            @PathVariable Long sessionId,
            @Valid @RequestBody CreateMessageRequest request) {
        ChatTurnResponse response = chatTurnService.sendMessage(user, sessionId, request.content());
        return ResponseEntity.ok(response);
    }
}
