package ru.sbrf.uddk.ai.testing.domain.model;

import lombok.Builder;
import lombok.Data;

/**
 * Модель решения AI о следующем действии (ReAct паттерн)
 */
@Data
@Builder
public class Decision {

    /**
     * Тип действия
     */
    private String action;

    /**
     * Целевой элемент (селектор)
     */
    private String target;

    /**
     * Значение (для TYPE и других)
     */
    private String value;

    /**
     * Обоснование выбора (краткое)
     */
    private String reason;

    /**
     * Ожидаемый результат
     */
    private String expectedOutcome;

    /**
     * Уверенность решения (0.0 - 1.0)
     */
    private Double confidence;
    
    /**
     * Размышление AI о текущем состоянии (ReAct Thought)
     * Содержит анализ ситуации, план, оценку предыдущего действия
     */
    private String thought;
}
