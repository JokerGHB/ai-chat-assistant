package com.lrj.end.service;

import com.lrj.end.dto.ChatTurnResponse;
import com.lrj.end.entity.ChatMessage;
import com.lrj.end.entity.SysUser;
import com.lrj.end.exception.AiServiceException;
import com.lrj.end.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 一轮对话的编排服务。发送流程（顺序不能乱）：
 *  1. 敏感词检测 → 命中则 400 拦截，不调 AI、不落库；
 *  2. 每日次数校验 → 超限则 429 拒绝；
 *  3. 先存 user 消息（即使 AI 挂了用户提问也不丢）；
 *  4. 首条消息时自动生成会话标题；
 *  5. 调 DeepSeek：成功存 assistant(complete, 含 token/cost)；
 *     失败存 assistant(failed, 含失败原因) —— 未解决对话标记，仍返回 200 让前端展示友好提示。
 */
@Service
public class ChatTurnService {
    private final ChatMessageService messageService;
    private final ChatSessionService sessionService;
    private final SensitiveWordService sensitiveWordService;
    private final DeepSeekClient deepSeekClient;
    /** 单用户每日对话次数上限 */
    private final int dailyLimit;

    public ChatTurnService(ChatMessageService messageService,
                           ChatSessionService sessionService,
                           SensitiveWordService sensitiveWordService,
                           DeepSeekClient deepSeekClient,
                           @Value("${chat.daily-limit:100}") int dailyLimit) {
        this.messageService = messageService;
        this.sessionService = sessionService;
        this.sensitiveWordService = sensitiveWordService;
        this.deepSeekClient = deepSeekClient;
        this.dailyLimit = dailyLimit;
    }

    public ChatTurnResponse sendMessage(SysUser user, Long sessionId, String content) {
        // 1. 敏感词拦截：命中直接 400，不存任何消息
        if (sensitiveWordService.containsSensitive(content)) {
            throw new IllegalArgumentException("消息包含敏感内容，已被拦截，请修改后重试");
        }
        // 2. 每日次数限额：超限 429
        int usedToday = messageService.countUserMessagesToday(user.getId());
        if (usedToday >= dailyLimit) {
            throw new TooManyRequestsException("今日对话次数已达上限（" + dailyLimit + " 次），请明天再来");
        }

        // 3. 先落库用户提问（网络调用前持久化，AI 不可用时提问不丢）
        ChatMessage userMessage = messageService.addUserMessage(user.getId(), sessionId, content);

        // 4. 会话内第一条提问时，自动用提问内容生成会话标题
        if (messageService.countUserMessagesInSession(sessionId) == 1) {
            sessionService.applyAutoTitle(user.getId(), sessionId, content);
        }

        // 5. 调用大模型并落库回复
        List<ChatMessage> history = messageService.listMessages(user.getId(), sessionId);
        ChatMessage assistantMessage;
        try {
            DeepSeekClient.AiReply reply = deepSeekClient.generateReply(history);
            assistantMessage = messageService.addAssistantMessage(
                    user.getId(), sessionId, reply.content(),
                    reply.promptTokens(), reply.completionTokens(), reply.cost(),
                    "complete", null);
        } catch (AiServiceException exception) {
            // AI 调用失败：写入 failed 状态的 assistant 消息作为"未解决对话"标记，
            // 接口仍返回 200，前端展示这条失败提示，用户消息已保留可重试。
            assistantMessage = messageService.addAssistantMessage(
                    user.getId(), sessionId, "抱歉，AI 服务暂时不可用：" + exception.getMessage(),
                    null, null, null,
                    "failed", exception.getMessage());
        }
        return new ChatTurnResponse(userMessage, assistantMessage);
    }
}
