package com.lrj.end.service;

import com.lrj.end.entity.ChatSession;
import com.lrj.end.entity.SysUser;
import com.lrj.end.exception.ResourceNotFoundException;
import com.lrj.end.mapper.ChatSessionMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 会话业务。多用户改造要点：
 *  - 所有方法都以"当前登录用户"为前提，查询带 user_id，越权访问统一抛 404
 *    （不暴露他人数据是否存在的任何信息）；
 *  - 删除为逻辑删除，并写操作日志 DELETE_SESSION；
 *  - 首条消息发送后自动用提问内容生成会话标题。
 */
@Service
public class ChatSessionService {
    /** 新建会话的默认标题，用于判断是否需要自动生成标题 */
    public static final String DEFAULT_TITLE = "新对话";

    private final ChatSessionMapper sessionMapper;
    private final OperationLogService logService;
    /** 自动标题截取的提问长度 */
    private final int titleMaxLength;

    public ChatSessionService(ChatSessionMapper sessionMapper,
                              OperationLogService logService,
                              @Value("${chat.title-max-length:24}") int titleMaxLength) {
        this.sessionMapper = sessionMapper;
        this.logService = logService;
        this.titleMaxLength = titleMaxLength;
    }

    /** 当前用户的会话列表（不含已逻辑删除） */
    @Transactional(readOnly = true)
    public List<ChatSession> listSessions(Long userId) {
        return sessionMapper.findAllByUser(userId);
    }

    /** 查询当前用户的单个会话，不存在或不属于该用户都抛 404 */
    @Transactional(readOnly = true)
    public ChatSession getSession(Long userId, Long id) {
        return sessionMapper.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到会话：" + id));
    }

    @Transactional
    public ChatSession createSession(Long userId, String requestedTitle) {
        String title = requestedTitle == null || requestedTitle.isBlank() ? DEFAULT_TITLE : requestedTitle.trim();
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title);
        sessionMapper.insert(session);
        return getSession(userId, session.getId());
    }

    @Transactional
    public ChatSession renameSession(Long userId, Long id, String title) {
        getSession(userId, id);
        sessionMapper.updateTitleByUser(id, userId, title.trim());
        return getSession(userId, id);
    }

    /** 逻辑删除：数据保留，管理员端可见/可物理删除；写操作日志 */
    @Transactional
    public void deleteSession(SysUser user, Long id, String ip) {
        ChatSession session = getSession(user.getId(), id);
        sessionMapper.softDelete(id, user.getId());
        logService.log(user, "DELETE_SESSION", "session", String.valueOf(id),
                "逻辑删除会话：" + session.getTitle(), ip);
    }

    /**
     * 首条消息发送后自动生成标题：仅当会话标题仍是默认标题时，
     * 取首条提问的前 titleMaxLength 个字符作为标题。
     */
    @Transactional
    public void applyAutoTitle(Long userId, Long sessionId, String firstContent) {
        ChatSession session = getSession(userId, sessionId);
        if (!DEFAULT_TITLE.equals(session.getTitle())) {
            return;
        }
        String content = firstContent.trim();
        String autoTitle = content.length() <= titleMaxLength
                ? content
                : content.substring(0, titleMaxLength);
        sessionMapper.updateTitleByUser(sessionId, userId, autoTitle);
    }
}
