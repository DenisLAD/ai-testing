package ru.sbrf.uddk.ai.testing.interfaces;


import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import ru.sbrf.uddk.ai.testing.service.DecisionEngineService;
import org.openqa.selenium.WebDriver;



public interface TestAgentAction {
    /**
     * Выполняет действие в браузере
     * @param driver WebDriver для взаимодействия с браузером
     * @return Лог выполненного действия
     */
    AgentAction execute(WebDriver driver);

    /**
     * Возвращает описание действия
     */
    String getDescription();

    /**
     * Конфигурирует действие на основе решения AI
     */
    default void configure(DecisionEngineService.Decision decision) {
        // Базовая реализация может быть пустой
    }
}
