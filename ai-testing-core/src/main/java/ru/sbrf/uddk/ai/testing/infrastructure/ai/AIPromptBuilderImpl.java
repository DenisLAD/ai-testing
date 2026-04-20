package ru.sbrf.uddk.ai.testing.infrastructure.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.sbrf.uddk.ai.testing.domain.ai.AIPromptBuilder;
import ru.sbrf.uddk.ai.testing.domain.model.ActionHistory;
import ru.sbrf.uddk.ai.testing.domain.model.Issue;
import ru.sbrf.uddk.ai.testing.domain.model.Observation;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Построитель промптов для AI
 * Реализация по умолчанию
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AIPromptBuilderImpl implements AIPromptBuilder {

    private static final String SYSTEM_PROMPT = """
        Ты автономный агент для тестирования веб-приложений.
        Твоя задача: принимать решения о следующих действиях для тестирования.
        
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
        - Если прогресс > 80%% - подумай о завершении
        
        Верни ответ в строгом JSON формате:
        {
          "action": "ACTION_TYPE",
          "target": "element_selector_or_url",
          "value": "optional_text_or_value",
          "reason": "обоснование выбора на русском",
          "expectedOutcome": "что ожидаешь увидеть"
        }
        """;

    @Override
    public String buildPrompt(Observation observation) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("""
            === ИНФОРМАЦИЯ О СТРАНИЦЕ ===
            URL: %s
            Заголовок: %s
            Цель тестирования: %s
            Прогресс: %.1f%%
            
            === ВИДИМЫЙ DOM ===
            (Показаны только видимые интерактивные элементы, теги html/head/body удалены)
            %s
            
            """.formatted(
                observation.getUrl(),
                observation.getTitle(),
                observation.getGoalDescription(),
                observation.getProgress() != null ? observation.getProgress() * 100 : 0,
                removeStyleAndScriptTags(observation.getVisibleDOM())
        ));
        
        appendVisibleElements(prompt, observation.getElements());
        appendActionHistory(prompt, observation.getPreviousActions());
        appendIssues(prompt, observation.getIssues());
        
        prompt.append(SYSTEM_PROMPT);
        
        return prompt.toString();
    }
    
    @Override
    public String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }
    
    private void appendVisibleElements(StringBuilder prompt, List<?> elements) {
        if (elements == null || elements.isEmpty()) {
            prompt.append("Нет видимых интерактивных элементов на странице.\n\n");
            return;
        }
        
        prompt.append("Видимые интерактивные элементы (")
              .append(elements.size())
              .append("):\n");
        
        int count = 0;
        for (Object obj : elements) {
            if (count >= 20) {
                prompt.append("... и еще ")
                      .append(elements.size() - 20)
                      .append(" элементов\n");
                break;
            }
            
            // TODO: Использовать правильный тип вместо Object
            prompt.append(formatElement(obj, count + 1));
            count++;
        }
        
        prompt.append("\n\n");
    }
    
    private String formatElement(Object element, int index) {
        // Упрощенное форматирование - будет заменено на правильное
        return index + ". " + element.toString() + "\n";
    }
    
    private void appendActionHistory(StringBuilder prompt, List<ActionHistory> actions) {
        if (actions == null || actions.isEmpty()) {
            prompt.append("История действий: Нет предыдущих действий\n\n");
            return;
        }
        
        prompt.append("История последних действий:\n");
        
        List<ActionHistory> recent = actions.stream()
            .sorted(Comparator.comparing(ActionHistory::getTimestamp).reversed())
            .limit(10)
            .collect(Collectors.toList());
        
        int i = 1;
        for (ActionHistory action : recent) {
            prompt.append(i++)
                  .append(". ")
                  .append(action.getActionType())
                  .append(action.getTarget() != null ? " на '" + truncate(action.getTarget(), 40) + "'" : "")
                  .append(action.isSuccess() ? " [УСПЕХ]" : " [ОШИБКА]")
                  .append("\n");
        }
        
        long successCount = actions.stream().filter(ActionHistory::isSuccess).count();
        prompt.append("\nСтатистика: ")
              .append(successCount)
              .append("/")
              .append(actions.size())
              .append(" успешных действий\n\n");
    }
    
    private void appendIssues(StringBuilder prompt, List<Issue> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }
        
        prompt.append("Уже обнаруженные проблемы:\n");
        prompt.append("Всего проблем: ").append(issues.size()).append("\n");
        
        issues.stream()
            .sorted(Comparator.comparing(Issue::getSeverity).reversed())
            .limit(3)
            .forEach(issue -> 
                prompt.append("• ")
                      .append(issue.getTitle())
                      .append(" [")
                      .append(issue.getSeverity())
                      .append("]\n")
            );
        
        prompt.append("\n\n");
    }
    
    private String removeStyleAndScriptTags(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        String result = input;
        result = result.replaceAll("(?is)<style\\b[^>]*>.*?</style>", "");
        result = result.replaceAll("(?is)<script\\b[^>]*>.*?</script>", "");
        result = result.replaceAll("(?i)<(style|script)\\b[^>]*/>", "");
        result = result.replaceAll("(?i)<(style|script)\\b[^>]*>", "");
        
        return result;
    }
    
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 3) + "...";
    }
}
