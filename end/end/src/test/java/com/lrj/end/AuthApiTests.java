package com.lrj.end;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认证与账号安全测试（测试角色：周鹏）。
 *
 * <p>这一组围绕「账号能不能被冒用」展开，覆盖四类问题：
 * 正常注册登录、非法输入拦截、令牌生命周期（登出/改名后立刻失效）、防账号枚举。
 */
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+",
        disabledReason = "未设置 DB_PASSWORD 环境变量（口令不入源码），无法连接数据库；设置后重新运行即可")
class AuthApiTests extends AbstractApiTests {

    @Test
    @DisplayName("认证 01 注册 → 登录 → 查询资料：200，限额与配置一致")
    void registerLoginAndProfile() throws Exception {
        String username = randomName();
        assertEquals(200, register(username, PASSWORD).statusCode());

        HttpResponse<String> login = login(username, PASSWORD);
        assertEquals(200, login.statusCode(), "登录应成功");
        String token = firstGroup(TOKEN, login.body());

        HttpResponse<String> profile = call("GET", "/api/auth/profile", token, null);
        assertEquals(200, profile.statusCode());
        assertTrue(profile.body().contains("\"username\":\"" + username + "\""), "资料应返回当前用户名");
        // 角色必须是 user：防止接口把内部字段（如管理员标记）错误地暴露出来
        assertTrue(profile.body().contains("\"role\":\"user\""));
        assertTrue(profile.body().contains("\"status\":\"active\""));
        // 剩余次数必须等于配置的 3：验证配置真的生效了，而不是代码里写死的默认值
        assertTrue(profile.body().contains("\"dailyLimit\":3"), "每日限额应与测试配置一致");
        assertTrue(profile.body().contains("\"remainingToday\":3"));
    }

    @Test
    @DisplayName("认证 02 重复用户名注册：400 + 用户名已被注册")
    void registerDuplicateUsernameReturnsBadRequest() throws Exception {
        String username = randomName();
        assertEquals(200, register(username, PASSWORD).statusCode());

        HttpResponse<String> again = register(username, PASSWORD);
        // 这里历史上踩过「文档写 409、实现返回 400」的坑（BUG-007），
        // 最终以实现为准修订了文档 —— 所以断言写 400，注释保留这段来龙去脉
        assertEquals(400, again.statusCode(), "重复用户名应返回 400（文档原写 409，按实现修订，见 BUG-007）");
        assertEquals("用户名已被注册", firstGroup(MESSAGE, again.body()));
    }

