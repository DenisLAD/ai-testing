package ru.sbrf.uddk.ai.testing.infrastructure.action;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;

/**
 * Действие: Проверка текста элемента
 */
@Slf4j
@Component
public class AssertTextAction extends BaseAgentAction {

    @Override
    public String getType() {
        return "ASSERT_TEXT";
    }

    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing AssertTextAction on: {} with expected: {}", target, value);

        try {
            // Скриншот до
            String screenshotBefore = takeScreenshotBefore(driver);
            
            WebElement element = findElement(driver, target);
            String actualText = element.getText();
            boolean matches = actualText != null && actualText.contains(value);
            
            // Скриншот после
            String screenshotAfter = takeScreenshotAfter(driver);

            String message = matches 
                ? String.format("Текст элемента '%s' содержит '%s'", target, value)
                : String.format("Текст элемента '%s' ('%s') не содержит '%s'", target, actualText, value);

            AgentAction logEntry = createActionLog("ASSERT_TEXT", matches, message);
            logEntry.setScreenshotBefore(screenshotBefore);
            logEntry.setScreenshotAfter(screenshotAfter);
            return logEntry;

        } catch (Exception e) {
            log.error("AssertTextAction failed: {}", e.getMessage());
            return createActionLog("ASSERT_TEXT", false,
                    String.format("Ошибка проверки текста: %s", e.getMessage()));
        }
    }
}
