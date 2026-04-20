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
            driver.get(target);
            
            // Ждем загрузки страницы
            getWait(driver).until(webDriver -> 
                ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete"));

            return createActionLog("NAVIGATE_TO", true,
                    String.format("Успешно перешел на: %s", target));

        } catch (Exception e) {
            log.error("NavigateToAction failed: {}", e.getMessage());
            return createActionLog("NAVIGATE_TO", false,
                    String.format("Ошибка перехода на %s: %s", target, e.getMessage()));
        }
    }
}
