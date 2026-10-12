package com.lrj.end.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class AiModelConfig {
    private Long id;
    private String name;
    private String provider;
    private String modelCode;
    private String baseUrl;
    private Boolean enabled;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
