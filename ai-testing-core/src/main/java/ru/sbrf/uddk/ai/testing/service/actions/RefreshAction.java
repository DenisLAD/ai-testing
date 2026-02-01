package ru.sbrf.uddk.ai.testing.service.actions;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

@Slf4j
public class RefreshAction extends BaseAgentAction {
    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing RefreshAction");

        try {
            driver.navigate().refresh();

            return createActionLog("REFRESH", true, "Успешно обновил страницу");

        } catch (Exception e) {
            log.error("RefreshAction failed", e);
            return createActionLog("REFRESH", false,
                    "Ошибка обновления страницы: " + e.getMessage());
        }
    }
}
