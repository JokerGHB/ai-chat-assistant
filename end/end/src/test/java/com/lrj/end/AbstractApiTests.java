package com.lrj.end;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接口集成测试的公共基类（抽象类，JUnit 不会把它当成测试执行）。
 *
 * <p><b>为什么这样设计（讲解用）：</b>
 * <ul>
 *   <li>用「真实容器 + HTTP 请求」而不是 MockMvc：请求走完整的 过滤器 → 控制器 → 服务 → 数据库 链路，
 *       和用户实际使用的路径一致，测出来的问题就是线上会出的问题；</li>
 *   <li>用 JDK 自带的 HttpClient：不依赖 MockMvc / JSON 库，不受 Spring Boot 4 测试依赖拆分影响，
 *       {@code mvn test} 直接能跑；</li>
 *   <li>口令通过环境变量注入而不是写死在代码里：前版本曾把数据库口令硬编码在源码里，
 *       一旦提交代码就等于泄露（这就是我们登记的 BUG-010），测试代码不能重蹈覆辙；</li>
 *   <li>随机端口：容器每次起在空闲端口上，和本机已占用的 8080/8081 互不干扰。</li>
 * </ul>
 *
 * <p>三个子类（AuthApiTests / SessionMessageApiTests / AdminApiTests）继承本类后共享同一套
 * 容器配置，Spring 会复用同一个上下文 —— 分类的同时不牺牲执行速度。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        // 数据源强制用空密码 + 默认库（@EnabledIfEnvironmentVariable 只做守卫，真正的连接配置在这里覆盖）
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/ai_chat_local?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Taipei&useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=root",
        "spring.datasource.password=",
        // 密钥置空：所有用例都不调用线上大模型 —— 不花钱、不依赖外网，
        // 同时正好覆盖「AI 不可用」这条降级路径
        "deepseek.api-key=",
        // 把每日限额压到 3、敏感词配一个词：让限额和拦截用例几秒钟就能复现，
        // 否则按生产默认 100 次根本没法测
        "chat.sensitive-words=赌博",
        "chat.daily-limit=3"
})
// 注意：类级守卫需要同时标在各个子类上（JUnit 的条件注解不保证从父类继承），
// 作用是：未设置 DB_PASSWORD 时整组测试跳过（显示 skipped），而不是报一堆连接错误
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
abstract class AbstractApiTests {

    /** 测试账号统一使用这个密码，随机用户名见 randomName()。 */
    protected static final String PASSWORD = "abc123";
    /** 用正则从 JSON 响应里取字段值：token、用户 id、message 提示语。不引 JSON 库，保持零依赖。 */
    protected static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
    protected static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    protected static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");

    /** 容器实际监听的随机端口，由 Spring 注入。 */
    @LocalServerPort
    protected int port;

    protected final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 统一的请求入口：拼 URL、按需加 Authorization 头、发请求。
     * token 传 null 表示「故意不带令牌」，用于测 401 场景。
     */
    protected HttpResponse<String> call(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20));
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    protected HttpResponse<String> register(String username, String password) throws Exception {
        return call("POST", "/api/auth/register", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    protected HttpResponse<String> login(String username, String password) throws Exception {
        return call("POST", "/api/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    /** 注册 + 登录一步到位，返回令牌 —— 每条用例都用全新随机用户，互不干扰、可重复执行。 */
    protected String registerAndLogin() throws Exception {
        String username = randomName();
        assertEquals(200, register(username, PASSWORD).statusCode());
        HttpResponse<String> login = login(username, PASSWORD);
        assertEquals(200, login.statusCode());
        return firstGroup(TOKEN, login.body());
    }

    /** 管理员账号由系统启动时自动创建，初始口令默认 Admin@123（可用环境变量覆盖）。 */
    protected String adminLogin() throws Exception {
        HttpResponse<String> login = login("admin", "Admin@123");
        assertEquals(200, login.statusCode(), "初始管理员账号应可用 admin / Admin@123");
        return firstGroup(TOKEN, login.body());
    }

    protected long createSession(String token) throws Exception {
        HttpResponse<String> response = call("POST", "/api/sessions", token, "{}");
        assertEquals(201, response.statusCode(), "创建会话应返回 201");
        return Long.parseLong(firstGroup(ID, response.body()));
    }

    protected String json(String field, String value) {
        return "{\"" + field + "\":\"" + value + "\"}";
    }

    /** 从 JSON 响应里取第一个匹配组的值；匹配不到就报错并附上原始响应，方便定位。 */
    protected String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        assertTrue(matcher.find(), "未在响应中匹配到 " + pattern.pattern() + "，响应=" + text);
        return matcher.group(1);
    }

    /** 数某个标记在文本里出现的次数 —— 并发用例靠它数「实际落库了几条消息」。 */
    protected int countOccurrences(String text, String marker) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(marker, index)) >= 0) {
            count++;
            index += marker.length();
        }
        return count;
    }

    /** 随机用户名：t + 10 位十六进制。每次执行都不同，测试不依赖固定数据，可无限重复。 */
    protected String randomName() {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
