package com.lrj.end.service;

import com.lrj.end.dto.UserProfileResponse;
import com.lrj.end.entity.SysUser;
import com.lrj.end.exception.ResourceNotFoundException;
import com.lrj.end.mapper.LoginTokenMapper;
import com.lrj.end.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

/**
 * 管理员用户管理：列表 / 封禁 / 解封 / 重置密码。
 * 所有操作都写操作日志（BAN_USER / UNBAN_USER / RESET_PASSWORD）。
 * 安全约束：不允许对 admin 角色账号执行封禁/重置，防止管理员被锁死。
 */
@Service
public class AdminUserService {
    /** 重置密码生成的随机密码字符池（满足"字母+数字"复杂度要求） */
    private static final String PASSWORD_CHARS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final SysUserMapper userMapper;
    private final LoginTokenMapper tokenMapper;
    private final AuthService authService;
    private final OperationLogService logService;

    public AdminUserService(SysUserMapper userMapper,
                            LoginTokenMapper tokenMapper,
                            AuthService authService,
                            OperationLogService logService) {
        this.userMapper = userMapper;
        this.tokenMapper = tokenMapper;
        this.authService = authService;
        this.logService = logService;
    }

    /** 全部用户列表（转成 UserProfileResponse，绝不暴露 passwordHash） */
    @Transactional(readOnly = true)
    public List<UserProfileResponse> listUsers() {
        return userMapper.findAll().stream().map(UserProfileResponse::from).toList();
    }

    /**
     * 封禁：置 status=banned，并删除该用户全部令牌（立即强制下线，
     * 不等 30 分钟闲置超时）。
     */
    @Transactional
    public void banUser(SysUser admin, Long targetUserId, String ip) {
        SysUser target = requireManageableUser(targetUserId);
        userMapper.updateStatus(targetUserId, "banned");
        tokenMapper.deleteByUserId(targetUserId);
        logService.log(admin, "BAN_USER", "user", String.valueOf(targetUserId),
                "封禁用户：" + target.getUsername(), ip);
    }

    /** 解封：恢复 status=active */
    @Transactional
    public void unbanUser(SysUser admin, Long targetUserId, String ip) {
        SysUser target = requireManageableUser(targetUserId);
        userMapper.updateStatus(targetUserId, "active");
        logService.log(admin, "UNBAN_USER", "user", String.valueOf(targetUserId),
                "解封用户：" + target.getUsername(), ip);
    }

    /**
     * 重置密码：生成 10 位随机密码（含字母数字），BCrypt 入库，
     * 删除该用户全部令牌强制重新登录；明文只通过接口返回给管理员一次。
     */
    @Transactional
    public String resetPassword(SysUser admin, Long targetUserId, String ip) {
        SysUser target = requireManageableUser(targetUserId);
        String newPassword = generateRandomPassword();
        userMapper.updatePassword(targetUserId, authService.encodePassword(newPassword));
        tokenMapper.deleteByUserId(targetUserId);
        logService.log(admin, "RESET_PASSWORD", "user", String.valueOf(targetUserId),
                "重置用户密码：" + target.getUsername(), ip);
        return newPassword;
    }

    /** 校验目标用户存在且不是 admin（admin 账号不允许被封禁/重置，防锁死） */
    private SysUser requireManageableUser(Long targetUserId) {
        SysUser target = userMapper.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到用户：" + targetUserId));
        if ("admin".equals(target.getRole())) {
            throw new IllegalArgumentException("不能对管理员账号执行该操作");
        }
        return target;
    }

    /** 生成 10 位随机密码：首位字母 + 次位数字，保证满足复杂度正则 */
    private String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        String letters = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ";
        String digits = "23456789";
        StringBuilder sb = new StringBuilder(10);
        sb.append(letters.charAt(random.nextInt(letters.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        for (int i = 2; i < 10; i++) {
            sb.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
