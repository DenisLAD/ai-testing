package ru.sbrf.uddk.ai.testing.domain.observation;

import ru.sbrf.uddk.ai.testing.domain.model.InteractiveElement;
import ru.sbrf.uddk.ai.testing.domain.model.Observation;

import java.util.List;

/**
 * Сервис сканирования элементов страницы
 * Отвечает за поиск и анализ интерактивных элементов
 */
public interface ElementScanner {
    
    /**
     * Сканирует видимые интерактивные элементы
     */
    List<InteractiveElement> scanVisibleElements(String sessionId);
    
    /**
     * Проверяет видимость элемента
     */
    boolean isVisible(String selector);
}
