package ru.sbrf.uddk.ai.testing.domain.ai;

import ru.sbrf.uddk.ai.testing.domain.model.Observation;

/**
 * Построитель промптов для AI
 * Отвечает за формирование контекста и инструкций для LLM
 */
public interface AIPromptBuilder {
    
    /**
     * Строит промпт на основе наблюдения
     */
    String buildPrompt(Observation observation);
    
    /**
     * Строит системный промпт
     */
    String buildSystemPrompt();
}
