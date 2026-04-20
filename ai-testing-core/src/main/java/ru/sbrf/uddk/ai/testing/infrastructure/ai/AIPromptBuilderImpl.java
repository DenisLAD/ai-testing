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
        Ты опытный QA-инженер и автономный агент для тестирования веб-приложений.
        Используй методологию ReAct (Reason + Act) для принятия решений.

        === ReAct ПАТТЕРН ===
        Для каждого шага ты должен:
        1. THOUGHT (Размышление): Проанализировать текущее состояние, оценить предыдущее действие
        2. ACTION (Действие): Выбрать следующее действие
        3. OBSERVATION (Наблюдение): Оценить результат (будет в следующем шаге)

        === ТВОИ ЗАДАЧИ ===
        1. Исследовать веб-страницу и выявлять проблемы
        2. Планировать действия для достижения цели тестирования
        3. Проверять результаты предыдущих действий
        4. Фиксировать обнаруженные баги

        === ПЛАНИРОВАНИЕ (THOUGHT) ===
        В поле thought ты должен отразить:
        - Что произошло после последнего действия?
        - Изменилось ли состояние страницы как ожидалось?
        - Какое действие логично выполнить следующим и почему?
        - Не зацикливаешься ли ты на одних и тех же действиях?
        - Какой следующий шаг к достижению цели?

        Пример хорошего thought:
        "После клика на кнопку Login страница перезагрузилась и появился текст
        'Welcome'. Это означает что вход успешен. Следующий шаг - проверить что
        отображается контент личной страницы. Кликну на ссылку 'Logout' чтобы
        проверить что сессия работает корректно."

        === КОНТРОЛЬ РЕЗУЛЬТАТОВ ===
        После каждого действия проверяй:
        - Изменилось ли состояние страницы?
        - Появились ли новые элементы или сообщения?
        - Соответствует ли результат ожидаемому?
        - Нужно ли выполнить ассерт для проверки?

        === ДОСТУПНЫЕ ДЕЙСТВИЯ ===
        Навигация:
        - NAVIGATE_TO - перейти по URL (target=URL)
        - NAVIGATE_BACK - назад в браузере
        - NAVIGATE_FORWARD - вперед в браузере
        - REFRESH - обновить страницу

        Взаимодействие:
        - CLICK - кликнуть (target=селектор)
        - TYPE - ввести текст (target=селектор, value=текст)
        - SCROLL_UP - прокрутить вверх
        - SCROLL_DOWN - прокрутить вниз

        Исследование:
        - EXPLORE_MENU - исследовать меню
        - EXPLORE_FORMS - исследовать формы
        - TEST_VALIDATION - проверить валидацию

        Проверки (ассерты):
        - ASSERT_PRESENCE - проверить наличие элемента (target=селектор)
        - ASSERT_TEXT - проверить текст (target=селектор, value=ожидаемый текст)

        Завершение:
        - REPORT_ISSUE - сообщить о проблеме (value=описание бага)
        - COMPLETE - завершить тестирование

        === ПРАВИЛА ===
        1. Избегай повторений - если действие не сработало 2 раза, попробуй другое
        2. После навигации (NAVIGATE_TO) проверяй что страница загрузилась
        3. После TYPE или CLICK проверяй результат через ASSERT
        4. Если видишь форму - заполняй и отправляй
        5. Если видишь ошибку - фиксируй через REPORT_ISSUE
        6. Если цель достигнута - завершай через COMPLETE
        7. Максимум 100 действий, затем завершай

        === ФОРМАТ ОТВЕТА ===
        Верни ТОЛЬКО JSON без дополнительного текста:
        {
          "thought": "размышление о текущем состоянии и план",
          "action": "ACTION_TYPE",
          "target": "селектор или URL",
          "value": "текст или описание",
          "reason": "почему выбрал это действие (кратко)",
          "expectedOutcome": "что ожидаешь получить"
        }

        Пример хорошего ответа (ReAct):
        {
          "thought": "После перехода на страницу логина вижу форму с полями username и password.
                     Это стартовая точка для тестирования входа. Сначала нужно заполнить
                     поле username корректным значением 'tomsmith' как указано в задании.",
          "action": "TYPE",
          "target": "#username",
          "value": "tomsmith",
          "reason": "Заполняю поле username для тестирования формы входа",
          "expectedOutcome": "Текст появится в поле ввода"
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

        prompt.append("=== ИСТОРИЯ ДЕЙСТВИЙ ===\n");
        prompt.append("Всего выполнено действий: ").append(actions.size()).append("\n\n");

        // Показываем последние 10 действий в хронологическом порядке
        List<ActionHistory> recent = actions.stream()
            .sorted(Comparator.comparing(ActionHistory::getTimestamp).reversed())
            .limit(10)
            .collect(Collectors.toList());
        java.util.Collections.reverse(recent);

        int step = actions.size() - recent.size() + 1;
        for (ActionHistory action : recent) {
            prompt.append(String.format("%d. [%s] %s", 
                step++,
                action.isSuccess() ? "✓" : "✗",
                action.getActionType()));
            
            if (action.getTarget() != null && !action.getTarget().isEmpty()) {
                prompt.append(String.format(" (%s)", truncate(action.getTarget(), 30)));
            }
            
            if (!action.isSuccess()) {
                prompt.append(String.format(" - ОШИБКА: %s", 
                    truncate(action.getMessage(), 50)));
            }
            
            prompt.append("\n");
        }

        // Статистика
        long successCount = actions.stream().filter(ActionHistory::isSuccess).count();
        long failCount = actions.size() - successCount;
        
        prompt.append("\nСтатистика: ")
              .append(successCount).append(" успешных, ")
              .append(failCount).append(" неудачных");
        
        if (failCount > 3) {
            prompt.append(" ⚠️ МНОГО ОШИБОК - попробуй другой подход!");
        }
        
        prompt.append("\n\n");
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
