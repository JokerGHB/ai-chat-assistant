package com.lrj.end.controller;

import com.lrj.end.config.CurrentUser;
import com.lrj.end.dto.AdminSessionView;
import com.lrj.end.entity.ChatMessage;
import com.lrj.end.entity.SysUser;
import com.lrj.end.service.AdminChatService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员对话管理接口：全平台会话查看 + 物理删除。
 */
@RestController
@RequestMapping("/api/admin/sessions")
public class AdminChatController {
    private final AdminChatService adminChatService;

    public AdminChatController(AdminChatService adminChatService) {
        this.adminChatService = adminChatService;
    }

    /** 全平台会话列表（含逻辑删除标记） */
    @GetMapping
    public List<AdminSessionView> listAllSessions() {
        return adminChatService.listAllSessions();
    }

    /** 查看任意会话的消息 */
    @GetMapping("/{sessionId}/messages")
    public List<ChatMessage> listSessionMessages(@PathVariable Long sessionId) {
        return adminChatService.listSessionMessages(sessionId);
    }

    /** 物理删除会话（真正 DELETE，不可恢复） */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> physicalDeleteSession(@CurrentUser SysUser admin,
                                                      @PathVariable Long sessionId,
                                                      HttpServletRequest request) {
        adminChatService.physicalDeleteSession(admin, sessionId, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
