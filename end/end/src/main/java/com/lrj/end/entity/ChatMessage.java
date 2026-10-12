package com.lrj.end.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class ChatMessage {
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String status;
    private String errorMessage;
    private Integer promptTokens;
    private Integer completionTokens;
    private LocalDateTime createdAt;
}
