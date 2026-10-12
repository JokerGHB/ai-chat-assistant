package com.lrj.end.mapper;

import com.lrj.end.entity.ChatSession;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

public interface ChatSessionMapper {
    @Select("""
            SELECT id, title, model_config_id, created_at, updated_at
            FROM chat_session
            ORDER BY updated_at DESC, id DESC
            """)
    List<ChatSession> findAll();

    @Select("""
            SELECT id, title, model_config_id, created_at, updated_at
            FROM chat_session
            WHERE id = #{id}
            """)
    Optional<ChatSession> findById(@Param("id") Long id);

    @Insert("""
            INSERT INTO chat_session (title, model_config_id)
            VALUES (#{title}, #{modelConfigId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatSession session);

    @Update("""
            UPDATE chat_session
            SET title = #{title}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int updateTitle(@Param("id") Long id, @Param("title") String title);

    @Update("""
            UPDATE chat_session
            SET updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int touch(@Param("id") Long id);

    @Delete("DELETE FROM chat_session WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
