package ru.sbrf.uddk.ai.testing.infrastructure.action;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

/**
 * Действие: Прокрутка вверх
 */
@Slf4j
@Component
public class ScrollUpAction extends BaseAgentAction {

    @Override
    public String getType() {
        return "SCROLL_UP";
    }

    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing ScrollUpAction");

        try {
            // Скриншот до
            String screenshotBefore = takeScreenshotBefore(driver);
            
            executeJavaScript(driver, "window.scrollBy(0, -500);");
            Thread.sleep(500);
            
            // Скриншот после
            String screenshotAfter = takeScreenshotAfter(driver);

            AgentAction logEntry = createActionLog("SCROLL_UP", true, "Страница прокручена вверх на 500px");
            logEntry.setScreenshotBefore(screenshotBefore);
            logEntry.setScreenshotAfter(screenshotAfter);
            return logEntry;

        } catch (Exception e) {
            log.error("ScrollUpAction failed: {}", e.getMessage());
            return createActionLog("SCROLL_UP", false,
                    String.format("Ошибка прокрутки: %s", e.getMessage()));
        }
    }
}
