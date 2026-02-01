package ru.sbrf.uddk.ai.testing.service.actions;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

@Slf4j
public class NavigateBackAction extends BaseAgentAction {
    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing NavigateBackAction");

        try {
            driver.navigate().back();

            return createActionLog("NAVIGATE_BACK", true,
                    "Успешно вернулся на предыдущую страницу");

        } catch (Exception e) {
            log.error("NavigateBackAction failed", e);
            return createActionLog("NAVIGATE_BACK", false,
                    "Ошибка возврата на предыдущую страницу: " + e.getMessage());
        }
    }
}
