package ru.sbrf.uddk.ai.testing.infrastructure.action;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

/**
 * Действие: Вперед в браузере
 */
@Slf4j
@Component
public class NavigateForwardAction extends BaseAgentAction {

    @Override
    public String getType() {
        return "NAVIGATE_FORWARD";
    }

    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing NavigateForwardAction");

        try {
            // Скриншот до
            String screenshotBefore = takeScreenshotBefore(driver);
            
            driver.navigate().forward();
            Thread.sleep(1000);
            
            // Скриншот после
            String screenshotAfter = takeScreenshotAfter(driver);

            AgentAction logEntry = createActionLog("NAVIGATE_FORWARD", true,
                    "Успешно перешел вперед: " + driver.getCurrentUrl());
            logEntry.setScreenshotBefore(screenshotBefore);
            logEntry.setScreenshotAfter(screenshotAfter);
            return logEntry;

        } catch (Exception e) {
            log.error("NavigateForwardAction failed: {}", e.getMessage());
            return createActionLog("NAVIGATE_FORWARD", false,
                    String.format("Ошибка навигации вперед: %s", e.getMessage()));
        }
    }
}
