package ru.sbrf.uddk.ai.testing.domain.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * История действия
 */
@Data
@Builder
public class ActionHistory {
    
    /**
     * Тип действия
     */
    private String actionType;
    
    /**
     * Целевой элемент
     */
    private String target;
    
    /**
     * Результат (успех/неудача)
     */
    private boolean success;
    
    /**
     * Сообщение
     */
    private String message;
    
    /**
     * Временная метка
     */
    private LocalDateTime timestamp;
}
