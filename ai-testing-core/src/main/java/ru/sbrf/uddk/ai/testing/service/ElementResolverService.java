package ru.sbrf.uddk.ai.testing.service;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ElementResolverService {
    /**
     * Найти элемент по сложному селектору с поддержкой разных стратегий
     */
    public WebElement resolveElement(WebDriver driver, String sel, int timeoutSeconds) {
        if (sel == null || sel.trim().isEmpty()) {
            throw new IllegalArgumentException("Selector cannot be null or empty");
        }

        String selector = sel.trim();


        FluentWait<WebDriver> wait = new FluentWait<>(driver)
                .withTimeout(Duration.ofSeconds(timeoutSeconds))
                .pollingEvery(Duration.ofMillis(500))
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class);

        return wait.until((Function<WebDriver, WebElement>) d -> {
            // Попробовать разные стратегии поиска
            List<WebElement> elements = tryAllFindingStrategies(driver, selector);

            if (elements.isEmpty()) {
                throw new NoSuchElementException("No elements found with selector: " + selector);
            }

            // Вернуть первый видимый и доступный элемент
            return elements.stream()
                    .filter(el -> {
                        try {
                            return el.isDisplayed() && el.isEnabled();
                        } catch (StaleElementReferenceException e) {
                            return false;
                        }
                    })
                    .findFirst()
                    .orElseThrow(() -> new ElementNotInteractableException(
                            "No visible/interactable elements found with selector: " + selector));
        });
    }

    /**
     * Попробовать все стратегии поиска элемента
     */
    private List<WebElement> tryAllFindingStrategies(WebDriver driver, String selector) {
        List<WebElement> results = new ArrayList<>();

        // 1. CSS Selector (наиболее вероятный)
        try {
            results.addAll(driver.findElements(By.cssSelector(selector)));
        } catch (Exception e) {
            // Игнорируем
        }

        // 2. XPath
        if (selector.startsWith("//") || selector.startsWith("./") ||
                selector.startsWith("(") || selector.contains("::")) {
            try {
                results.addAll(driver.findElements(By.xpath(selector)));
            } catch (Exception e) {
                // Игнорируем
            }
        }

        // 3. ID (только если селектор простой)
        if (selector.startsWith("#") && !selector.contains(" ")) {
            try {
                results.add(driver.findElement(By.id(selector.substring(1))));
            } catch (Exception e) {
                // Игнорируем
            }
        }

        // 4. Class Name (только если селектор простой)
        if (selector.startsWith(".") && !selector.contains(" ")) {
            try {
                results.add(driver.findElement(By.className(selector.substring(1))));
            } catch (Exception e) {
                // Игнорируем
            }
        }

        // 5. JavaScript поиск
        try {
            WebElement jsElement = findElementByJavaScript(driver, selector);
            if (jsElement != null) {
                results.add(jsElement);
            }
        } catch (Exception e) {
            // Игнорируем
        }

        return results;
    }

    private WebElement findElementByJavaScript(WebDriver driver, String selector) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Универсальный JavaScript поиск
            String script = String.format("""
                    try {
                        // Попробовать как CSS селектор
                        var element = document.querySelector('%s');
                        if (element) return element;
                        
                        // Попробовать как XPath
                        if ('%s'.startsWith('//') || '%s'.startsWith('./')) {
                            var result = document.evaluate('%s', document, null, 
                                XPathResult.FIRST_ORDERED_NODE_TYPE, null);
                            return result.singleNodeValue;
                        }
                        
                        return null;
                    } catch(e) {
                        return null;
                    }
                    """, selector, selector, selector, selector);

            Object result = js.executeScript(script);
            return result instanceof WebElement ? (WebElement) result : null;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Проверить, что элемент кликабелен
     */
    public boolean isElementClickable(WebDriver driver, WebElement element) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
            return wait.until(ExpectedConditions.elementToBeClickable(element)) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Найти все элементы по селектору
     */
    public List<WebElement> resolveElements(WebDriver driver, String selector, int timeoutSeconds) {
        FluentWait<WebDriver> wait = new FluentWait<>(driver)
                .withTimeout(Duration.ofSeconds(timeoutSeconds))
                .pollingEvery(Duration.ofMillis(500));

        return wait.until((Function<WebDriver, List<WebElement>>) d -> {
            List<WebElement> elements = driver.findElements(By.cssSelector(selector));
            return !elements.isEmpty() ? elements : null;
        });
    }
}
