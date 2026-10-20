package com.lrj.end.service;

import com.lrj.end.dto.LoginResponse;
import com.lrj.end.dto.UserProfileResponse;
import com.lrj.end.entity.LoginToken;
import com.lrj.end.entity.SysUser;
import com.lrj.end.exception.ForbiddenException;
import com.lrj.end.exception.UnauthorizedException;
import com.lrj.end.mapper.ChatMessageMapper;
import com.lrj.end.mapper.LoginTokenMapper;
import com.lrj.end.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 认证与账号服务：注册 / 登录 / 登出 / 改密 / 令牌校验。
 *
 * 密码安全：所有密码只存 BCrypt 哈希（BCryptPasswordEncoder，自带随机盐、成本因子 10），
 * 明文只存在于请求内存中，绝不入库、不写日志。
 *
 * 令牌模型：登录成功签发 64 位十六进制随机串存入 login_token 表；
 * 每次请求校验并滑动刷新 lastActiveAt，闲置超时（默认 30 分钟）自动失效。
 * 选用服务端令牌而非 JWT，是为了封禁/改密后能立刻强制下线。
 */
@Service
public class AuthService {
    private final SysUserMapper userMapper;
    private final LoginTokenMapper tokenMapper;
    private final OperationLogService logService;
    private final ChatMessageMapper messageMapper;
    /** BCrypt 编码器：无状态线程安全，作为单例字段复用 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    /** 令牌闲置超时分钟数，来自 auth.token-idle-minutes 配置 */
    private final long idleMinutes;
    /** 每日对话次数上限，来自 chat.daily-limit 配置 */
    private final int dailyLimit;

    public AuthService(SysUserMapper userMapper,
                       LoginTokenMapper tokenMapper,
                       OperationLogService logService,
                       ChatMessageMapper messageMapper,
                       @Value("${auth.token-idle-minutes:30}") long idleMinutes,
                       @Value("${chat.daily-limit:100}") int dailyLimit) {
        this.userMapper = userMapper;
        this.tokenMapper = tokenMapper;
        this.logService = logService;
        this.messageMapper = messageMapper;
        this.idleMinutes = idleMinutes;
        this.dailyLimit = dailyLimit;
    }

    /**
     * 注册：用户名唯一性校验 + BCrypt 加密入库。
     * 密码复杂度（6-12 位且含字母数字）已在 RegisterRequest 用 @Pattern 校验。
     */
    @Transactional
    public UserProfileResponse register(String username, String rawPassword, String ip) {
        String name = username.trim();
        if (userMapper.findByUsername(name).isPresent()) {
            throw new IllegalArgumentException("用户名已被注册");
        }
        SysUser user = new SysUser();
        user.setUsername(name);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole("user");
        user.setStatus("active");
        userMapper.insert(user);
        logService.log(user.getId(), name, "REGISTER", "用户注册", ip);
        return UserProfileResponse.from(user);
    }

    /**
     * 登录：校验密码 → 校验封禁状态 → 签发令牌。
     * 用户名不存在和密码错误返回同样的提示，避免暴露账号是否存在（防账号枚举）。
     */
    @Transactional
    public LoginResponse login(String username, String rawPassword, String ip) {
        SysUser user = userMapper.findByUsername(username.trim())
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if ("banned".equals(user.getStatus())) {
            // 封禁是业务规则而非认证失败，返回 403 让前端给出明确提示
            throw new ForbiddenException("账号已被封禁，请联系管理员");
        }
        LoginToken token = new LoginToken();
        token.setToken(generateToken());
        token.setUserId(user.getId());
        tokenMapper.insert(token);
        logService.log(user, "LOGIN", null, null, "登录成功", ip);
        return new LoginResponse(token.getToken(), UserProfileResponse.from(user));
    }

    /** 登出：删除令牌记录，立即失效 */
    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            tokenMapper.deleteByToken(token);
        }
    }

    /** 查看个人资料（附带当日对话次数使用情况，供前端展示剩余次数） */
    @Transactional(readOnly = true)
    public UserProfileResponse profile(Long userId) {
        SysUser user = requireUser(userId);
        int usedToday = messageMapper.countUserMessagesToday(userId);
        return UserProfileResponse.from(user, dailyLimit, usedToday);
    }

    /**
     * 修改密码：验证旧密码 → 更新哈希 → 删除该用户全部令牌（所有设备强制重新登录）。
     */
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword, String ip) {
        SysUser user = requireUser(userId);
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("旧密码不正确");
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
        tokenMapper.deleteByUserId(userId);
        logService.log(user, "CHANGE_PASSWORD", null, null, "修改登录密码", ip);
    }

    /**
     * 请求级令牌校验（AuthInterceptor 每个受保护请求都会调用）：
     *  1. 令牌不存在 → 401
     *  2. 闲置超时 → 删令牌 + 401
     *  3. 账号已被封禁 → 删令牌 + 401（封禁立即生效，无需等超时）
     *  4. 一切正常 → 滑动刷新活动时间，返回当前用户
     */
    @Transactional
    public SysUser authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("未登录或登录已过期");
        }
        LoginToken loginToken = tokenMapper.findByToken(token)
                .orElseThrow(() -> new UnauthorizedException("未登录或登录已过期"));
        if (loginToken.getLastActiveAt().isBefore(LocalDateTime.now().minusMinutes(idleMinutes))) {
            tokenMapper.deleteByToken(token);
            throw new UnauthorizedException("长时间未操作，登录已过期，请重新登录");
        }
        SysUser user = requireUser(loginToken.getUserId());
        if ("banned".equals(user.getStatus())) {
            tokenMapper.deleteByToken(token);
            throw new UnauthorizedException("账号已被封禁，请联系管理员");
        }
        tokenMapper.touch(token);
        return user;
    }

    /** 生成 64 位十六进制随机令牌（32 字节安全随机数） */
    private String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private SysUser requireUser(Long userId) {
        return userMapper.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("账号不存在"));
    }

    /** 供管理员模块重置密码时复用 BCrypt（第 5 步使用） */
    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
