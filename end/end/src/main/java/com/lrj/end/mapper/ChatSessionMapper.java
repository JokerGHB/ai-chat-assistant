package com.lrj.end.mapper;

import com.lrj.end.dto.AdminSessionView;
import com.lrj.end.entity.ChatSession;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 会话 Mapper。多用户改造要点：
 *  - 所有查询/更新都带 user_id 条件，越权访问他人会话查不到（对外表现为 404）；
 *  - 列表和详情都过滤 deleted=0，逻辑删除的会话对普通用户不可见；
 *  - 删除走 softDelete（逻辑删除），物理删除只保留给管理端和定时清理任务。
 */
public interface ChatSessionMapper {
    /** 查询某用户的会话列表（不含已逻辑删除），按最近更新倒序 */
    @Select("""
            SELECT id, user_id, title, deleted, deleted_at, last_active_at, created_at, updated_at
            FROM chat_session
            WHERE user_id = #{userId} AND deleted = 0
            ORDER BY updated_at DESC, id DESC
            """)
    List<ChatSession> findAllByUser(@Param("userId") Long userId);

    /** 按 id + user_id 查询，同时满足"存在"和"属于当前用户"两个条件 */
    @Select("""
            SELECT id, user_id, title, deleted, deleted_at, last_active_at, created_at, updated_at
            FROM chat_session
            WHERE id = #{id} AND user_id = #{userId} AND deleted = 0
            """)
    Optional<ChatSession> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Insert("""
            INSERT INTO chat_session (user_id, title)
            VALUES (#{userId}, #{title})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatSession session);

    /** 改名，带 user_id 条件防止改到他人会话 */
    @Update("""
            UPDATE chat_session
            SET title = #{title}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId} AND deleted = 0
            """)
    int updateTitleByUser(@Param("id") Long id, @Param("userId") Long userId, @Param("title") String title);

    /** 刷新活动时间和更新时间（每次发消息时调用），last_active_at 供 90 天过期判定 */
    @Update("""
            UPDATE chat_session
            SET updated_at = CURRENT_TIMESTAMP(3), last_active_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int touch(@Param("id") Long id);

    /** 逻辑删除：打 deleted 标记并记录删除时间，数据保留供管理员查看/恢复 */
    @Update("""
            UPDATE chat_session
            SET deleted = 1, deleted_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId} AND deleted = 0
            """)
    int softDelete(@Param("id") Long id, @Param("userId") Long userId);

    // ==================== 管理端专用（无用户隔离、含已删除数据） ====================

    /** 管理员查看全平台会话（含逻辑删除标记），联表带出用户名便于展示 */
    @Select("""
            SELECT s.id, s.user_id, u.username, s.title, s.deleted, s.deleted_at,
                   s.last_active_at, s.created_at, s.updated_at
            FROM chat_session s
            JOIN sys_user u ON u.id = s.user_id
            ORDER BY s.updated_at DESC, s.id DESC
            """)
    List<AdminSessionView> findAllForAdmin();

    /** 管理员按 id 查会话（不过滤用户、不过滤删除标记） */
    @Select("""
            SELECT id, user_id, title, deleted, deleted_at, last_active_at, created_at, updated_at
            FROM chat_session
            WHERE id = #{id}
            """)
    Optional<ChatSession> findByIdForAdmin(@Param("id") Long id);

    /** 管理员物理删除会话（消息靠外键级联删除），这是真正的 DELETE */
    @Delete("DELETE FROM chat_session WHERE id = #{id}")
    int physicalDelete(@Param("id") Long id);

    /**
     * 定时清理：物理删除 last_active_at 早于 cutoff（90 天前）的会话，
     * 消息靠外键级联一并删除。返回删除行数供任务写日志。
     */
    @Delete("DELETE FROM chat_session WHERE last_active_at < #{cutoff}")
    int purgeExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
