package com.lrj.end.service;

import com.lrj.end.entity.OperationLog;
import com.lrj.end.entity.SysUser;
import com.lrj.end.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 操作日志服务。只提供写入能力：
 *  - 没有 update/delete 方法，日志一旦写入无法通过业务代码篡改（需求 4.3-2）。
 *  - 使用 REQUIRES_NEW 独立事务：业务操作即使后续失败回滚，日志也已经留下，
 *    例如"登录密码错误后触发封禁"这类场景日志不能丢。
 */
@Service
public class OperationLogService {
    private final OperationLogMapper logMapper;

    public OperationLogService(OperationLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    /** 记录系统任务日志（无操作人，如定时清理） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSystem(String action, String targetType, String targetId, String detail) {
        write(null, "system", action, targetType, targetId, detail, null);
    }

    /** 记录用户操作日志 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(SysUser user, String action, String targetType, String targetId, String detail, String ip) {
        write(user.getId(), user.getUsername(), action, targetType, targetId, detail, ip);
    }

    /** 记录尚未拿到用户实体时的日志（如注册，用户刚创建） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String username, String action, String detail, String ip) {
        write(userId, username, action, null, null, detail, ip);
    }

    private void write(Long userId, String username, String action,
                       String targetType, String targetId, String detail, String ip) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setIp(ip);
        logMapper.insert(log);
    }

    /** 管理员查看最近日志（只读；limit 上限 500 防止一次拉取过多） */
    @Transactional(readOnly = true)
    public java.util.List<OperationLog> listRecent(int limit) {
        return logMapper.findRecent(Math.min(Math.max(limit, 1), 500));
    }
}
