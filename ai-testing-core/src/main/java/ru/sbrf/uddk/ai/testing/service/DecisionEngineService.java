package ru.sbrf.uddk.ai.testing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import ru.sbrf.uddk.ai.testing.entity.DiscoveredIssue;
import ru.sbrf.uddk.ai.testing.entity.InteractiveElement;
import ru.sbrf.uddk.ai.testing.entity.consts.IssueSeverity;
import ru.sbrf.uddk.ai.testing.entity.consts.IssueType;
import ru.sbrf.uddk.ai.testing.interfaces.TestAgentAction;
import ru.sbrf.uddk.ai.testing.model.AgentObservation;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DecisionEngineService {

    @Setter(onMethod_ = @Autowired)
    private ChatClient chatClient;

    @Setter(onMethod_ = @Autowired)
    private ActionRegistryService actionRegistryService;

    @Setter(onMethod_ = @Autowired)
    private ObjectMapper objectMapper;

    public static String removeStyleAndScriptTagsOptimized(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Предварительно компилируем паттерны для производительности
        Pattern[] patterns = {
                Pattern.compile("(?is)<style\\b[^>]*>.*?</style>"),  // (?is) - case insensitive + dotall
                Pattern.compile("(?is)<script\\b[^>]*>.*?</script>"),
                Pattern.compile("(?i)<(style|script)\\b[^>]*/>"),
                Pattern.compile("(?i)<(style|script)\\b[^>]*>")
        };

        String result = input;
        for (Pattern pattern : patterns) {
            result = pattern.matcher(result).replaceAll("");
        }

        return result;
    }

    public TestAgentAction decideNextAction(AgentObservation observation) {
        try {
            String prompt = buildDecisionPrompt(observation);
            log.debug("Sending prompt to AI: {}", prompt.substring(0, Math.min(500, prompt.length())));

            String aiResponse = chatClient.prompt().system("no_think").user(prompt).call().content();
            log.debug("AI Response: {}", aiResponse);

            Pattern pattern = Pattern.compile("(?i)<think>.*?</think>", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(aiResponse);
            String result = matcher.replaceAll("");

            Decision decision = parseDecision(result);
            log.info("AI Decision: action={}, target={}, reason={}",
                    decision.getAction(), decision.getTarget(), decision.getReason());

            return actionRegistryService.createAction(decision);

        } catch (Exception e) {
            log.error("Failed to decide next action", e);
            return getFallbackAction(observation);
        }
    }

    private String buildDecisionPrompt(AgentObservation observation) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("""
                Ты автономный агент для тестирования веб-приложений.
                Твоя задача: принимать решения о следующих действиях для тестирования.
                                
                Видимый DOM: %s
                                
                Цель тестирования: %s
                URL: %s
                Заголовок страницы: %s
                Прогресс: %.1f%%
                                
                """.formatted(
                removeStyleAndScriptTagsOptimized(observation.getPageSource()),
                observation.getGoalDescription(),
                observation.getUrl(),
                observation.getPageTitle(),
                observation.getGoalProgress() != null ? observation.getGoalProgress() * 100 : 0
        ));

        // Видимые элементы
        if (observation.getVisibleElements() != null && !observation.getVisibleElements().isEmpty()) {
            prompt.append("Видимые интерактивные элементы (").append(observation.getVisibleElements().size()).append("):\n");
            prompt.append(formatElements(observation.getVisibleElements()));
            prompt.append("\n\n");
        } else {
            prompt.append("Нет видимых интерактивных элементов на странице.\n\n");
        }

        // История действий
        if (observation.getPreviousActions() != null && !observation.getPreviousActions().isEmpty()) {
            prompt.append("История последних действий:\n");
            prompt.append(formatActionHistory(observation.getPreviousActions()));
            prompt.append("\n\n");
        }

        // Обнаруженные проблемы
        if (observation.getDiscoveredIssues() != null && !observation.getDiscoveredIssues().isEmpty()) {
            prompt.append("Уже обнаруженные проблемы:\n");
            prompt.append(formatIssues(observation.getDiscoveredIssues()));
            prompt.append("\n\n");
        }

        // Инструкции по выбору действия
        prompt.append("""
                Доступные типы действий:
                1. CLICK - кликнуть на элемент (указать селектор в target)
                2. TYPE - ввести текст в поле (указать селектор в target, текст в value)
                3. NAVIGATE_BACK - вернуться на предыдущую страницу
                4. NAVIGATE_FORWARD - перейти вперед
                5. NAVIGATE_TO - перейти по URL (указать URL в target)
                6. ASSERT_PRESENCE - проверить наличие элемента
                7. ASSERT_TEXT - проверить текст элемента
                8. SCROLL_UP - прокрутить вверх
                9. SCROLL_DOWN - прокрутить вниз
                10. REFRESH - обновить страницу
                11. EXPLORE_MENU - исследовать меню/навигацию
                12. EXPLORE_FORMS - исследовать формы на странице
                13. TEST_VALIDATION - проверить валидацию полей
                14. REPORT_ISSUE - сообщить о проблеме (указать описание в value)
                15. COMPLETE - завершить тестирование
                            
                Критерии выбора:
                - Приоритет у новых, неисследованных элементов
                - Избегай повторения одних и тех же действий
                - Если видишь форму - исследуй ее
                - Если видишь ошибку - зафиксируй ее
                - Если прогресс > 80% - подумай о завершении
                            
                Верни ответ в строгом JSON формате:
                {
                  "action": "ACTION_TYPE",
                  "target": "element_selector_or_url",
                  "value": "optional_text_or_value",
                  "reason": "обоснование выбора на русском",
                  "expectedOutcome": "что ожидаешь увидеть"
                }
                            
                Если элемент не найден или страница пустая, выбери REFRESH или NAVIGATE_BACK.
                """);

        return prompt.toString();
    }

    private String formatElements(List<InteractiveElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return "Нет элементов";
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;

        for (InteractiveElement element : elements) {
            if (count >= 20) { // Ограничиваем количество элементов в промпте
                sb.append("... и еще ").append(elements.size() - 20).append(" элементов\n");
                break;
            }

            sb.append(count + 1).append(". ");
            sb.append("Тип: ").append(element.getTagName());

            if (element.getText() != null && !element.getText().isEmpty()) {
                String text = element.getText();
                if (text.length() > 50) text = text.substring(0, 47) + "...";
                sb.append(", Текст: '").append(text).append("'");
            }

            if (element.getIdAttr() != null && !element.getIdAttr().isEmpty()) {
                sb.append(", ID: #").append(element.getIdAttr());
            }

            if (element.getName() != null && !element.getName().isEmpty()) {
                sb.append(", Name: ").append(element.getName());
            }

            if (element.getType() != null && !element.getType().isEmpty()) {
                sb.append(", Input type: ").append(element.getType());
            }

            if (element.getPlaceholder() != null && !element.getPlaceholder().isEmpty()) {
                sb.append(", Placeholder: '").append(element.getPlaceholder()).append("'");
            }

            if (element.getSelector() != null && !element.getSelector().isEmpty()) {
                sb.append(", Селектор: ").append(element.getSelector());
            }

            sb.append("\n");
            count++;
        }

        return sb.toString();
    }

    private String formatActionHistory(List<AgentAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return "Нет предыдущих действий";
        }

        StringBuilder sb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        // Берем последние 10 действий
        List<AgentAction> recentActions = actions.stream()
                .sorted((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()))
                .limit(10)
                .collect(Collectors.toList());

        Collections.reverse(recentActions); // Чтобы были в хронологическом порядке

        for (int i = 0; i < recentActions.size(); i++) {
            AgentAction action = recentActions.get(i);
            sb.append(i + 1).append(". ");
            sb.append(formatter.format(action.getTimestamp())).append(" - ");
            sb.append(action.getActionType());

            if (action.getTargetSelector() != null && !action.getTargetSelector().isEmpty()) {
                sb.append(" на '").append(truncate(action.getTargetSelector(), 40)).append("'");
            }

            if (action.getInputValue() != null && !action.getInputValue().isEmpty()) {
                sb.append(" значение: '").append(truncate(action.getInputValue(), 30)).append("'");
            }

            if (action.getResult() != null && action.getResult().getSuccess() != null) {
                sb.append(" [").append(action.getResult().getSuccess() ? "УСПЕХ" : "ОШИБКА").append("]");

                if (!action.getResult().getSuccess() && action.getResult().getMessage() != null) {
                    sb.append(": ").append(truncate(action.getResult().getMessage(), 50));
                }
            }

            sb.append("\n");
        }

        // Статистика
        long successCount = actions.stream()
                .filter(a -> a.getResult() != null && Boolean.TRUE.equals(a.getResult().getSuccess()))
                .count();

        sb.append("\nСтатистика: ").append(successCount).append("/").append(actions.size())
                .append(" успешных действий (").append(actions.size() > 0 ?
                        String.format("%.1f", (successCount * 100.0 / actions.size())) : "0")
                .append("%)");

        return sb.toString();
    }

    private String formatIssues(List<DiscoveredIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return "Проблем не обнаружено";
        }

        StringBuilder sb = new StringBuilder();

        // Группируем по типу и серьезности
        Map<IssueSeverity, Long> severityCount = issues.stream()
                .collect(Collectors.groupingBy(DiscoveredIssue::getSeverity, Collectors.counting()));

        Map<IssueType, Long> typeCount = issues.stream()
                .collect(Collectors.groupingBy(DiscoveredIssue::getType, Collectors.counting()));

        sb.append("Всего проблем: ").append(issues.size()).append("\n");

        if (!severityCount.isEmpty()) {
            sb.append("По серьезности:\n");
            severityCount.entrySet().stream()
                    .sorted(Map.Entry.<IssueSeverity, Long>comparingByKey().reversed())
                    .forEach(entry -> {
                        sb.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                    });
        }

        if (!typeCount.isEmpty()) {
            sb.append("По типу:\n");
            typeCount.entrySet().stream()
                    .sorted(Map.Entry.<IssueType, Long>comparingByValue().reversed())
                    .forEach(entry -> {
                        sb.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                    });
        }

        // Последние 3 проблемы подробно
        sb.append("\nПоследние обнаруженные проблемы:\n");
        issues.stream()
                .sorted((i1, i2) -> i2.getDiscoveredAt().compareTo(i1.getDiscoveredAt()))
                .limit(3)
                .forEach(issue -> {
                    sb.append("• ").append(issue.getTitle()).append(" [").append(issue.getSeverity()).append("]\n");
                    if (issue.getDescription() != null && issue.getDescription().length() > 100) {
                        sb.append("  ").append(issue.getDescription().substring(0, 97)).append("...\n");
                    }
                });

        return sb.toString();
    }

    private Decision parseDecision(String aiResponse) {
        try {
            // Пытаемся найти JSON в ответе AI
            String jsonResponse = extractJsonFromResponse(aiResponse);

            Decision decision = objectMapper.readValue(jsonResponse, Decision.class);

            // Валидация решения
            validateDecision(decision);

            return decision;

        } catch (JsonProcessingException e) {
            log.error("Failed to parse AI response as JSON: {}", aiResponse, e);
            return getDefaultDecision();
        } catch (Exception e) {
            log.error("Error parsing decision", e);
            return getDefaultDecision();
        }
    }

    private String extractJsonFromResponse(String response) {
        // Ищем JSON в ответе (мог быть обрамлен текстом)
        int jsonStart = response.indexOf('{');
        int jsonEnd = response.lastIndexOf('}');

        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd + 1);
        }

        // Если JSON не найден, пробуем очистить ответ
        String cleaned = response.replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

        // Если все еще не JSON, возвращаем как есть
        return cleaned.startsWith("{") ? cleaned : "{\"action\":\"REFRESH\",\"reason\":\"Не удалось распарсить ответ AI\"}";
    }

    private void validateDecision(Decision decision) {
        if (decision.getAction() == null || decision.getAction().isEmpty()) {
            throw new IllegalArgumentException("Action cannot be null or empty");
        }

        // Нормализуем действие
        decision.setAction(decision.getAction().toUpperCase().trim());

        // Проверяем поддерживаемые действия
        List<String> validActions = Arrays.asList(
                "CLICK", "TYPE", "NAVIGATE_BACK", "NAVIGATE_FORWARD",
                "NAVIGATE_TO", "ASSERT_PRESENCE", "ASSERT_TEXT",
                "SCROLL_UP", "SCROLL_DOWN", "REFRESH", "EXPLORE_MENU",
                "EXPLORE_FORMS", "TEST_VALIDATION", "REPORT_ISSUE", "COMPLETE"
        );

        if (!validActions.contains(decision.getAction())) {
            log.warn("Unknown action: {}, defaulting to REFRESH", decision.getAction());
            decision.setAction("REFRESH");
            decision.setReason("Неизвестное действие, обновляю страницу");
        }

        // Для определенных действий требуются target
        List<String> actionsRequiringTarget = Arrays.asList(
                "CLICK", "TYPE", "NAVIGATE_TO", "ASSERT_PRESENCE",
                "ASSERT_TEXT"
        );

        if (actionsRequiringTarget.contains(decision.getAction()) &&
                (decision.getTarget() == null || decision.getTarget().isEmpty())) {
            throw new IllegalArgumentException("Action " + decision.getAction() + " requires target");
        }

        // Для TYPE требуется value
        if ("TYPE".equals(decision.getAction()) &&
                (decision.getValue() == null || decision.getValue().isEmpty())) {
            decision.setValue("test"); // Значение по умолчанию для теста
        }

        // Если reason отсутствует, создаем дефолтный
        if (decision.getReason() == null || decision.getReason().isEmpty()) {
            decision.setReason(generateDefaultReason(decision));
        }
    }

    private String generateDefaultReason(Decision decision) {
        return switch (decision.getAction()) {
            case "CLICK" -> "Кликаю на элемент для проверки его функциональности";
            case "TYPE" -> "Ввожу тестовые данные в поле для проверки валидации";
            case "REFRESH" -> "Обновляю страницу для получения актуального состояния";
            case "SCROLL_DOWN" -> "Прокручиваю страницу для поиска новых элементов";
            case "EXPLORE_FORMS" -> "Исследую формы на странице для тестирования";
            case "COMPLETE" -> "Завершаю тестирование, так как достигнуты цели";
            default -> "Выполняю действие для продолжения тестирования";
        };
    }

    private Decision getDefaultDecision() {
        Decision decision = new Decision();
        decision.setAction("REFRESH");
        decision.setReason("Ошибка парсинга, обновляю страницу");
        decision.setExpectedOutcome("Страница будет перезагружена");
        return decision;
    }

    private TestAgentAction getFallbackAction(AgentObservation observation) {
        // Fallback логика: если страница пустая или нет элементов - обновить
        // иначе - кликнуть на первый доступный элемент

        if (observation.getVisibleElements() == null || observation.getVisibleElements().isEmpty()) {
            Decision decision = new Decision();
            decision.setAction("REFRESH");
            decision.setReason("Нет видимых элементов, обновляю страницу");
            return actionRegistryService.createAction(decision);
        }

        // Ищем первый кликабельный элемент
        Optional<InteractiveElement> clickableElement = observation.getVisibleElements().stream()
                .filter(el -> "a".equals(el.getTagName()) || "button".equals(el.getTagName()))
                .findFirst();

        if (clickableElement.isPresent()) {
            Decision decision = new Decision();
            decision.setAction("CLICK");
            decision.setTarget(clickableElement.get().getSelector());
            decision.setReason("Fallback: кликаю на первый доступный элемент");
            return actionRegistryService.createAction(decision);
        }

        // Если нет кликабельных элементов, скроллим
        Decision decision = new Decision();
        decision.setAction("SCROLL_DOWN");
        decision.setReason("Fallback: прокручиваю страницу для поиска элементов");
        return actionRegistryService.createAction(decision);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    @Data
    public static class Decision {
        private String action;
        private String target;
        private String value;
        private String reason;
        private String expectedOutcome;

        public Decision() {
            this.expectedOutcome = "Действие будет выполнено успешно";
        }
    }
}
