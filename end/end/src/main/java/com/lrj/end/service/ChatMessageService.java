package com.lrj.end.service;

import com.lrj.end.entity.ChatMessage;
import com.lrj.end.exception.ResourceNotFoundException;
import com.lrj.end.mapper.ChatMessageMapper;
import com.lrj.end.mapper.ChatSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChatMessageService {
    private final ChatMessageMapper messageMapper;
    private final ChatSessionMapper sessionMapper;

    public ChatMessageService(ChatMessageMapper messageMapper, ChatSessionMapper sessionMapper) {
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> listMessages(Long sessionId) {
        requireSession(sessionId);
        return messageMapper.findBySessionId(sessionId);
    }

    @Transactional(readOnly = true)
    public ChatMessage getMessage(Long sessionId, Long messageId) {
        requireSession(sessionId);
        return messageMapper.findBySessionAndId(sessionId, messageId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到消息：" + messageId));
    }

    @Transactional
    public ChatMessage addUserMessage(Long sessionId, String rawContent) {
        requireSession(sessionId);
        String content = rawContent.trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole("user");
        message.setContent(content);
        message.setStatus("complete");
        messageMapper.insert(message);
        sessionMapper.touch(sessionId);
        return messageMapper.findById(message.getId())
                .orElseThrow(() -> new IllegalStateException("消息写入后无法读取"));
    }

    @Transactional
    public ChatMessage addAssistantMessage(Long sessionId, String content, Integer promptTokens, Integer completionTokens) {
        requireSession(sessionId);
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole("assistant");
        message.setContent(content);
        message.setStatus("complete");
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        messageMapper.insert(message);
        sessionMapper.touch(sessionId);
        return messageMapper.findById(message.getId())
                .orElseThrow(() -> new IllegalStateException("消息写入后无法读取"));
    }

    private void requireSession(Long sessionId) {
        if (sessionMapper.findById(sessionId).isEmpty()) {
            throw new ResourceNotFoundException("找不到会话：" + sessionId);
        }
    }
}
