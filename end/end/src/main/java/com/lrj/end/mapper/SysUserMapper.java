package com.lrj.end.mapper;

import com.lrj.end.entity.SysUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/** 用户表访问。密码字段只写不查用于展示，登录比对在 Service 层用 BCrypt 完成。 */
public interface SysUserMapper {

    @Select("""
            SELECT id, username, password_hash, role, status, created_at, updated_at
            FROM sys_user WHERE id = #{id}
            """)
    Optional<SysUser> findById(@Param("id") Long id);

    @Select("""
            SELECT id, username, password_hash, role, status, created_at, updated_at
            FROM sys_user WHERE username = #{username}
            """)
    Optional<SysUser> findByUsername(@Param("username") String username);

    /** 管理员查看全部用户（第 5 步使用） */
    @Select("""
            SELECT id, username, password_hash, role, status, created_at, updated_at
            FROM sys_user ORDER BY id ASC
            """)
    List<SysUser> findAll();

    @Insert("""
            INSERT INTO sys_user (username, password_hash, role, status)
            VALUES (#{username}, #{passwordHash}, #{role}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysUser user);

    @Update("UPDATE sys_user SET password_hash = #{passwordHash} WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    /** 封禁/解封（第 5 步管理员功能使用） */
    @Update("UPDATE sys_user SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
