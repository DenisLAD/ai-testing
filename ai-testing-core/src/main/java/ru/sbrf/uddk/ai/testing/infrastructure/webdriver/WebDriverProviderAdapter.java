package ru.sbrf.uddk.ai.testing.infrastructure.webdriver;

import org.openqa.selenium.WebDriver;

/**
 * Адаптер для WebDriver
 * Используется для совместимости со старым кодом
 */
public interface WebDriverProviderAdapter {
    WebDriver get();
}
