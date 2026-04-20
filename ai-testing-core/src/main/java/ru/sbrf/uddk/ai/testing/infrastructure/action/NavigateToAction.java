package ru.sbrf.uddk.ai.testing.infrastructure.action;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;

/**
 * Действие: Переход на URL
 */
@Slf4j
@Component
public class NavigateToAction extends BaseAgentAction {

    @Override
    public String getType() {
        return "NAVIGATE_TO";
    }

    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing NavigateToAction to: {}", target);

        try {
            // Скриншот до
            String screenshotBefore = takeScreenshotBefore(driver);
            
            driver.get(target);
            
            // Ждем загрузки страницы
            getWait(driver).until(webDriver -> 
                ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete"));
            
            // Скриншот после
            String screenshotAfter = takeScreenshotAfter(driver);

            AgentAction logEntry = createActionLog("NAVIGATE_TO", true,
                    String.format("Успешно перешел на: %s", target));
            logEntry.setScreenshotBefore(screenshotBefore);
            logEntry.setScreenshotAfter(screenshotAfter);
            return logEntry;

        } catch (Exception e) {
            log.error("NavigateToAction failed: {}", e.getMessage());
            return createActionLog("NAVIGATE_TO", false,
                    String.format("Ошибка перехода на %s: %s", target, e.getMessage()));
        }
    }
}
