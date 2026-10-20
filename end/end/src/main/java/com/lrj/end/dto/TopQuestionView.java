package com.lrj.end.dto;

import lombok.Data;

/**
 * 高频提问统计项：提问内容（截前 100 字）+ 出现次数。
 */
@Data
public class TopQuestionView {
    private String content;
    private Long cnt;
}
