package ru.sbrf.uddk.ai.testing.infrastructure.action;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

/**
 * Действие: Назад в браузере
 */
@Slf4j
@Component
public class NavigateBackAction extends BaseAgentAction {

    @Override
    public String getType() {
        return "NAVIGATE_BACK";
    }

    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing NavigateBackAction");

        try {
            // Скриншот до
            String screenshotBefore = takeScreenshotBefore(driver);
            
            driver.navigate().back();
            Thread.sleep(1000);
            
            // Скриншот после
            String screenshotAfter = takeScreenshotAfter(driver);

            AgentAction logEntry = createActionLog("NAVIGATE_BACK", true,
                    "Успешно вернулся на предыдущую страницу: " + driver.getCurrentUrl());
            logEntry.setScreenshotBefore(screenshotBefore);
            logEntry.setScreenshotAfter(screenshotAfter);
            return logEntry;

        } catch (Exception e) {
            log.error("NavigateBackAction failed: {}", e.getMessage());
            return createActionLog("NAVIGATE_BACK", false,
                    String.format("Ошибка навигации назад: %s", e.getMessage()));
        }
    }
}
