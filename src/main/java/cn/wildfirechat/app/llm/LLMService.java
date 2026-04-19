package cn.wildfirechat.app.llm;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class LLMService {
    private static final Logger LOG = LoggerFactory.getLogger(LLMService.class);

    @Value("${llm.api.url:}")
    private String apiUrl;

    @Value("${llm.api.key:}")
    private String apiKey;

    @Value("${llm.api.model:}")
    private String model;

    @Value("${llm.api.max-context-length:200000}")
    private int maxContextLength;

    private final RestTemplate restTemplate = new RestTemplate();
    private final Gson gson = new Gson();

    public String chat(String systemPrompt, String userPrompt) {
        if (apiUrl == null || apiUrl.isEmpty()) {
            LOG.warn("LLM API URL not configured, skip chat completion");
            return null;
        }

        try {
            List<ChatCompletionRequest.Message> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                messages.add(new ChatCompletionRequest.Message("system", systemPrompt));
            }
            messages.add(new ChatCompletionRequest.Message("user", userPrompt));

            ChatCompletionRequest requestBody = new ChatCompletionRequest(model, messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.set("Authorization", "Bearer " + apiKey);
            }

            HttpEntity<ChatCompletionRequest> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCodeValue() == 200 && response.getBody() != null) {
                ChatCompletionResponse completionResponse = gson.fromJson(response.getBody(), ChatCompletionResponse.class);
                if (completionResponse != null && completionResponse.getChoices() != null && !completionResponse.getChoices().isEmpty()) {
                    String content = completionResponse.getChoices().get(0).getMessage().getContent();
                    LOG.info("LLM response received, content length={}", content != null ? content.length() : 0);
                    return content;
                }
            } else {
                LOG.error("LLM API returned status code: {}", response.getStatusCodeValue());
            }
        } catch (Exception e) {
            LOG.error("Failed to call LLM API", e);
        }
        return null;
    }

    /**
     * 估算文本的 token 数量（保守估算）
     * 中文字符约 1 token，英文/数字/标点约 0.5 token
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int tokens = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x4e00 && c <= 0x9fff) {
                tokens += 1;
            } else if (Character.isLetter(c)) {
                tokens += 1;
            } else {
                tokens += 1;
            }
        }
        return tokens;
    }

    /**
     * 判断 systemPrompt + userPrompt 是否超过上下文长度限制
     */
    public boolean isExceedContext(String systemPrompt, String userPrompt) {
        int totalTokens = estimateTokens(systemPrompt) + estimateTokens(userPrompt);
        return totalTokens > maxContextLength;
    }

    public int getMaxContextLength() {
        return maxContextLength;
    }
}