    @Test
    @DisplayName("认证 03 弱密码注册（纯数字 123456）：400 参数校验失败")
    void registerWeakPasswordReturnsBadRequest() throws Exception {
        HttpResponse<String> response = register(randomName(), "123456");
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("密码"), response.body());
    }

    /**
     * 安全要点：防账号枚举。
     * 「密码错误」和「用户不存在」必须返回同一个提示语 ——
     * 否则攻击者可以用提示语差异猜出哪些用户名是注册过的。
     */
    @Test
    @DisplayName("认证 04 错误密码登录：400 + 用户名或密码错误（防枚举）")
    void loginWithWrongPasswordReturnsBadRequest() throws Exception {
        String username = randomName();
        register(username, PASSWORD);

        HttpResponse<String> wrong = login(username, "wrong999");
        assertEquals(400, wrong.statusCode(), "登录失败返回 400（文档原写 401，按实现修订，见 BUG-008）");
        assertEquals("用户名或密码错误", firstGroup(MESSAGE, wrong.body()));

        // 关键断言：换个不存在的用户名，提示语必须一模一样
        HttpResponse<String> missing = login("no_such_" + randomName(), PASSWORD);
        assertEquals(400, missing.statusCode(), "用户不存在与密码错误提示必须一致，防账号枚举");
        assertEquals("用户名或密码错误", firstGroup(MESSAGE, missing.body()));
    }

    @Test
    @DisplayName("认证 05 未携带/伪造令牌访问受保护接口：401")
    void accessProtectedApiWithoutOrWithInvalidTokenReturnsUnauthorized() throws Exception {
        HttpResponse<String> without = call("GET", "/api/sessions", null, null);
        assertEquals(401, without.statusCode());

        HttpResponse<String> invalid = call("GET", "/api/sessions", "not-a-real-token", null);
        assertEquals(401, invalid.statusCode());
    }

    /**
     * 改用户名是新加的功能，这条把它的安全语义一次测全：
     * ① 改名 204，旧令牌立即失效（防止「改名了但旧会话还挂着」的悬空登录态）；
     * ② 新名字能登录；
     * ③ 与他人重名 400；
     * ④ 「单字符 + 空格」必须被拦 —— 校验要先去空格再做长度判断，
     *    否则 "a " 长度为 2 能通过校验，入库 trim 后只剩 1 个字符（BUG-004 的绕过手法）；
     * ⑤ 与当前同名 204 幂等，且不吊销令牌；
     * ⑥ 改名必须写操作日志（CHANGE_USERNAME），出问题可追溯。
     */
    @Test
    @DisplayName("认证 06 用户自助改用户名：204 + 旧令牌失效 + 新名可登录 + 校验不可绕过（Q01/BUG-004 回归）")
    void changeUsernameFlow() throws Exception {
        String token = registerAndLogin();
        String newName = "renamed_" + randomName().substring(1);

        // ① 正常改名：204，随后旧令牌必须失效（强制重新登录）
        assertEquals(204, call("PATCH", "/api/auth/username", token, json("username", newName)).statusCode(),
                "改用户名应返回 204（前版无此接口返回 404）");
        assertEquals(401, call("GET", "/api/auth/profile", token, null).statusCode(),
                "改名后旧令牌应立即失效");

        // ② 新用户名可登录
        HttpResponse<String> relogin = login(newName, PASSWORD);
        assertEquals(200, relogin.statusCode(), "新用户名应可登录");
        String newToken = firstGroup(TOKEN, relogin.body());

        // ③ 与他人重名：400
        String otherName = randomName();
        register(otherName, PASSWORD);
        assertEquals(400, call("PATCH", "/api/auth/username", newToken, json("username", otherName)).statusCode(),
                "改名与他人重名应返回 400");

        // ④ 「单字符 + 空格」：先去空格再校验，长度 1 必须拦截（BUG-004 曾被绕过存成 1 个字符）
        assertEquals(400, call("PATCH", "/api/auth/username", newToken, json("username", "a ")).statusCode(),
                "去空格后长度不足 2 应被拦截");

        // ⑤ 与当前同名：204 幂等，不吊销令牌
        assertEquals(204, call("PATCH", "/api/auth/username", newToken, json("username", newName)).statusCode(),
                "与当前同名应幂等返回 204");
        assertEquals(200, call("GET", "/api/auth/profile", newToken, null).statusCode(),
                "同名 204 不应吊销令牌");

        // ⑥ 改名动作应写入操作日志（管理员可查到 CHANGE_USERNAME）
        String adminToken = adminLogin();
        HttpResponse<String> logs = call("GET", "/api/admin/logs?limit=50", adminToken, null);
        assertEquals(200, logs.statusCode());
        assertTrue(logs.body().contains("CHANGE_USERNAME"), "改名必须留痕（操作日志）");
    }

    /** 登出语义：不是「前端忘了 token」，而是服务端把令牌作废 —— 所以旧 token 再用必须 401。 */
    @Test
    @DisplayName("认证 07 登出后令牌立即失效：401")
    void logoutInvalidatesToken() throws Exception {
        String token = registerAndLogin();

        HttpResponse<String> logout = call("POST", "/api/auth/logout", token, null);
        assertTrue(logout.statusCode() == 200 || logout.statusCode() == 204, "登出应成功");

        assertEquals(401, call("GET", "/api/auth/profile", token, null).statusCode(),
                "登出后令牌应立即失效");
    }
}
