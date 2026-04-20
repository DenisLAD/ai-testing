package ru.sbrf.uddk.ai.testing.infrastructure.action;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;

/**
 * Действие: Завершение тестирования
 */
@Slf4j
@Component
public class CompleteAction extends BaseAgentAction {

    @Override
    public String getType() {
        return "COMPLETE";
    }

    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing CompleteAction - тестирование завершено");

        return createActionLog("COMPLETE", true,
                "Тестирование завершено успешно. " + (reason != null ? reason : ""));
    }
}
