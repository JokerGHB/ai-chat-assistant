package com.lrj.end.mapper;

import com.lrj.end.entity.ChatMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

public interface ChatMessageMapper {
    @Select("""
            SELECT id, session_id, role, content, status, error_message,
                   prompt_tokens, completion_tokens, created_at
            FROM chat_message
            WHERE session_id = #{sessionId}
            ORDER BY created_at ASC, id ASC
            """)
    List<ChatMessage> findBySessionId(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT id, session_id, role, content, status, error_message,
                   prompt_tokens, completion_tokens, created_at
            FROM chat_message
            WHERE id = #{id}
            """)
    Optional<ChatMessage> findById(@Param("id") Long id);

    @Select("""
            SELECT id, session_id, role, content, status, error_message,
                   prompt_tokens, completion_tokens, created_at
            FROM chat_message
            WHERE id = #{messageId} AND session_id = #{sessionId}
            """)
    Optional<ChatMessage> findBySessionAndId(
            @Param("sessionId") Long sessionId,
            @Param("messageId") Long messageId);

    @Insert("""
            INSERT INTO chat_message (session_id, role, content, status, prompt_tokens, completion_tokens)
            VALUES (#{sessionId}, #{role}, #{content}, #{status}, #{promptTokens}, #{completionTokens})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatMessage message);
}
