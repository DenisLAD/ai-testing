package ru.sbrf.uddk.ai.testing.infrastructure.action;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;

/**
 * Действие: Проверка наличия элемента
 */
@Slf4j
@Component
public class AssertPresenceAction extends BaseAgentAction {

    @Override
    public String getType() {
        return "ASSERT_PRESENCE";
    }

    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing AssertPresenceAction on: {}", target);

        try {
            // Скриншот до
            String screenshotBefore = takeScreenshotBefore(driver);
            
            WebElement element = findElement(driver, target);
            boolean isPresent = element.isDisplayed();
            
            // Скриншот после
            String screenshotAfter = takeScreenshotAfter(driver);

            AgentAction logEntry = createActionLog("ASSERT_PRESENCE", isPresent,
                    String.format("Элемент %s: %s", target, isPresent ? "найден" : "не найден"));
            logEntry.setScreenshotBefore(screenshotBefore);
            logEntry.setScreenshotAfter(screenshotAfter);
            return logEntry;

        } catch (Exception e) {
            log.error("AssertPresenceAction failed: {}", e.getMessage());
            return createActionLog("ASSERT_PRESENCE", false,
                    String.format("Элемент %s не найден: %s", target, e.getMessage()));
        }
    }
}
