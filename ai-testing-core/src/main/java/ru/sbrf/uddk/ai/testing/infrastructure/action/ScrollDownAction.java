package ru.sbrf.uddk.ai.testing.infrastructure.action;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;

/**
 * Действие: Прокрутка вниз
 */
@Slf4j
@Component
public class ScrollDownAction extends BaseAgentAction {

    @Override
    public String getType() {
        return "SCROLL_DOWN";
    }

    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing ScrollDownAction");

        try {
            // Скриншот до
            String screenshotBefore = takeScreenshotBefore(driver);
            
            executeJavaScript(driver, """
                    const main = document.querySelector('main');
                    if (main) {
                        const scrollables = main.querySelectorAll('*');
                        for (const node of scrollables) {
                            if (node.scrollHeight > node.clientHeight + 20) {
                                node.scrollTop = node.scrollHeight;
                            }
                        }
                    }
                    window.scrollBy(0, Math.max(500, window.innerHeight * 0.8));
                    window.scrollTo(0, document.body.scrollHeight);
                    """);
            Thread.sleep(600);
            
            // Скриншот после
            String screenshotAfter = takeScreenshotAfter(driver);

            AgentAction logEntry = createActionLog("SCROLL_DOWN", true, "Страница прокручена вниз на 500px");
            logEntry.setScreenshotBefore(screenshotBefore);
            logEntry.setScreenshotAfter(screenshotAfter);
            return logEntry;

        } catch (Exception e) {
            log.error("ScrollDownAction failed: {}", e.getMessage());
            return createActionLog("SCROLL_DOWN", false,
                    String.format("Ошибка прокрутки: %s", e.getMessage()));
        }
    }
}
