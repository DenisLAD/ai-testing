package ru.sbrf.uddk.ai.testing.service.actions;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

@Slf4j
public class NavigateForwardAction extends BaseAgentAction {
    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing NavigateForwardAction");

        try {
            driver.navigate().forward();

            return createActionLog("NAVIGATE_FORWARD", true,
                    "Успешно перешел вперед по истории");

        } catch (Exception e) {
            log.error("NavigateForwardAction failed", e);
            return createActionLog("NAVIGATE_FORWARD", false,
                    "Ошибка перехода вперед: " + e.getMessage());
        }
    }
}
