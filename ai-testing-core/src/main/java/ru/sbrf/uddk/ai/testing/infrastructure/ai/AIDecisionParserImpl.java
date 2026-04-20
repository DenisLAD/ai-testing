package ru.sbrf.uddk.ai.testing.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import ru.sbrf.uddk.ai.testing.domain.ai.AIDecisionParser;
import ru.sbrf.uddk.ai.testing.domain.model.Decision;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Парсер решений AI
 * Реализация по умолчанию
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AIDecisionParserImpl implements AIDecisionParser {

    private final ObjectMapper objectMapper;
    
    private static final List<String> VALID_ACTIONS = Arrays.asList(
        "CLICK", "TYPE", "NAVIGATE_BACK", "NAVIGATE_FORWARD",
        "NAVIGATE_TO", "ASSERT_PRESENCE", "ASSERT_TEXT",
        "SCROLL_UP", "SCROLL_DOWN", "REFRESH", "EXPLORE_MENU",
        "EXPLORE_FORMS", "TEST_VALIDATION", "REPORT_ISSUE", "COMPLETE"
    );
    
    private static final Pattern JSON_EXTRACT_PATTERN = Pattern.compile("(?s).*?(\\{.*\\}).*?");
    private static final Pattern THINK_PATTERN = Pattern.compile("(?i)<think>.*?</think>", Pattern.DOTALL);

    @Override
    public Decision parse(String aiResponse) {
        try {
            String jsonResponse = extractJsonFromResponse(aiResponse);
            Decision decision = objectMapper.readValue(jsonResponse, Decision.class);
            validate(decision);
            return decision;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse AI response as JSON: {}", aiResponse, e);
            return createDefaultDecision();
        } catch (Exception e) {
            log.error("Error parsing decision", e);
            return createDefaultDecision();
        }
    }
    
    @Override
    public void validate(Decision decision) {
        if (decision.getAction() == null || decision.getAction().isEmpty()) {
            throw new IllegalArgumentException("Action cannot be null or empty");
        }
        
        String normalizedAction = decision.getAction().toUpperCase().trim();
        
        if (!VALID_ACTIONS.contains(normalizedAction)) {
            log.warn("Unknown action: {}, defaulting to REFRESH", normalizedAction);
            decision.setAction("REFRESH");
            decision.setReason("Неизвестное действие, обновляю страницу");
        } else {
            decision.setAction(normalizedAction);
        }
        
        validateTarget(decision);
        validateValue(decision);
        ensureReason(decision);
    }
    
    private void validateTarget(Decision decision) {
        List<String> actionsRequiringTarget = Arrays.asList(
            "CLICK", "TYPE", "NAVIGATE_TO", "ASSERT_PRESENCE", "ASSERT_TEXT"
        );
        
        if (actionsRequiringTarget.contains(decision.getAction()) &&
            (decision.getTarget() == null || decision.getTarget().isEmpty())) {
            throw new IllegalArgumentException("Action " + decision.getAction() + " requires target");
        }
    }
    
    private void validateValue(Decision decision) {
        if ("TYPE".equals(decision.getAction()) &&
            (decision.getValue() == null || decision.getValue().isEmpty())) {
            decision.setValue("test");
        }
    }
    
    private void ensureReason(Decision decision) {
        if (decision.getReason() == null || decision.getReason().isEmpty()) {
            decision.setReason(generateDefaultReason(decision.getAction()));
        }
    }
    
    private String generateDefaultReason(String action) {
        return switch (action) {
            case "CLICK" -> "Кликаю на элемент для проверки его функциональности";
            case "TYPE" -> "Ввожу тестовые данные в поле для проверки валидации";
            case "REFRESH" -> "Обновляю страницу для получения актуального состояния";
            case "SCROLL_DOWN" -> "Прокручиваю страницу для поиска новых элементов";
            case "EXPLORE_FORMS" -> "Исследую формы на странице для тестирования";
            case "COMPLETE" -> "Завершаю тестирование, так как достигнуты цели";
            default -> "Выполняю действие для продолжения тестирования";
        };
    }
    
    private Decision createDefaultDecision() {
        return Decision.builder()
            .action("REFRESH")
            .reason("Ошибка парсинга, обновляю страницу")
            .expectedOutcome("Страница будет перезагружена")
            .build();
    }
    
    private String extractJsonFromResponse(String response) {
        String cleaned = THINK_PATTERN.matcher(response).replaceAll("");
        
        int jsonStart = cleaned.indexOf('{');
        int jsonEnd = cleaned.lastIndexOf('}');
        
        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return cleaned.substring(jsonStart, jsonEnd + 1);
        }
        
        String jsonLike = cleaned.replaceAll("```json\\s*", "")
            .replaceAll("```\\s*", "")
            .trim();
        
        return jsonLike.startsWith("{") ? jsonLike : "{\"action\":\"REFRESH\",\"reason\":\"Не удалось распарсить ответ AI\"}";
    }
}
