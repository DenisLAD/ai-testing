package ru.sbrf.uddk.ai.testing.domain.action;

import ru.sbrf.uddk.ai.testing.domain.model.Decision;

/**
 * Фабрика действий
 * Отвечает за создание и регистрацию действий
 */
public interface ActionFactory {
    
    /**
     * Регистрирует действие
     */
    void register(String type, TestAgentAction action);
    
    /**
     * Создает действие по типу
     */
    TestAgentAction create(Decision decision);
    
    /**
     * Проверяет наличие действия
     */
    boolean hasAction(String type);
    
    /**
     * Возвращает все зарегистрированные типы
     */
    java.util.Set<String> getRegisteredTypes();
}
