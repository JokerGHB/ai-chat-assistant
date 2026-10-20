package com.lrj.end.dto;

import java.math.BigDecimal;

/**
 * 统计看板总览：调用次数 / 总成本 / 未解决对话数。
 */
public record StatsOverviewResponse(long callCount, BigDecimal totalCost, long unresolvedCount) {
}
