package ru.sbrf.uddk.ai.testing.infrastructure.action;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;

/**
 * Действие: Обновление страницы
 */
@Slf4j
@Component
public class RefreshAction extends BaseAgentAction {

    @Override
    public String getType() {
        return "REFRESH";
    }

    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing RefreshAction");

        try {
            // Скриншот до
            String screenshotBefore = takeScreenshotBefore(driver);
            
            driver.navigate().refresh();
            
            // Ждем загрузки
            getWait(driver).until(webDriver ->
                ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete"));
            
            // Скриншот после
            String screenshotAfter = takeScreenshotAfter(driver);

            AgentAction logEntry = createActionLog("REFRESH", true, "Страница обновлена");
            logEntry.setScreenshotBefore(screenshotBefore);
            logEntry.setScreenshotAfter(screenshotAfter);
            return logEntry;

        } catch (Exception e) {
            log.error("RefreshAction failed: {}", e.getMessage());
            return createActionLog("REFRESH", false,
                    String.format("Ошибка обновления: %s", e.getMessage()));
        }
    }
}
