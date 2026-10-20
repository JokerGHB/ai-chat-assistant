package com.lrj.end.service;

import com.lrj.end.entity.ChatMessage;
import com.lrj.end.exception.AiServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeepSeekClient {
    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    /** token 单价（元/千 token），用于计算每次调用的成本写入 chat_message.cost */
    private final BigDecimal promptPricePer1k;
    private final BigDecimal completionPricePer1k;

    public DeepSeekClient(
            @Value("${deepseek.base-url}") String baseUrl,
            @Value("${deepseek.api-key:}") String apiKey,
            @Value("${deepseek.model}") String model,
            @Value("${deepseek.prompt-price-per-1k:0}") BigDecimal promptPricePer1k,
            @Value("${deepseek.completion-price-per-1k:0}") BigDecimal completionPricePer1k) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
        this.promptPricePer1k = promptPricePer1k;
        this.completionPricePer1k = completionPricePer1k;
    }

    public AiReply generateReply(List<ChatMessage> history) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                    "尚未配置 DeepSeek API 密钥，请在 IDEA 运行配置中设置 DEEPSEEK_API_KEY");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "你是一个友好的中文 AI 助手。除非用户指定其他语言，否则请使用中文回答。"));
        history.stream()
                .filter(message -> "user".equals(message.getRole()) || "assistant".equals(message.getRole()))
                .forEach(message -> messages.add(Map.of("role", message.getRole(), "content", message.getContent())));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", messages);
        request.put("stream", false);

        try {
            Map<String, Object> response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return parseReply(response);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
                throw new AiServiceException(HttpStatus.BAD_GATEWAY, "DeepSeek API 密钥无效或没有调用权限");
            }
            if (exception.getStatusCode().value() == 429) {
                throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "DeepSeek 服务繁忙或额度不足，请稍后重试");
            }
            throw new AiServiceException(HttpStatus.BAD_GATEWAY, "调用 DeepSeek 服务失败，请稍后重试");
        } catch (RestClientException exception) {
            throw new AiServiceException(HttpStatus.BAD_GATEWAY, "无法连接 DeepSeek 服务，请检查网络后重试");
        }
    }

    @SuppressWarnings("unchecked")
    private AiReply parseReply(Map<String, Object> response) {
        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            throw new AiServiceException(HttpStatus.BAD_GATEWAY, "DeepSeek 服务没有返回有效回复");
        }
        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choice) || !(choice.get("message") instanceof Map<?, ?> message)) {
            throw new AiServiceException(HttpStatus.BAD_GATEWAY, "DeepSeek 服务返回的数据格式不正确");
        }
        Object content = message.get("content");
        if (!(content instanceof String text) || text.isBlank()) {
            throw new AiServiceException(HttpStatus.BAD_GATEWAY, "DeepSeek 服务返回了空回复");
        }

        Integer promptTokens = null;
        Integer completionTokens = null;
        if (response.get("usage") instanceof Map<?, ?> usage) {
            promptTokens = numberValue(usage.get("prompt_tokens"));
            completionTokens = numberValue(usage.get("completion_tokens"));
        }
        return new AiReply(text, promptTokens, completionTokens, calculateCost(promptTokens, completionTokens));
    }

    /** 按配置的单价计算本次调用成本（元），token 数缺失时返回 null */
    private BigDecimal calculateCost(Integer promptTokens, Integer completionTokens) {
        if (promptTokens == null && completionTokens == null) {
            return null;
        }
        BigDecimal thousand = new BigDecimal("1000");
        BigDecimal cost = BigDecimal.ZERO;
        if (promptTokens != null) {
            cost = cost.add(promptPricePer1k.multiply(new BigDecimal(promptTokens)).divide(thousand, 6, RoundingMode.HALF_UP));
        }
        if (completionTokens != null) {
            cost = cost.add(completionPricePer1k.multiply(new BigDecimal(completionTokens)).divide(thousand, 6, RoundingMode.HALF_UP));
        }
        return cost;
    }

    private Integer numberValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    public record AiReply(String content, Integer promptTokens, Integer completionTokens, BigDecimal cost) {
    }
}
