package ru.sbrf.uddk.ai.testing.infrastructure.action;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

/**
 * Действие: Сообщение о проблеме
 */
@Slf4j
@Component
public class ReportIssueAction extends BaseAgentAction {

    @Override
    public String getType() {
        return "REPORT_ISSUE";
    }

    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing ReportIssueAction: {}", value);

        try {
            // Скриншот до
            String screenshotBefore = takeScreenshotBefore(driver);
            
            // Создаем запись об issue
            AgentAction actionLog = createActionLog("REPORT_ISSUE", true, 
                String.format("Сообщил о проблеме: %s", value));
            actionLog.setScreenshotBefore(screenshotBefore);
            
            return actionLog;

        } catch (Exception e) {
            log.error("ReportIssueAction failed", e);
            return createActionLog("REPORT_ISSUE", false,
                    String.format("Ошибка сообщения о проблеме: %s", e.getMessage()));
        }
    }
}
