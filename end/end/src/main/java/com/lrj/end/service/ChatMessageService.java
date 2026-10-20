package com.lrj.end.service;

import com.lrj.end.entity.ChatMessage;
import com.lrj.end.exception.ResourceNotFoundException;
import com.lrj.end.mapper.ChatMessageMapper;
import com.lrj.end.mapper.ChatSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 消息业务。多用户改造要点：
 *  - 所有方法带 userId，先校验会话归属再操作消息，越权统一 404；
 *  - 写消息时写入冗余 user_id，assistant 消息附带 token 数和成本 cost；
 *  - 不提供任何单条消息删除接口（需求：禁止删除单条消息）。
 */
@Service
public class ChatMessageService {
    private final ChatMessageMapper messageMapper;
    private final ChatSessionMapper sessionMapper;

    public ChatMessageService(ChatMessageMapper messageMapper, ChatSessionMapper sessionMapper) {
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> listMessages(Long userId, Long sessionId) {
        requireSession(userId, sessionId);
        return messageMapper.findBySessionIdAndUserId(sessionId, userId);
    }

    @Transactional(readOnly = true)
    public ChatMessage getMessage(Long userId, Long sessionId, Long messageId) {
        requireSession(userId, sessionId);
        return messageMapper.findBySessionAndIdAndUserId(sessionId, messageId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到消息：" + messageId));
    }

    /** 用户当天已发送的 user 消息数（每日限额统计口径） */
    @Transactional(readOnly = true)
    public int countUserMessagesToday(Long userId) {
        return messageMapper.countUserMessagesToday(userId);
    }

    /** 会话内 user 消息数，供 ChatTurnService 判断是否首条消息以自动生成标题 */
    @Transactional(readOnly = true)
    public int countUserMessagesInSession(Long sessionId) {
        return messageMapper.countUserMessagesInSession(sessionId);
    }

    /** 写入一条 user 消息并刷新会话活动时间 */
    @Transactional
    public ChatMessage addUserMessage(Long userId, Long sessionId, String rawContent) {
        requireSession(userId, sessionId);
        String content = rawContent.trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole("user");
        message.setContent(content);
        message.setStatus("complete");
        messageMapper.insert(message);
        sessionMapper.touch(sessionId);
        return messageMapper.findById(message.getId())
                .orElseThrow(() -> new IllegalStateException("消息写入后无法读取"));
    }

    /**
     * 写入一条 assistant 消息。
     *  - 成功：status=complete，带 token 数和 cost；
     *  - 失败：status=failed（未解决对话），errorMessage 记录失败原因，用户消息仍保留。
     */
    @Transactional
    public ChatMessage addAssistantMessage(Long userId, Long sessionId, String content,
                                           Integer promptTokens, Integer completionTokens, BigDecimal cost,
                                           String status, String errorMessage) {
        requireSession(userId, sessionId);
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole("assistant");
        message.setContent(content);
        message.setStatus(status);
        message.setErrorMessage(errorMessage);
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setCost(cost);
        messageMapper.insert(message);
        sessionMapper.touch(sessionId);
        return messageMapper.findById(message.getId())
                .orElseThrow(() -> new IllegalStateException("消息写入后无法读取"));
    }

    /** 校验会话存在且属于当前用户，否则抛 404（不暴露他人数据存在性） */
    private void requireSession(Long userId, Long sessionId) {
        if (sessionMapper.findByIdAndUserId(sessionId, userId).isEmpty()) {
            throw new ResourceNotFoundException("找不到会话：" + sessionId);
        }
    }
}
