package ru.sbrf.uddk.ai.testing.service.actions;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

@Slf4j
public class NavigateToAction extends BaseAgentAction {
    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing NavigateToAction to: {}", target);

        try {
            driver.navigate().to(target);

            return createActionLog("NAVIGATE_TO", true,
                    String.format("Успешно перешел по URL: %s", target));

        } catch (Exception e) {
            log.error("NavigateToAction failed", e);
            return createActionLog("NAVIGATE_TO", false,
                    String.format("Ошибка перехода по URL %s: %s", target, e.getMessage()));
        }
    }
}
