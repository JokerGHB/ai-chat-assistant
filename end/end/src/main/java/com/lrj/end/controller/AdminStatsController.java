package com.lrj.end.controller;

import com.lrj.end.dto.StatsOverviewResponse;
import com.lrj.end.dto.TopQuestionView;
import com.lrj.end.dto.UnresolvedMessageView;
import com.lrj.end.service.AdminChatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员统计看板接口：总览 / 高频提问 / 未解决对话，全部从 chat_message 聚合。
 * range 支持 7d（近7天）/ 30d（近30天）/ all（全部，默认）。
 */
@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {
    private final AdminChatService adminChatService;

    public AdminStatsController(AdminChatService adminChatService) {
        this.adminChatService = adminChatService;
    }

    /** 总览：调用次数 / 总成本 / 未解决对话数 */
    @GetMapping("/overview")
    public StatsOverviewResponse overview(@RequestParam(defaultValue = "all") String range) {
        return adminChatService.overview(range);
    }

    /** 高频提问 Top N（默认 10 条） */
    @GetMapping("/top-questions")
    public List<TopQuestionView> topQuestions(@RequestParam(defaultValue = "all") String range,
                                              @RequestParam(defaultValue = "10") int limit) {
        return adminChatService.topQuestions(range, limit);
    }

    /** 未解决对话明细（默认最近 50 条） */
    @GetMapping("/unresolved")
    public List<UnresolvedMessageView> unresolved(@RequestParam(defaultValue = "all") String range,
                                                  @RequestParam(defaultValue = "50") int limit) {
        return adminChatService.unresolved(range, limit);
    }
}
