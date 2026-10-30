package com.lrj.end;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 启动冒烟测试（测试角色：周鹏）。
 *
 * <p>本类只回答一个问题：整个 Spring 容器能不能正常启动。
 * 容器启动会依次装配数据源、MyBatis、控制器和服务 —— 任何一环配置错误
 * （数据库连不上、配置项写错、依赖缺失）都会在这里第一时间暴露，
 * 而不是等到业务测试时抛出一堆看不懂的连锁异常。
 *
 * <p>在套件里它排第二（套件入口是 {@link EndApplicationTests}）：
 * 单元测试之后、所有接口测试之前 —— 容器起不来，后面的接口测试没有意义。
 *
 * <p>运行约定：需要本地 MySQL，口令经环境变量 {@code DB_PASSWORD} 注入、不写入源码（BUG-010 教训）；
 * 未设置时本类整体跳过（显示 skipped），不会误报失败。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/ai_chat_local?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Taipei&useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=root",
        "spring.datasource.password="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+",
        disabledReason = "未设置 DB_PASSWORD 环境变量（口令不入源码），无法连接数据库；设置后重新运行即可")
class StartupSmokeTests {

    /**
     * 冒烟测试：方法体为空 —— 容器起得来就过，起不来就红。
     * 它是所有其他测试的前置条件：这个过不了，后面的接口测试没有意义。
     */
    @Test
    @DisplayName("启动冒烟：Spring 容器装配成功（数据源、MyBatis、全部 Bean）")
    void contextLoads() {
    }
}
