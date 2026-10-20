package com.lrj.end.mapper;

import com.lrj.end.entity.OperationLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 操作日志访问。故意只提供 insert 和查询，不提供 update/delete —— 日志不可篡改。
 */
public interface OperationLogMapper {

    @Insert("""
            INSERT INTO operation_log (user_id, username, action, target_type, target_id, detail, ip)
            VALUES (#{userId}, #{username}, #{action}, #{targetType}, #{targetId}, #{detail}, #{ip})
            """)
    int insert(OperationLog log);

    /** 管理员分页查看（按时间倒序，limit 由调用方控制） */
    @Select("""
            SELECT id, user_id, username, action, target_type, target_id, detail, ip, created_at
            FROM operation_log
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<OperationLog> findRecent(@Param("limit") int limit);
}
