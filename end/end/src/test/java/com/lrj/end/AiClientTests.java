package com.lrj.end;

import com.lrj.end.exception.AiServiceException;
import com.lrj.end.service.DeepSeekClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型客户端单元测试（测试角色：周鹏）。
 *
 * <p>这是纯单元测试：不启动 Spring 容器、不连数据库、不发网络请求，
 * 所以没有数据库环境也能跑（没有 @EnabledIfEnvironmentVariable 守卫）。
 *
 * <p>测的是「未配置密钥时快速失败」这条降级规则：
 * DeepSeekClient 拿到空白密钥必须主动抛 503 异常并提示缺少 DEEPSEEK_API_KEY，
 * 而不是带着空密钥去请求线上接口。
 * 好处：错误在本地就被拦住，既不产生费用，报错信息也直接告诉运维少配了什么。
 */
class AiClientTests {

    @Test
    @DisplayName("单元测试：未配置密钥时快速失败（503 + 明确提示），不调用线上接口")
    void missingDeepSeekKeyReturnsConfigurationErrorWithoutCallingProvider() {
        // 构造器共 5 个参数：地址、密钥、模型名、输入/输出 token 单价（元/千 token），与主代码保持一致。
        // 密钥传空格 " "：业务上视为「未配置」，走 isBlank() 判空逻辑
        DeepSeekClient client = new DeepSeekClient(
                "https://api.deepseek.com", " ", "deepseek-flash",
                new BigDecimal("0.001"), new BigDecimal("0.002"));

        // 断言：调用时必须抛 AiServiceException —— 如果实现里忘了判空，
        // 请求会真的打到 api.deepseek.com，这里就会因为「没抛异常」而失败
        AiServiceException exception = assertThrows(
                AiServiceException.class,
                () -> client.generateReply(List.of()));

        // 语义断言一：状态码必须是 503（服务端配置问题，不是客户端的 4xx）
        assertEquals(503, exception.getStatus().value());
        // 语义断言二：提示语必须指名道姓说缺 DEEPSEEK_API_KEY，让运维一眼知道少配了什么
        assertTrue(exception.getMessage().contains("DEEPSEEK_API_KEY"));
    }
}
