package com.lrj.end.task;

import com.lrj.end.mapper.ChatSessionMapper;
import com.lrj.end.service.OperationLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 过期会话定时清理任务。
 * 每日凌晨 3 点执行：物理删除 last_active_at 距今超过 90 天的会话，
 * 会话下的消息靠外键 ON DELETE CASCADE 一并删除。
 * 清理结果写入 operation_log（操作人为 system），供管理员审计。
 */
@Component
public class SessionPurgeTask {
    private static final Logger log = LoggerFactory.getLogger(SessionPurgeTask.class);
    /** 过期阈值：90 天无活动 */
    private static final int EXPIRE_DAYS = 90;

    private final ChatSessionMapper sessionMapper;
    private final OperationLogService logService;

    public SessionPurgeTask(ChatSessionMapper sessionMapper, OperationLogService logService) {
        this.sessionMapper = sessionMapper;
        this.logService = logService;
    }

    /** cron：秒 分 时 日 月 周 → 每天 03:00:00 执行（单条 DELETE 自带事务，无需 @Transactional） */
    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpiredSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(EXPIRE_DAYS);
        int deleted = sessionMapper.purgeExpiredBefore(cutoff);
        log.info("过期会话清理完成：删除 {} 个 {} 天无活动会话", deleted, EXPIRE_DAYS);
        // 系统任务日志：操作人为 NULL/system，记录清理数量与截止时间
        logService.logSystem("PURGE_SESSION", "session", null,
                "定时清理：物理删除 " + deleted +  "个 " + EXPIRE_DAYS + " 天无活动会话（cutoff=" + cutoff + "）");
    }
}
