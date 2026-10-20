package com.lrj.end.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 敏感词过滤服务。
 * 启动时从配置 chat.sensitive-words（英文逗号分隔）加载词库到内存 Set，
 * 发送消息前做包含匹配（忽略大小写），命中即拦截，不提交大模型、不落库。
 * 词库不要求管理员在线维护，修改配置重启即可生效。
 */
@Service
public class SensitiveWordService {
    private final String configuredWords;
    /** 内存词库，统一转小写存储便于忽略大小写匹配 */
    private Set<String> words = Set.of();

    public SensitiveWordService(@Value("${chat.sensitive-words:}") String configuredWords) {
        this.configuredWords = configuredWords;
    }

    @PostConstruct
    void loadWords() {
        if (configuredWords == null || configuredWords.isBlank()) {
            this.words = Set.of();
            return;
        }
        this.words = Arrays.stream(configuredWords.split(","))
                .map(String::trim)
                .filter(word -> !word.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 判断内容是否命中敏感词（忽略大小写的包含匹配） */
    public boolean containsSensitive(String content) {
        if (content == null || words.isEmpty()) {
            return false;
        }
        String lowered = content.toLowerCase();
        return words.stream().anyMatch(lowered::contains);
    }
}
