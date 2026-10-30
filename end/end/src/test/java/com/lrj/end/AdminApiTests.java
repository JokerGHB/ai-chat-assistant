package com.lrj.end;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端测试（测试角色：周鹏）。
 *
 * <p>这一组覆盖三件事：权限边界（谁能进管理端）、管理员能力（列表/看板/日志）、
 * 封禁的即时性（封禁是否立刻把人踢下线），以及一条特殊的契约测试——
 * 验证「已下线的接口」确实不存在了。
 */
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+",
        disabledReason = "未设置 DB_PASSWORD 环境变量（口令不入源码），无法连接数据库；设置后重新运行即可")
class AdminApiTests extends AbstractApiTests {

    /**
     * 权限边界：普通用户访问管理端必须 403。
     * 注意和「越权访问他人会话返回 404」的对比：管理端接口本身是公开存在的事实，
     * 所以用 403「没权限」；而用户会话是私有资源，用 404「不存在」避免暴露。
     */
    @Test
    @DisplayName("管理端 01 权限与能力：普通用户 403，管理员可查用户/看板/日志")
    void adminAccessControlAndCapabilities() throws Exception {
        // 普通用户访问管理端：403
        String userToken = registerAndLogin();
        assertEquals(403, call("GET", "/api/admin/users", userToken, null).statusCode());

        // 管理员可用：用户列表
        String adminToken = adminLogin();
        assertEquals(200, call("GET", "/api/admin/users", adminToken, null).statusCode());

        // 统计看板：三个统计字段都必须在（调用次数、成本、未解决数）
        HttpResponse<String> stats = call("GET", "/api/admin/stats/overview?range=7d", adminToken, null);
        assertEquals(200, stats.statusCode());
        assertTrue(stats.body().contains("callCount"), stats.body());
        assertTrue(stats.body().contains("totalCost"), stats.body());
        assertTrue(stats.body().contains("unresolvedCount"), stats.body());

        // 操作日志可查
        assertEquals(200, call("GET", "/api/admin/logs?limit=10", adminToken, null).statusCode());
    }

    /**
     * 契约测试：管理员重置密码已下线（产品决策——管理员代持用户口令属越权设计，
     * 响应里曾直接返回明文新口令）。
     * 接口删除后这里必须 404 —— 这条断言防止「文档删了、接口还在」或反向的回归。
     */
    @Test
    @DisplayName("管理端 02 重置密码接口已下线：404（契约保持）")
    void resetPasswordEndpointRemoved() throws Exception {
        String adminToken = adminLogin();
        HttpResponse<String> removed = call("POST", "/api/admin/users/1/reset-password", adminToken, null);
        assertEquals(404, removed.statusCode(), "重置密码接口已删除，应返回 404");
    }

    /**
     * 封禁的即时性：封禁后不等到令牌自然过期，而是立刻删除该用户全部令牌，
     * 所以旧令牌马上 401 —— 对被封账号是实时生效的。解封后应能重新登录。
     */
    @Test
    @DisplayName("管理端 03 封禁即时生效：旧令牌 401、登录 403，解封恢复；管理员不能封自己")
    void banUnbanForcesReloginAndSelfBanForbidden() throws Exception {
        String adminToken = adminLogin();

        String victim = randomName();
        register(victim, PASSWORD);
        HttpResponse<String> victimLogin = login(victim, PASSWORD);
        String victimToken = firstGroup(TOKEN, victimLogin.body());
        long victimId = Long.parseLong(firstGroup(ID, victimLogin.body()));

        // 封禁 → 旧令牌立即失效 → 禁止登录
        assertEquals(204, call("POST", "/api/admin/users/" + victimId + "/ban", adminToken, null).statusCode(),
                "封禁应返回 204");
        assertEquals(401, call("GET", "/api/auth/profile", victimToken, null).statusCode(),
                "封禁后旧令牌应立即失效");
        HttpResponse<String> bannedLogin = login(victim, PASSWORD);
        assertEquals(403, bannedLogin.statusCode(), "封禁账号登录应 403");

        // 解封 → 恢复登录
        assertEquals(204, call("POST", "/api/admin/users/" + victimId + "/unban", adminToken, null).statusCode(),
                "解封应返回 204");
        assertEquals(200, login(victim, PASSWORD).statusCode(), "解封后应能重新登录");

        // 自我保护：管理员不能封自己（否则一个误操作就把整个管理端锁死）
        assertEquals(400, call("POST", "/api/admin/users/1/ban", adminToken, null).statusCode(),
                "不允许操作管理员账号");
    }
}
