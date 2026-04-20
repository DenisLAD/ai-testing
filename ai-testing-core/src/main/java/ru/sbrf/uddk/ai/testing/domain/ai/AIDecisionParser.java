package ru.sbrf.uddk.ai.testing.domain.ai;

import ru.sbrf.uddk.ai.testing.domain.model.Decision;

/**
 * Парсер решений AI
 * Отвечает за извлечение и валидацию решений из ответов LLM
 */
public interface AIDecisionParser {
    
    /**
     * Парсит ответ AI в объект решения
     */
    Decision parse(String aiResponse);
    
    /**
     * Валидирует решение
     */
    void validate(Decision decision);
}
