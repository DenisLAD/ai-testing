package ru.sbrf.uddk.ai.testing.infrastructure.action;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;

/**
 * Действие: Прокрутка вниз
 */
@Slf4j
@Component
public class ScrollDownAction extends BaseAgentAction {

    @Override
    public String getType() {
        return "SCROLL_DOWN";
    }

    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing ScrollDownAction");

        try {
            executeJavaScript(driver, "window.scrollBy(0, 500);");
            Thread.sleep(500);

            return createActionLog("SCROLL_DOWN", true, "Страница прокручена вниз на 500px");

        } catch (Exception e) {
            log.error("ScrollDownAction failed: {}", e.getMessage());
            return createActionLog("SCROLL_DOWN", false,
                    String.format("Ошибка прокрутки: %s", e.getMessage()));
        }
    }
}
