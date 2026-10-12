package com.lrj.end.dto;

import com.lrj.end.entity.ChatMessage;

public record ChatTurnResponse(ChatMessage userMessage, ChatMessage assistantMessage) {
}
