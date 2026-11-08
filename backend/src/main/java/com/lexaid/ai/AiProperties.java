package com.lexaid.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Shared configuration for chat and subjective grading. Keep credentials out of toString().
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String baseUrl = "https://api.deepseek.com";
    private String deepseekApiKey = "";
    private String apiKey = "";
    private String model = "deepseek-flash";
    private int maxTokens = 1200;
}
