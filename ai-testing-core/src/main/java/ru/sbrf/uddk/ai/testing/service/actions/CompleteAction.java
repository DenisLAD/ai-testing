package ru.sbrf.uddk.ai.testing.service.actions;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

@Slf4j
public class CompleteAction extends BaseAgentAction {
    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing CompleteAction");

        try {
            return createActionLog("COMPLETE", true,
                    "Тестирование завершено успешно");

        } catch (Exception e) {
            log.error("CompleteAction failed", e);
            return createActionLog("COMPLETE", false,
                    "Ошибка завершения тестирования: " + e.getMessage());
        }
    }
}
