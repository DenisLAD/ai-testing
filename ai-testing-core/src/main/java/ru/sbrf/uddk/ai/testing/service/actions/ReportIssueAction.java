package ru.sbrf.uddk.ai.testing.service.actions;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

@Slf4j
public class ReportIssueAction extends BaseAgentAction {
    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing ReportIssueAction: {}", value);

        try {
            // Создаем запись об issue
            AgentAction actionLog = createActionLog("REPORT_ISSUE", true, String.format("Сообщил о проблеме: %s", value));

            // Можно добавить дополнительную логику, например, скриншот

            return actionLog;

        } catch (Exception e) {
            log.error("ReportIssueAction failed", e);
            return createActionLog("REPORT_ISSUE", false,
                    String.format("Ошибка сообщения о проблеме: %s", e.getMessage()));
        }
    }
}
