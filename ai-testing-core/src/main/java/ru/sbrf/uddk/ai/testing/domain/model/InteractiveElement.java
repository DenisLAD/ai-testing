package ru.sbrf.uddk.ai.testing.domain.model;

import lombok.Builder;
import lombok.Data;

/**
 * Интерактивный элемент страницы
 */
@Data
@Builder
public class InteractiveElement {
    
    /**
     * Тег элемента
     */
    private String tagName;
    
    /**
     * ID элемента
     */
    private String id;
    
    /**
     * CSS класс
     */
    private String className;
    
    /**
     * Текст элемента
     */
    private String text;
    
    /**
     * Тип (для input)
     */
    private String type;
    
    /**
     * Placeholder
     */
    private String placeholder;
    
    /**
     * CSS селектор
     */
    private String selector;
    
    /**
     * XPath
     */
    private String xpath;
    
    /**
     * Видим ли элемент
     */
    private boolean visible;
    
    /**
     * Доступен ли элемент
     */
    private boolean enabled;
    
    /**
     * Координаты на странице
     */
    private ElementBounds bounds;
}
