package com.lrj.end;

import com.lrj.end.exception.AiServiceException;
import com.lrj.end.service.DeepSeekClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.boot.test.context.SpringBootTest;

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
        DeepSeekClient client = new DeepSeekClient(
                "https://api.deepseek.com", " ", "deepseek-flash");

        AiServiceException exception = assertThrows(
                AiServiceException.class,
                () -> client.generateReply(List.of()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        assertTrue(exception.getMessage().contains("DEEPSEEK_API_KEY"));
    }

}
