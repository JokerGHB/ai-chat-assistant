package com.lrj.end.service;

import com.lrj.end.dto.ChatTurnResponse;
import com.lrj.end.entity.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatTurnService {
    private final ChatMessageService messageService;
    private final DeepSeekClient deepSeekClient;

    public ChatTurnService(ChatMessageService messageService, DeepSeekClient deepSeekClient) {
        this.messageService = messageService;
        this.deepSeekClient = deepSeekClient;
    }

    public ChatTurnResponse sendMessage(Long sessionId, String content) {
        // Persist the user input before the network call so it remains available if the provider is down.
        ChatMessage userMessage = messageService.addUserMessage(sessionId, content);
        List<ChatMessage> history = messageService.listMessages(sessionId);
        DeepSeekClient.AiReply reply = deepSeekClient.generateReply(history);
        ChatMessage assistantMessage = messageService.addAssistantMessage(
                sessionId, reply.content(), reply.promptTokens(), reply.completionTokens());
        return new ChatTurnResponse(userMessage, assistantMessage);
    }
}
