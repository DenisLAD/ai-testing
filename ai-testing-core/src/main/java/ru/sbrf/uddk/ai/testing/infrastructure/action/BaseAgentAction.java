package ru.sbrf.uddk.ai.testing.infrastructure.action;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.WebDriverWait;
import ru.sbrf.uddk.ai.testing.domain.action.TestAgentAction;
import ru.sbrf.uddk.ai.testing.domain.model.Decision;
import ru.sbrf.uddk.ai.testing.entity.ActionResult;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Function;

/**
 * Базовый класс для действий агента
 * Реализует общую логику поиска элементов и логирования
 */
@Slf4j
@RequiredArgsConstructor
public abstract class BaseAgentAction implements TestAgentAction {

    public static final int DEFAULT_TIMEOUT_SECONDS = 10;
    public static final int POLLING_INTERVAL_MS = 200;
    
    protected String target;
    protected String value;
    
    @Getter
    protected String reason;
    protected String expectedOutcome;

    @Override
    public void configure(Decision decision) {
        this.target = decision.getTarget();
        this.value = decision.getValue();
        this.reason = decision.getReason();
        this.expectedOutcome = decision.getExpectedOutcome();
    }

    @Override
    public String getDescription() {
        return String.format("%s(target='%s', value='%s', reason='%s')",
                getClass().getSimpleName(),
                target,
                value != null ? truncate(value, 20) : "",
                reason != null ? truncate(reason, 50) : "");
    }

    /**
     * Выполняет действие
     */
    @Override
    public abstract AgentAction execute(WebDriver driver);

    /**
     * Создает лог действия
     */
    protected AgentAction createActionLog(String actionType, boolean success, String message) {
        AgentAction log = new AgentAction();
        log.setActionType(actionType);
        log.setTargetSelector(target);
        log.setInputValue(value);
        log.setReason(reason);
        log.setExpectedOutcome(expectedOutcome);
        log.setTimestamp(LocalDateTime.now());
        
        ActionResult result = new ActionResult();
        result.setSuccess(success);
        result.setMessage(message);
        log.setResult(result);
        
        return log;
    }

    /**
     * Находит элемент с ожиданием
     */
    protected WebElement findElement(WebDriver driver, String selector) {
        return findElement(driver, selector, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Находит элемент с кастомным таймаутом
     */
    protected WebElement findElement(WebDriver driver, String selector, int timeoutSeconds) {
        if (selector == null || selector.trim().isEmpty()) {
            throw new IllegalArgumentException("Selector cannot be null or empty");
        }

        log.debug("Finding element with selector: {}", selector);

        try {
            FluentWait<WebDriver> wait = new FluentWait<>(driver)
                    .withTimeout(Duration.ofSeconds(timeoutSeconds))
                    .pollingEvery(Duration.ofMillis(POLLING_INTERVAL_MS))
                    .ignoring(NoSuchElementException.class)
                    .ignoring(StaleElementReferenceException.class);

            return wait.until(driverInstance -> locateElement(driverInstance, selector));

        } catch (TimeoutException e) {
            throw new NoSuchElementException("Element not found: " + selector);
        }
    }

    /**
     * Создает WebDriverWait
     */
    protected WebDriverWait getWait(WebDriver driver) {
        return new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
    }

    /**
     * Выполняет JavaScript
     */
    protected Object executeJavaScript(WebDriver driver, String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }

    /**
     * Выделяет элемент (для отладки)
     */
    protected void highlightElement(WebDriver driver, WebElement element) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].style.border='3px solid red'", element);
        } catch (Exception e) {
            log.debug("Failed to highlight element", e);
        }
    }

    /**
     * Снимает выделение
     */
    protected void removeHighlight(WebDriver driver, WebElement element) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].style.border=''", element);
        } catch (Exception e) {
            log.debug("Failed to remove highlight", e);
        }
    }

    /**
     * Усечение строки
     */
    protected String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    // Приватные методы поиска
    private WebElement locateElement(WebDriver driver, String selector) {
        // Пробуем разные стратегии поиска
        try {
            return driver.findElement(By.cssSelector(selector));
        } catch (Exception ignored) {}

        try {
            return driver.findElement(By.xpath(selector));
        } catch (Exception ignored) {}

        if (selector.startsWith("#")) {
            try {
                return driver.findElement(By.id(selector.substring(1)));
            } catch (Exception ignored) {}
        }

        if (selector.startsWith(".")) {
            try {
                return driver.findElement(By.className(selector.substring(1)));
            } catch (Exception ignored) {}
        }

        throw new NoSuchElementException("Unable to locate element: " + selector);
    }
}
