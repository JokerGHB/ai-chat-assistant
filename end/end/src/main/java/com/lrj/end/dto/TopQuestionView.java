package com.lrj.end.dto;

import lombok.Data;

/**
 * 高频提问统计项：提问内容 + 出现次数。
 * content 来源二选一：range=7d/30d 时为消息文本截前 100 字；range=all 时为预聚合表最新样例（截前 500 字）。
 */
@Data
public class TopQuestionView {
    private String content;
    private Long cnt;
}
