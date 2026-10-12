package com.lrj.end.service;

import com.lrj.end.entity.ChatSession;
import com.lrj.end.exception.ResourceNotFoundException;
import com.lrj.end.mapper.AiModelConfigMapper;
import com.lrj.end.mapper.ChatSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChatSessionService {
    private final ChatSessionMapper sessionMapper;
    private final AiModelConfigMapper modelConfigMapper;

    public ChatSessionService(ChatSessionMapper sessionMapper, AiModelConfigMapper modelConfigMapper) {
        this.sessionMapper = sessionMapper;
        this.modelConfigMapper = modelConfigMapper;
    }

    @Transactional(readOnly = true)
    public List<ChatSession> listSessions() {
        return sessionMapper.findAll();
    }

    @Transactional(readOnly = true)
    public ChatSession getSession(Long id) {
        return sessionMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("找不到会话：" + id));
    }

    @Transactional
    public ChatSession createSession(String requestedTitle, Long modelConfigId) {
        if (modelConfigId != null && modelConfigMapper.findEnabledById(modelConfigId).isEmpty()) {
            throw new IllegalArgumentException("指定的模型不存在或未启用");
        }
        String title = requestedTitle == null || requestedTitle.isBlank() ? "新对话" : requestedTitle.trim();
        ChatSession session = new ChatSession();
        session.setTitle(title);
        session.setModelConfigId(modelConfigId);
        sessionMapper.insert(session);
        return getSession(session.getId());
    }

    @Transactional
    public ChatSession renameSession(Long id, String title) {
        getSession(id);
        sessionMapper.updateTitle(id, title.trim());
        return getSession(id);
    }

    @Transactional
    public void deleteSession(Long id) {
        getSession(id);
        sessionMapper.deleteById(id);
    }
}
