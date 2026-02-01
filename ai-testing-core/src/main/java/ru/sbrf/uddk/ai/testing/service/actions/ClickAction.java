package ru.sbrf.uddk.ai.testing.service.actions;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

@Slf4j
public class ClickAction extends BaseAgentAction {
    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing ClickAction on: {}", target);

        try {
            WebElement element = findElement(driver, target);
            element.click();

            return createActionLog("CLICK", true,
                    String.format("Успешно кликнул на элемент: %s", target));

        } catch (Exception e) {
            log.error("ClickAction failed {}", e.getMessage());
            return createActionLog("CLICK", false,
                    String.format("Ошибка клика на элемент %s: %s", target, e.getMessage()));
        }
    }
}
