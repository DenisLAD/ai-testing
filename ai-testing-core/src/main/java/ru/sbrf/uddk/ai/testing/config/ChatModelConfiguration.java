package ru.sbrf.uddk.ai.testing.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class ChatModelConfiguration {

    @Bean
    public ChatModel chatModel() {
        return new OpenAiChatModel(
                OpenAiApi.builder().baseUrl("http://localhost:1234").apiKey("").build(),
                OpenAiChatOptions.builder().build(),
                ToolCallingManager.builder().build(),
                RetryTemplate.defaultInstance(),
                ObservationRegistry.NOOP
        );
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
