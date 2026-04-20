package ru.sbrf.uddk.ai.testing.domain.webdriver;

import org.openqa.selenium.WebDriver;

/**
 * Провайдер WebDriver
 * Отвечает за создание и управление экземплярами браузера
 */
public interface WebDriverProvider {
    
    /**
     * Получает экземпляр WebDriver
     */
    WebDriver get();
    
    /**
     * Освобождает WebDriver
     */
    void release(WebDriver driver);
    
    /**
     * Проверяет доступность провайдера
     */
    boolean isAvailable();
}
