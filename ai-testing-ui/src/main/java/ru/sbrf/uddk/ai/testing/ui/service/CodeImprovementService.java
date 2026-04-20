package ru.sbrf.uddk.ai.testing.ui.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import ru.sbrf.uddk.ai.testing.ui.model.dto.CodeImprovementRequestDto;
import ru.sbrf.uddk.ai.testing.ui.model.dto.CodeImprovementResponseDto;

/**
 * Сервис для улучшения сгенерированного кода с помощью AI
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeImprovementService {

    private final ChatClient chatClient;

    private static final String IMPROVEMENT_PROMPT = """
        Ты опытный Java-разработчик и эксперт по тестированию.
        
        Твоя задача: улучшить сгенерированный JUnit 5 тест, сделав его более качественным, 
        читаемым и поддерживаемым.
        
        === КРИТЕРИИ УЛУЧШЕНИЯ ===
        1. Добавь понятные @DisplayName для каждого теста
        2. Улучши названия методов (should_When_Then формат)
        3. Добавь комментарии к сложным участкам
        4. Оптимизируй повторяющийся код
        5. Добавь правильные импорты
        6. Убедись что используются лучшие практики:
           - Явные ожидания вместо Thread.sleep()
           - Правильные ассерты (AssertJ если возможно)
           - Try-with-resources для ресурсов
           - Правильная структура теста (Arrange-Act-Assert)
        
        === ФОРМАТ ОТВЕТА ===
        Верни ТОЛЬКО улучшенный код теста без дополнительных объяснений.
        Код должен быть готовым к компиляции.
        
        === ИСХОДНЫЙ КОД ===
        """;

    /**
     * Улучшает сгенерированный код с помощью AI
     */
    public CodeImprovementResponseDto improveCode(CodeImprovementRequestDto request) {
        log.info("Улучшение кода для теста");

        try {
            String prompt = IMPROVEMENT_PROMPT + request.getSourceCode();

            String improvedCode = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

            // Извлекаем код из ответа (если AI добавил маркдаун)
            improvedCode = extractCodeFromResponse(improvedCode);

            String notes = generateImprovementNotes(request.getSourceCode(), improvedCode);

            return CodeImprovementResponseDto.builder()
                .improvedCode(improvedCode)
                .improvementNotes(notes)
                .build();

        } catch (Exception e) {
            log.error("Ошибка улучшения кода", e);
            // Возвращаем исходный код если улучшение не удалось
            return CodeImprovementResponseDto.builder()
                .improvedCode(request.getSourceCode())
                .improvementNotes("Не удалось улучшить код: " + e.getMessage())
                .build();
        }
    }

    /**
     * Извлекает код из ответа AI (убирает маркдаун блоки)
     */
    private String extractCodeFromResponse(String response) {
        if (response == null) return "";
        
        // Удаляем маркдаун блоки кода если они есть
        String code = response.replaceAll("```java\\s*", "")
                              .replaceAll("```\\s*", "")
                              .trim();
        
        return code;
    }

    /**
     * Генерирует краткое описание изменений
     */
    private String generateImprovementNotes(String original, String improved) {
        StringBuilder notes = new StringBuilder();
        notes.append("Улучшения:\n");
        
        // Проверяем что было добавлено
        if (improved.contains("@DisplayName")) {
            notes.append("- Добавлены @DisplayName для лучшей читаемости\n");
        }
        
        if (improved.contains("should") || improved.contains("When") || improved.contains("Then")) {
            notes.append("- Улучшены названия методов (BDD стиль)\n");
        }
        
        if (improved.contains("//")) {
            notes.append("- Добавлены комментарии\n");
        }
        
        if (improved.contains("Duration.ofSeconds")) {
            notes.append("- Заменены Thread.sleep() на явные ожидания\n");
        }
        
        int originalLines = original.split("\n").length;
        int improvedLines = improved.split("\n").length;
        
        if (improvedLines < originalLines) {
            notes.append("- Сокращен код на ").append(originalLines - improvedLines).append(" строк\n");
        }
        
        return notes.toString();
    }
}
