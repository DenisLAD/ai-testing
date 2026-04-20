package ru.sbrf.uddk.ai.testing.domain.action;

import org.openqa.selenium.WebDriver;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import ru.sbrf.uddk.ai.testing.domain.model.Decision;

/**
 * Интерфейс действия агента
 * Базовый контракт для всех типов действий
 */
public interface TestAgentAction {
    
    /**
     * Выполняет действие
     */
    AgentAction execute(WebDriver driver);
    
    /**
     * Конфигурирует действие решением
     */
    void configure(Decision decision);
    
    /**
     * Возвращает описание действия
     */
    String getDescription();
    
    /**
     * Возвращает тип действия
     */
    String getType();
}
