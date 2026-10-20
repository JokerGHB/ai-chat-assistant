package com.lrj.end.controller;

import com.lrj.end.entity.OperationLog;
import com.lrj.end.service.OperationLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员操作日志查询接口。日志只读，不提供任何修改/删除入口（不可篡改）。
 */
@RestController
@RequestMapping("/api/admin/logs")
public class AdminLogController {
    private final OperationLogService logService;

    public AdminLogController(OperationLogService logService) {
        this.logService = logService;
    }

    /** 最近操作日志（默认 100 条，上限 500） */
    @GetMapping
    public List<OperationLog> listRecent(@RequestParam(defaultValue = "100") int limit) {
        return logService.listRecent(limit);
    }
}
