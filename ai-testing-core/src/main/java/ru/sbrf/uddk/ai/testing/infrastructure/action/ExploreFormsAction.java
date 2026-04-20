package ru.sbrf.uddk.ai.testing.infrastructure.action;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Действие: Исследование форм на странице
 */
@Slf4j
@Component
public class ExploreFormsAction extends BaseAgentAction {

    @Override
    public String getType() {
        return "EXPLORE_FORMS";
    }

    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing ExploreFormsAction");

        try {
            // Скриншот до
            String screenshotBefore = takeScreenshotBefore(driver);
            
            // Ищем формы
            List<WebElement> forms = driver.findElements(By.tagName("form"));
            
            if (forms.isEmpty()) {
                // Пробуем найти интерактивные элементы
                List<WebElement> inputs = driver.findElements(
                    By.cssSelector("input, textarea, select, button"));
                
                if (inputs.isEmpty()) {
                    return createActionLog("EXPLORE_FORMS", false, "Формы и интерактивные элементы не найдены");
                }
                
                // Взаимодействуем с первым доступным элементом
                for (WebElement input : inputs) {
                    try {
                        if (input.isDisplayed()) {
                            String tagName = input.getTagName();
                            
                            if ("input".equals(tagName) || "textarea".equals(tagName)) {
                                input.click();
                            } else if ("button".equals(tagName)) {
                                input.click();
                            }
                            
                            // Скриншот после
                            String screenshotAfter = takeScreenshotAfter(driver);
                            
                            AgentAction logEntry = createActionLog("EXPLORE_FORMS", true, 
                                "Исследую форму: взаимодействие с " + tagName);
                            logEntry.setScreenshotBefore(screenshotBefore);
                            logEntry.setScreenshotAfter(screenshotAfter);
                            return logEntry;
                        }
                    } catch (Exception e) {
                        // Пропускаем недоступные элементы
                    }
                }
            }
            
            // Если есть формы, исследуем первую
            WebElement form = forms.get(0);
            
            // Скриншот после
            String screenshotAfter = takeScreenshotAfter(driver);
            
            return createActionLog("EXPLORE_FORMS", true, 
                "Найдена форма на странице: " + form.getAttribute("action"));
            
        } catch (Exception e) {
            log.error("ExploreFormsAction failed", e);
            return createActionLog("EXPLORE_FORMS", false,
                    String.format("Ошибка исследования форм: %s", e.getMessage()));
        }
    }
}
