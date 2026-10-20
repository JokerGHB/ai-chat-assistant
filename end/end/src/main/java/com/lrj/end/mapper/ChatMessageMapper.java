package com.lrj.end.mapper;

import com.lrj.end.dto.TopQuestionView;
import com.lrj.end.dto.UnresolvedMessageView;
import com.lrj.end.entity.ChatMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 消息 Mapper。多用户改造要点：
 *  - 查询都带 user_id 条件，越权读他人消息查不到；
 *  - insert 写入冗余的 user_id 和 assistant 消息的 cost；
 *  - countUserMessagesToday 供每日对话次数限额实时统计。
 */
public interface ChatMessageMapper {
    /** 查询某会话下属于某用户的全部消息（按时间正序） */
    @Select("""
            SELECT id, session_id, user_id, role, content, status, error_message,
                   prompt_tokens, completion_tokens, cost, created_at
            FROM chat_message
            WHERE session_id = #{sessionId} AND user_id = #{userId}
            ORDER BY created_at ASC, id ASC
            """)
    List<ChatMessage> findBySessionIdAndUserId(@Param("sessionId") Long sessionId, @Param("userId") Long userId);

    @Select("""
            SELECT id, session_id, user_id, role, content, status, error_message,
                   prompt_tokens, completion_tokens, cost, created_at
            FROM chat_message
            WHERE id = #{id}
            """)
    Optional<ChatMessage> findById(@Param("id") Long id);

    /** 按会话 + 消息 + 用户三重条件查询，防止跨用户/跨会话读取 */
    @Select("""
            SELECT id, session_id, user_id, role, content, status, error_message,
                   prompt_tokens, completion_tokens, cost, created_at
            FROM chat_message
            WHERE id = #{messageId} AND session_id = #{sessionId} AND user_id = #{userId}
            """)
    Optional<ChatMessage> findBySessionAndIdAndUserId(
            @Param("sessionId") Long sessionId,
            @Param("messageId") Long messageId,
            @Param("userId") Long userId);

    /**
     * 统计用户当天已发送的 user 消息数（每日对话次数限额口径：
     * 一次"对话"= 一条 user 消息，assistant 回复不计入）。
     * CURDATE() 取数据库当天 0 点，与服务器时区配置一致。
     */
    @Select("""
            SELECT COUNT(*)
            FROM chat_message
            WHERE user_id = #{userId} AND role = 'user' AND created_at >= CURDATE()
            """)
    int countUserMessagesToday(@Param("userId") Long userId);

    /** 统计会话内 user 消息数，用于判断是否为首条消息（决定是否自动生成标题） */
    @Select("""
            SELECT COUNT(*)
            FROM chat_message
            WHERE session_id = #{sessionId} AND role = 'user'
            """)
    int countUserMessagesInSession(@Param("sessionId") Long sessionId);

    @Insert("""
            INSERT INTO chat_message (session_id, user_id, role, content, status, error_message,
                                      prompt_tokens, completion_tokens, cost)
            VALUES (#{sessionId}, #{userId}, #{role}, #{content}, #{status}, #{errorMessage},
                    #{promptTokens}, #{completionTokens}, #{cost})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatMessage message);

    // ==================== 管理端统计（聚合 chat_message，时间范围由 from 参数控制，null 表示全部） ====================

    /** 调用次数 = assistant 消息数 */
    @Select("""
            SELECT COUNT(*) FROM chat_message
            WHERE role = 'assistant' AND (#{from} IS NULL OR created_at >= #{from})
            """)
    long countAssistantMessages(@Param("from") LocalDateTime from);

    /** 总成本 = SUM(cost)，无数据时返回 0 */
    @Select("""
            SELECT IFNULL(SUM(cost), 0) FROM chat_message
            WHERE role = 'assistant' AND (#{from} IS NULL OR created_at >= #{from})
            """)
    BigDecimal sumCost(@Param("from") LocalDateTime from);

    /** 未解决对话数 = AI 调用失败的 assistant 消息数 */
    @Select("""
            SELECT COUNT(*) FROM chat_message
            WHERE role = 'assistant' AND status = 'failed' AND (#{from} IS NULL OR created_at >= #{from})
            """)
    long countUnresolved(@Param("from") LocalDateTime from);

    /** 高频提问 Top N：按 user 消息内容分组计数（截前 100 字分组，避免长文差异稀释计数） */
    @Select("""
            SELECT LEFT(content, 100) AS content, COUNT(*) AS cnt
            FROM chat_message
            WHERE role = 'user' AND (#{from} IS NULL OR created_at >= #{from})
            GROUP BY LEFT(content, 100)
            ORDER BY cnt DESC
            LIMIT #{limit}
            """)
    List<TopQuestionView> findTopQuestions(@Param("from") LocalDateTime from, @Param("limit") int limit);

    /** 未解决对话明细（AI 失败的 assistant 消息，联表带出用户名） */
    @Select("""
            SELECT m.id, m.session_id, m.user_id, u.username, m.content, m.error_message, m.created_at
            FROM chat_message m
            JOIN sys_user u ON u.id = m.user_id
            WHERE m.role = 'assistant' AND m.status = 'failed' AND (#{from} IS NULL OR m.created_at >= #{from})
            ORDER BY m.created_at DESC, m.id DESC
            LIMIT #{limit}
            """)
    List<UnresolvedMessageView> findUnresolved(@Param("from") LocalDateTime from, @Param("limit") int limit);

    /** 管理员查看任意会话的消息（无用户隔离） */
    @Select("""
            SELECT id, session_id, user_id, role, content, status, error_message,
                   prompt_tokens, completion_tokens, cost, created_at
            FROM chat_message
            WHERE session_id = #{sessionId}
            ORDER BY created_at ASC, id ASC
            """)
    List<ChatMessage> findBySessionIdForAdmin(@Param("sessionId") Long sessionId);
}
