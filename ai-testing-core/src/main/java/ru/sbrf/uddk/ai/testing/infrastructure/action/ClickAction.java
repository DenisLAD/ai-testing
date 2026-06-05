package ru.sbrf.uddk.ai.testing.infrastructure.action;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;

import java.util.Optional;

/**
 * Действие: Клик по элементу
 */
@Slf4j
@Component
public class ClickAction extends BaseAgentAction {

    @Override
    public String getType() {
        return "CLICK";
    }

    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing ClickAction on: {}", target);

        try {
            // Скриншот до
            String screenshotBefore = takeScreenshotBefore(driver);
            
            WebElement element = findElement(driver, target);
            if (!element.isEnabled() || isMuiDisabled(element)) {
                return createActionLog("CLICK", false,
                        "Элемент disabled — сначала выполните обязательные шаги формы (чекбоксы, выбор в дереве)");
            }
            highlightElement(driver, element);
            clickInteractable(driver, element);
            Thread.sleep(400);

            // Скриншот после
            String screenshotAfter = takeScreenshotAfter(driver);

            AgentAction logEntry = createActionLog("CLICK", true,
                    String.format("Успешно кликнул на элемент: %s", target));
            logEntry.setScreenshotBefore(screenshotBefore);
            logEntry.setScreenshotAfter(screenshotAfter);
            return logEntry;

        } catch (Exception e) {
            log.error("ClickAction failed: {}", e.getMessage());
            return createActionLog("CLICK", false,
                    String.format("Ошибка клика на элемент %s: %s", target, e.getMessage()));
        }
    }

    private void clickInteractable(WebDriver driver, WebElement element) {
        String tag = element.getTagName().toLowerCase();
        if ("label".equals(tag)) {
            WebElement checkbox = findCheckboxControl(element);
            if (checkbox != null) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", checkbox);
                return;
            }
        }
        if ("input".equals(tag) && "checkbox".equalsIgnoreCase(element.getAttribute("type"))) {
            String id = Optional.ofNullable(element.getAttribute("id")).orElse("");
            if (id.contains("-item-")) {
                ((JavascriptExecutor) driver).executeScript("""
                        const input = arguments[0];
                        const item = input.closest('li.k-treeview-item, [role="treeitem"]');
                        const targets = [
                            item ? item.querySelector('.k-checkbox, .k-checkbox-wrap, [role="checkbox"]') : null,
                            item ? item.querySelector('.k-treeview-item-text, .k-treeview-leaf-text') : null,
                            input
                        ].filter(Boolean);
                        for (const target of targets) {
                            target.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
                            target.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
                            target.click();
                        }
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        input.dispatchEvent(new Event('change', { bubbles: true }));
                        """, element);
            } else {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            }
            return;
        }
        String role = element.getAttribute("role");
        if ("checkbox".equals(role)) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            return;
        }
        if ("span".equals(tag)) {
            String cls = Optional.ofNullable(element.getAttribute("class")).orElse("");
            if (cls.contains("k-treeview-item")) {
                element.click();
                return;
            }
        }
        element.click();
    }

    private WebElement findCheckboxControl(WebElement container) {
        try {
            return container.findElement(By.cssSelector("input[type='checkbox']"));
        } catch (Exception ignored) {
            try {
                return container.findElement(By.cssSelector("[role='checkbox']"));
            } catch (Exception ignored2) {
                return null;
            }
        }
    }

    private boolean isMuiDisabled(WebElement element) {
        String cls = element.getAttribute("class");
        return cls != null && cls.contains("Mui-disabled");
    }
}
