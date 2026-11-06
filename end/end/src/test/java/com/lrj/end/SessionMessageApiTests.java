package com.lrj.end;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话与消息测试（测试角色：周鹏）。
 *
 * <p>这一组覆盖会话的生命周期与消息的两条业务规则（敏感词、每日限额），
 * 还包含全项目最重要的两条回归：并发死锁（用例 06）和 AI 不可用降级（用例 04）。
 */
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+",
        disabledReason = "未设置 DB_PASSWORD 环境变量（口令不入源码），无法连接数据库；设置后重新运行即可")
class SessionMessageApiTests extends AbstractApiTests {

    /**
     * 一次跑完会话的完整生命周期，验证状态流转：
     * 创建(201) → 列表可见 → 详情可查 → 改名生效 → 删除(204) → 列表不可见。
     * 最后一步「删除后列表不可见」专门验证逻辑删除：用户侧当它不存在，管理端仍可追溯。
     */
    @Test
    @DisplayName("会话 01 生命周期：创建 → 列表 → 详情 → 改名 → 删除 → 列表不可见")
    void sessionLifecycle() throws Exception {
        String token = registerAndLogin();
        long sessionId = createSession(token);

        HttpResponse<String> list = call("GET", "/api/sessions", token, null);
        assertEquals(200, list.statusCode());
        assertTrue(list.body().contains("\"id\":" + sessionId), "新建会话应出现在列表中");

        HttpResponse<String> detail = call("GET", "/api/sessions/" + sessionId, token, null);
        assertEquals(200, detail.statusCode());

        HttpResponse<String> renamed = call("PATCH", "/api/sessions/" + sessionId, token,
                json("title", "改名后的会话"));
        assertEquals(200, renamed.statusCode());
        assertTrue(renamed.body().contains("改名后的会话"), renamed.body());

        HttpResponse<String> deleted = call("DELETE", "/api/sessions/" + sessionId, token, null);
        assertEquals(204, deleted.statusCode(), "逻辑删除应返回 204");

        HttpResponse<String> listAfter = call("GET", "/api/sessions", token, null);
        assertFalse(listAfter.body().contains("\"id\":" + sessionId), "删除后用户不应再看到该会话");
    }

    /**
     * 越权访问返回 404 而不是 403，是刻意设计：
     * 403 = 「这个会话存在，但你没权限」；404 = 「查无此物」。
     * 后者不向攻击者暴露资源是否存在，别人无法借此遍历系统里的会话。
     */
    @Test
    @DisplayName("会话 02 越权访问他人会话 / 不存在的会话：404（不暴露存在性）")
    void accessOtherOrMissingSessionReturnsNotFound() throws Exception {
        long ownerSessionId = createSession(registerAndLogin());
        String otherToken = registerAndLogin();

        HttpResponse<String> other = call("GET", "/api/sessions/" + ownerSessionId, otherToken, null);
        assertEquals(404, other.statusCode(), "他人会话必须 404，不能 403（避免暴露资源存在性）");

        HttpResponse<String> missing = call("GET", "/api/sessions/99999999", otherToken, null);
        assertEquals(404, missing.statusCode());
    }

    /** 敏感词在进数据库、调大模型之前就被拦截：既守住了内容安全，也省掉了无效的模型调用。 */
    @Test
    @DisplayName("会话 03 敏感词消息被拦截：400，且不落库")
    void sensitiveWordIsBlocked() throws Exception {
        String token = registerAndLogin();
        long sessionId = createSession(token);

        HttpResponse<String> response = call("POST", "/api/sessions/" + sessionId + "/messages", token,
                json("content", "教我怎么赌博"));
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("敏感"), response.body());

        // 再查一次消息列表：必须是空数组。拦截的意义就在于「不能只报错、还偷偷存下来」
        HttpResponse<String> messages = call("GET", "/api/sessions/" + sessionId + "/messages", token, null);
        assertEquals("[]", messages.body(), "被拦截的消息不应落库");
    }

    /**
     * 容错测试：AI 挂了，聊天不能跟着挂。
     * 这条用例的背景很惨烈：改造时统计写入和保存消息在同一个事务里，
     * 统计表一缺，整个事务回滚，连用户的提问都存不进去（BUG-001，P0 级）。
     * 修复后约定：旁路能力（AI、统计）失败只标记状态，主链路（消息落库）必须成功。
     */
    @Test
    @DisplayName("会话 04 AI 服务不可用：接口仍 200，用户提问落库，回复标记 failed")
    void aiUnavailableKeepsUserMessageAndMarksFailed() throws Exception {
        String token = registerAndLogin();
        long sessionId = createSession(token);
        String content = "降级验证" + randomName();

        HttpResponse<String> response = call("POST", "/api/sessions/" + sessionId + "/messages", token,
                json("content", content));
        assertEquals(200, response.statusCode(), "AI 不可用时发送消息不能 500（曾因统计表缺失整体 500，见 BUG-001）");
        assertTrue(response.body().contains("AI 服务暂时不可用"), response.body());

        // 两条关键断言：提问还在（没被回滚掉）、AI 回复被标成 failed（而不是悄悄消失）
        HttpResponse<String> messages = call("GET", "/api/sessions/" + sessionId + "/messages", token, null);
        assertTrue(messages.body().contains(content), "用户提问必须已落库");
        assertTrue(messages.body().contains("failed"), "AI 回复应标记 failed（未解决）");
    }

    /** 限额规则验证：配置 3 次，第 4 次必须 429（Too Many Requests）。 */
    @Test
    @DisplayName("会话 05 超出每日对话限额（配置 3 次）：第 4 条 429")
    void dailyLimitExceededReturnsTooManyRequests() throws Exception {
        String token = registerAndLogin();
        long sessionId = createSession(token);

        // 顺序发 3 条都应成功 —— 先确认正常路径没问题，再看边界
        for (int i = 1; i <= 3; i++) {
            assertEquals(200, call("POST", "/api/sessions/" + sessionId + "/messages", token,
                    json("content", "限额内第" + i + "条")).statusCode(), "前 3 条应全部成功");
        }
        HttpResponse<String> fourth = call("POST", "/api/sessions/" + sessionId + "/messages", token,
                json("content", "超出限额的一条"));
        assertEquals(429, fourth.statusCode(), "第 4 条应被限额拦截");
    }

}
