package ru.sbrf.uddk.ai.testing.domain.ai;

import ru.sbrf.uddk.ai.testing.domain.model.Decision;
import ru.sbrf.uddk.ai.testing.domain.model.Observation;

/**
 * Сервис принятия решений AI
 * Отвечает за взаимодействие с LLM и парсинг ответов
 */
public interface AIDecisionService {
    
    /**
     * Принимает решение о следующем действии на основе наблюдения
     */
    Decision decideNextAction(Observation observation);
    
    /**
     * Проверяет возможность принятия решений
     */
    boolean isAvailable();
}
