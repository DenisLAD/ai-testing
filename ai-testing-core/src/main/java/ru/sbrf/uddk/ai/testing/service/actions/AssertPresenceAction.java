package ru.sbrf.uddk.ai.testing.service.actions;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

@Slf4j
public class AssertPresenceAction extends BaseAgentAction {
    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing AssertPresenceAction for: {}", target);

        try {
            WebElement element = findElement(driver, target);
            boolean isPresent = element.isDisplayed();

            String message = isPresent ?
                    String.format("Элемент %s присутствует на странице", target) :
                    String.format("Элемент %s отсутствует на странице", target);

            return createActionLog("ASSERT_PRESENCE", isPresent, message);

        } catch (Exception e) {
            log.error("AssertPresenceAction failed", e);
            return createActionLog("ASSERT_PRESENCE", false,
                    String.format("Элемент %s не найден: %s", target, e.getMessage()));
        }
    }
}
