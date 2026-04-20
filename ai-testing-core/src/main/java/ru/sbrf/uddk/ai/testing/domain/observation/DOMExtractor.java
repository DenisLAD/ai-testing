package ru.sbrf.uddk.ai.testing.domain.observation;

/**
 * Сервис извлечения DOM
 * Отвечает за получение и оптимизацию DOM для передачи в AI
 */
public interface DOMExtractor {
    
    /**
     * Извлекает видимый DOM
     */
    String extractVisibleDOM();
    
    /**
     * Извлекает компактное представление DOM
     */
    String extractCompactDOM();
    
    /**
     * Оптимизирует DOM для передачи в AI
     */
    String optimizeForAI(String dom);
}
