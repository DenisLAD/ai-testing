package ru.sbrf.uddk.ai.testing.infrastructure.action;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Действие: Исследование меню/навигации
 */
@Slf4j
@Component
public class ExploreMenuAction extends BaseAgentAction {

    @Override
    public String getType() {
        return "EXPLORE_MENU";
    }

    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing ExploreMenuAction");

        try {
            // Скриншот до
            String screenshotBefore = takeScreenshotBefore(driver);
            
            // Ищем навигационные элементы
            List<WebElement> navElements = driver.findElements(
                By.cssSelector("nav, .menu, .navbar, .navigation, [role='navigation'], a[href]"));
            
            if (navElements.isEmpty()) {
                return createActionLog("EXPLORE_MENU", false, "Меню/навигация не найдены");
            }
            
            // Кликаем на первый доступный элемент меню
            for (WebElement element : navElements) {
                try {
                    if (element.isDisplayed() && element.isEnabled()) {
                        element.click();
                        Thread.sleep(500);
                        
                        // Скриншот после
                        String screenshotAfter = takeScreenshotAfter(driver);
                        
                        AgentAction logEntry = createActionLog("EXPLORE_MENU", true, 
                            "Исследую меню: клик на " + element.getText().substring(0, Math.min(30, element.getText().length())));
                        logEntry.setScreenshotBefore(screenshotBefore);
                        logEntry.setScreenshotAfter(screenshotAfter);
                        return logEntry;
                    }
                } catch (Exception e) {
                    // Пропускаем недоступные элементы
                }
            }
            
            return createActionLog("EXPLORE_MENU", false, "Не удалось кликнуть на элементы меню");
            
        } catch (Exception e) {
            log.error("ExploreMenuAction failed", e);
            return createActionLog("EXPLORE_MENU", false,
                    String.format("Ошибка исследования меню: %s", e.getMessage()));
        }
    }
}
