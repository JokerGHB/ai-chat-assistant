package com.lrj.end.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class ChatSession {
    private Long id;
    private String title;
    private Long modelConfigId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
