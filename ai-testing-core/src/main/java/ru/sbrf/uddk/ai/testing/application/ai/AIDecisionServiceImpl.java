package ru.sbrf.uddk.ai.testing.application.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import ru.sbrf.uddk.ai.testing.domain.ai.AIDecisionParser;
import ru.sbrf.uddk.ai.testing.domain.ai.AIDecisionService;
import ru.sbrf.uddk.ai.testing.domain.ai.AIPromptBuilder;
import ru.sbrf.uddk.ai.testing.domain.model.Decision;
import ru.sbrf.uddk.ai.testing.domain.model.Observation;

/**
 * Сервис принятия решений AI
 * Координирует взаимодействие с LLM
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIDecisionServiceImpl implements AIDecisionService {

    private final ChatClient chatClient;
    private final AIPromptBuilder promptBuilder;
    private final AIDecisionParser decisionParser;

    @Override
    public Decision decideNextAction(Observation observation) {
        try {
            String prompt = promptBuilder.buildPrompt(observation);
            log.debug("Sending prompt to AI (first 500 chars): {}", 
                prompt.substring(0, Math.min(500, prompt.length())));
            
            String aiResponse = chatClient.prompt()
                .system("no_think")
                .user(prompt)
                .call()
                .content();
            
            log.debug("AI Response: {}", aiResponse);
            
            return decisionParser.parse(aiResponse);
            
        } catch (Exception e) {
            log.error("Failed to decide next action", e);
            return createFallbackDecision(observation);
        }
    }
    
    @Override
    public boolean isAvailable() {
        try {
            String testResponse = chatClient.prompt()
                .user("ping")
                .call()
                .content();
            return testResponse != null && !testResponse.isEmpty();
        } catch (Exception e) {
            log.warn("AI service is not available", e);
            return false;
        }
    }
    
    private Decision createFallbackDecision(Observation observation) {
        if (observation.getElements() == null || observation.getElements().isEmpty()) {
            return Decision.builder()
                .action("REFRESH")
                .reason("Нет видимых элементов, обновляю страницу")
                .expectedOutcome("Страница будет перезагружена")
                .build();
        }
        
        // Возвращаем первое доступное действие
        return Decision.builder()
            .action("CLICK")
            .reason("Fallback: кликаю на первый доступный элемент")
            .expectedOutcome("Действие будет выполнено")
            .build();
    }
}
