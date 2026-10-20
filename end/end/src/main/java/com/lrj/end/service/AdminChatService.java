package com.lrj.end.service;

import com.lrj.end.dto.AdminSessionView;
import com.lrj.end.dto.StatsOverviewResponse;
import com.lrj.end.dto.TopQuestionView;
import com.lrj.end.dto.UnresolvedMessageView;
import com.lrj.end.entity.ChatMessage;
import com.lrj.end.entity.ChatSession;
import com.lrj.end.entity.SysUser;
import com.lrj.end.exception.ResourceNotFoundException;
import com.lrj.end.mapper.ChatMessageMapper;
import com.lrj.end.mapper.ChatSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员对话管理与统计看板。
 * 统计指标全部从 chat_message 聚合，时间范围 range=7d|30d|all 换算成 from 时间点。
 */
@Service
public class AdminChatService {
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final OperationLogService logService;

    public AdminChatService(ChatSessionMapper sessionMapper,
                            ChatMessageMapper messageMapper,
                            OperationLogService logService) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.logService = logService;
    }

    /** 全平台会话列表（含逻辑删除标记，联表用户名） */
    @Transactional(readOnly = true)
    public List<AdminSessionView> listAllSessions() {
        return sessionMapper.findAllForAdmin();
    }

    /** 查看任意会话的消息（无用户隔离） */
    @Transactional(readOnly = true)
    public List<ChatMessage> listSessionMessages(Long sessionId) {
        requireSession(sessionId);
        return messageMapper.findBySessionIdForAdmin(sessionId);
    }

    /** 物理删除会话：真正 DELETE，消息靠外键级联删除；写操作日志 */
    @Transactional
    public void physicalDeleteSession(SysUser admin, Long sessionId, String ip) {
        ChatSession session = requireSession(sessionId);
        sessionMapper.physicalDelete(sessionId);
        logService.log(admin, "ADMIN_DELETE_SESSION", "session", String.valueOf(sessionId),
                "物理删除会话：" + session.getTitle(), ip);
    }

    /** 看板总览：调用次数（assistant 消息数）、总成本、未解决对话数 */
    @Transactional(readOnly = true)
    public StatsOverviewResponse overview(String range) {
        LocalDateTime from = resolveFrom(range);
        return new StatsOverviewResponse(
                messageMapper.countAssistantMessages(from),
                messageMapper.sumCost(from),
                messageMapper.countUnresolved(from));
    }

    /** 高频提问 Top N */
    @Transactional(readOnly = true)
    public List<TopQuestionView> topQuestions(String range, int limit) {
        return messageMapper.findTopQuestions(resolveFrom(range), limit);
    }

    /** 未解决对话明细（AI 失败消息） */
    @Transactional(readOnly = true)
    public List<UnresolvedMessageView> unresolved(String range, int limit) {
        return messageMapper.findUnresolved(resolveFrom(range), limit);
    }

    /** 时间范围换算：7d=近7天，30d=近30天，all=全部（from=null 不过滤） */
    private LocalDateTime resolveFrom(String range) {
        if (range == null || range.isBlank() || "all".equals(range)) {
            return null;
        }
        return switch (range) {
            case "7d" -> LocalDateTime.now().minusDays(7);
            case "30d" -> LocalDateTime.now().minusDays(30);
            default -> throw new IllegalArgumentException("不支持的时间范围：" + range + "（可选 7d/30d/all）");
        };
    }

    private ChatSession requireSession(Long sessionId) {
        return sessionMapper.findByIdForAdmin(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到会话：" + sessionId));
    }
}
