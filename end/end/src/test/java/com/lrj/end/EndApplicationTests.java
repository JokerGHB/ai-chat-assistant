package com.lrj.end;

import com.lrj.end.exception.AiServiceException;
import com.lrj.end.service.DeepSeekClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class EndApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void missingDeepSeekKeyReturnsConfigurationErrorWithoutCallingProvider() {
        // 构造器共 5 个参数：地址、密钥、模型名、输入/输出 token 单价（元/千 token），与主代码保持一致
        DeepSeekClient client = new DeepSeekClient(
                "https://api.deepseek.com", " ", "deepseek-flash",
                new BigDecimal("0.001"), new BigDecimal("0.002"));

        AiServiceException exception = assertThrows(
                AiServiceException.class,
                () -> client.generateReply(List.of()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        assertTrue(exception.getMessage().contains("DEEPSEEK_API_KEY"));
    }

}
