package ru.sbrf.uddk.ai.testing.domain.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Модель наблюдения за состоянием страницы
 * Используется для передачи данных между слоями
 */
@Data
@Builder
public class Observation {
    
    /**
     * URL страницы
     */
    private String url;
    
    /**
     * Заголовок страницы
     */
    private String title;
    
    /**
     * Видимый DOM
     */
    private String visibleDOM;
    
    /**
     * Интерактивные элементы
     */
    private List<InteractiveElement> elements;
    
    /**
     * Скриншот в base64
     */
    private String screenshot;
    
    /**
     * История действий
     */
    private List<ActionHistory> previousActions;
    
    /**
     * Обнаруженные проблемы
     */
    private List<Issue> issues;
    
    /**
     * Прогресс выполнения
     */
    private Double progress;
    
    /**
     * Описание цели
     */
    private String goalDescription;
    
    /**
     * Временная метка
     */
    private LocalDateTime timestamp;
    
    /**
     * Сообщение об ошибке
     */
    private String errorMessage;
}
