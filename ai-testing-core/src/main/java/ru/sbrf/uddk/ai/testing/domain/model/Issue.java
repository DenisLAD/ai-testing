package ru.sbrf.uddk.ai.testing.domain.model;

import lombok.Builder;
import lombok.Data;

/**
 * Проблема/баг
 */
@Data
@Builder
public class Issue {
    
    /**
     * Тип проблемы
     */
    private String type;
    
    /**
     * Серьезность
     */
    private String severity;
    
    /**
     * Заголовок
     */
    private String title;
    
    /**
     * Описание
     */
    private String description;
    
    /**
     * Шаги воспроизведения
     */
    private String stepsToReproduce;
}
