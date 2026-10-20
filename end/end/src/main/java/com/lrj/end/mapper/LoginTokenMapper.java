package com.lrj.end.mapper;

import com.lrj.end.entity.LoginToken;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/** 登录令牌访问。过期/封禁/登出都通过删除记录实现，简单可靠。 */
public interface LoginTokenMapper {

    @Insert("""
            INSERT INTO login_token (token, user_id)
            VALUES (#{token}, #{userId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LoginToken token);

    @Select("""
            SELECT id, token, user_id, created_at, last_active_at
            FROM login_token WHERE token = #{token}
            """)
    Optional<LoginToken> findByToken(@Param("token") String token);

    /** 滑动过期：每次有效请求都刷新最近活动时间 */
    @Update("UPDATE login_token SET last_active_at = CURRENT_TIMESTAMP(3) WHERE token = #{token}")
    int touch(@Param("token") String token);

    @Delete("DELETE FROM login_token WHERE token = #{token}")
    int deleteByToken(@Param("token") String token);

    /** 封禁、改密后强制该用户所有设备下线 */
    @Delete("DELETE FROM login_token WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
