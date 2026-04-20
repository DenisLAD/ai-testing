package ru.sbrf.uddk.ai.testing.service.actions;

import lombok.Getter;
import ru.sbrf.uddk.ai.testing.entity.ActionResult;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import ru.sbrf.uddk.ai.testing.interfaces.TestAgentAction;
import ru.sbrf.uddk.ai.testing.service.DecisionEngineService;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.io.File;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import java.nio.file.Files;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

@Slf4j
public abstract class BaseAgentAction implements TestAgentAction {

    public static final int timeoutSeconds = 10;
    public static final int pollingIntervalMs = 200;
    protected String target;
    protected String value;

    @Getter
    protected String reason;
    protected String expectedOutcome;

    public void configure(DecisionEngineService.Decision decision) {
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
                value != null ? value.substring(0, Math.min(20, value.length())) : "",
                reason != null ? reason.substring(0, Math.min(50, reason.length())) : "");
    }

    protected WebElement findElement(WebDriver driver, String selector) {
        return findElement(driver, selector, timeoutSeconds);
    }

    protected WebElement findElement(WebDriver driver, String sel, int timeoutSeconds) {
        if (sel == null || sel.trim().isEmpty()) {
            throw new IllegalArgumentException("Selector cannot be null or empty");
        }

        String selector = sel.trim();
        log.debug("Finding element with selector: {}", selector);

        try {
            // Используем FluentWait для более гибкого ожидания
            FluentWait<WebDriver> wait = new FluentWait<>(driver)
                    .withTimeout(Duration.ofSeconds(timeoutSeconds))
                    .pollingEvery(Duration.ofMillis(pollingIntervalMs))
                    .ignoring(NoSuchElementException.class)
                    .ignoring(StaleElementReferenceException.class);

            return wait.until(new Function<WebDriver, WebElement>() {
                @Override
                public WebElement apply(WebDriver driver) {
                    return locateElement(driver, selector);
                }
            });

        } catch (TimeoutException e) {
            throw new NoSuchElementException(String.format(
                    "Element not found with selector '%s' after %d seconds",
                    selector, timeoutSeconds));
        }
    }

    // Основной метод локации элемента с поддержкой разных стратегий
    private WebElement locateElement(WebDriver driver, String selector) {
        // 1. Попробуем как CSS селектор (самый распространенный случай)
        try {
            List<WebElement> elements = driver.findElements(By.cssSelector(selector));
            if (!elements.isEmpty() && elements.get(0).isDisplayed() && elements.get(0).isEnabled()) {
                return elements.get(0);
            }
        } catch (Exception e) {
            log.debug("Failed to find element by CSS selector: {}", selector);
        }

        // 2. Если селектор похож на XPath
        if (selector.startsWith("//") || selector.startsWith("./") ||
                selector.startsWith("(") || selector.startsWith("@")) {
            try {
                return driver.findElement(By.xpath(selector));
            } catch (Exception e) {
                log.debug("Failed to find element by XPath: {}", selector);
            }
        }

        // 3. Если селектор похож на ID
        if (selector.startsWith("#") && !selector.contains(" ")) {
            try {
                String id = selector.substring(1);
                return driver.findElement(By.id(id));
            } catch (Exception e) {
                log.debug("Failed to find element by ID: {}", selector);
            }
        }

        // 4. Если селектор похож на класс
        if (selector.startsWith(".") && !selector.contains(" ")) {
            try {
                String className = selector.substring(1);
                return driver.findElement(By.className(className));
            } catch (Exception e) {
                log.debug("Failed to find element by class name: {}", selector);
            }
        }

        // 5. Если селектор похож на имя
        if (selector.startsWith("[name='") && selector.endsWith("']")) {
            try {
                String name = selector.substring(7, selector.length() - 2);
                return driver.findElement(By.name(name));
            } catch (Exception e) {
                log.debug("Failed to find element by name: {}", selector);
            }
        }

        // 6. Если селектор похож на текст ссылки
        if (selector.startsWith("link=") || selector.startsWith("text=")) {
            try {
                String linkText = selector.substring(5);
                return driver.findElement(By.linkText(linkText));
            } catch (Exception e) {
                log.debug("Failed to find element by link text: {}", selector);
            }
        }

        // 7. Если селектор похож на частичный текст ссылки
        if (selector.startsWith("partial=")) {
            try {
                String partialText = selector.substring(8);
                return driver.findElement(By.partialLinkText(partialText));
            } catch (Exception e) {
                log.debug("Failed to find element by partial link text: {}", selector);
            }
        }

        // 8. Если селектор похож на тег
        if (!selector.contains("[") && !selector.contains(".") &&
                !selector.contains("#") && !selector.contains(" ")) {
            try {
                return driver.findElement(By.tagName(selector));
            } catch (Exception e) {
                log.debug("Failed to find element by tag name: {}", selector);
            }
        }

        // 9. Попробуем JavaScript поиск как последнее средство
        try {
            WebElement element = findElementByJavaScript(driver, selector);
            if (element != null) {
                return element;
            }
        } catch (Exception e) {
            log.debug("JavaScript search failed for selector: {}", selector);
        }

        throw new NoSuchElementException("Element not found with selector: " + selector);
    }

    // JavaScript поиск элемента
    private WebElement findElementByJavaScript(WebDriver driver, String selector) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Пробуем разные стратегии через JavaScript
            String[] jsMethods = {
                    // CSS селектор
                    String.format("return document.querySelector('%s');", selector),

                    // Все элементы по CSS
                    String.format("var els = document.querySelectorAll('%s'); return els.length > 0 ? els[0] : null;", selector),

                    // По ID если селектор выглядит как ID
                    selector.startsWith("#") ?
                            String.format("return document.getElementById('%s');", selector.substring(1)) : null,

                    // По классу если селектор выглядит как класс
                    selector.startsWith(".") && !selector.contains(" ") ?
                            String.format("var els = document.getElementsByClassName('%s'); return els.length > 0 ? els[0] : null;",
                                    selector.substring(1)) : null,

                    // По имени
                    String.format("return document.querySelector('[name=\"%s\"]');",
                            selector.replaceAll("\\[name=['\"](.+)['\"]\\]", "$1")),

                    // По XPath
                    selector.startsWith("//") ?
                            String.format("""
                                    var result = document.evaluate('%s', document, null, 
                                        XPathResult.FIRST_ORDERED_NODE_TYPE, null);
                                    return result.singleNodeValue;
                                    """, selector) : null
            };

            for (String jsMethod : jsMethods) {
                if (jsMethod == null) continue;

                try {
                    Object result = js.executeScript(jsMethod);
                    if (result instanceof WebElement) {
                        return (WebElement) result;
                    }
                } catch (Exception e) {
                    // Продолжаем пробовать следующие методы
                }
            }
        } catch (Exception e) {
            log.debug("JavaScript element search failed", e);
        }

        return null;
    }

    // Найти несколько элементов
    protected List<WebElement> findElements(WebDriver driver, String selector) {
        return findElements(driver, selector, timeoutSeconds);
    }

    protected List<WebElement> findElements(WebDriver driver, String selector, int timeoutSeconds) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            return wait.until(d -> {
                List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                return !elements.isEmpty() ? elements : null;
            });
        } catch (TimeoutException e) {
            throw new NoSuchElementException("No elements found with selector: " + selector);
        }
    }

    // Проверить, что элемент кликабелен
    protected boolean isElementClickable(WebDriver driver, WebElement element) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
            return wait.until(ExpectedConditions.elementToBeClickable(element)) != null;
        } catch (Exception e) {
            return false;
        }
    }

    // Сделать скриншот перед действием
    protected String takeScreenshotBefore(WebDriver driver, String sessionId) {
        try {
            if (driver instanceof TakesScreenshot takesScreenshot) {
                File screenshot = takesScreenshot.getScreenshotAs(OutputType.FILE);
                byte[] screenshotBytes = Files.readAllBytes(screenshot.toPath());
                return Base64.getEncoder().encodeToString(screenshotBytes);
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to take screenshot before action", e);
            return null;
        }
    }

    // Сделать скриншот после действия
    protected String takeScreenshotAfter(WebDriver driver, String sessionId) {
        try {
            // Даем время для отрисовки изменений
            Thread.sleep(300);
            if (driver instanceof TakesScreenshot takesScreenshot) {
                File screenshot = takesScreenshot.getScreenshotAs(OutputType.FILE);
                byte[] screenshotBytes = Files.readAllBytes(screenshot.toPath());
                return Base64.getEncoder().encodeToString(screenshotBytes);
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to take screenshot after action", e);
            return null;
        }
    }

    // Обрезать строку
    protected String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    // Выделить элемент перед действием
    protected void highlightElement(WebDriver driver, WebElement element) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(
                    "arguments[0].style.border = '3px solid red'; arguments[0].style.backgroundColor = 'yellow';",
                    element
            );
            Thread.sleep(100); // Дать время для визуализации
        } catch (Exception e) {
            log.debug("Failed to highlight element", e);
        }
    }

    // Убрать выделение
    protected void removeHighlight(WebDriver driver, WebElement element) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(
                    "arguments[0].style.border = ''; arguments[0].style.backgroundColor = '';",
                    element
            );
        } catch (Exception e) {
            log.debug("Failed to remove highlight", e);
        }
    }

    // Получить текст элемента безопасно
    protected String getElementTextSafely(WebElement element) {
        try {
            return element.getText();
        } catch (StaleElementReferenceException e) {
            return "[stale element]";
        } catch (Exception e) {
            return "[error getting text]";
        }
    }

    protected AgentAction createActionLog(
            String actionType, boolean success, String message) {

        AgentAction actionLog = new AgentAction();

        actionLog.setActionType(actionType);
        actionLog.setTargetSelector(target);
        actionLog.setInputValue(value);
        actionLog.setReason(reason);
        actionLog.setTimestamp(LocalDateTime.now());

        ActionResult result = new ActionResult();
        result.setSuccess(success);
        result.setMessage(message);

        actionLog.setResult(result);
        return actionLog;
    }
}
