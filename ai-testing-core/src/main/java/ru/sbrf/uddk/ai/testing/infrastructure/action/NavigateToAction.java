package ru.sbrf.uddk.ai.testing.infrastructure.action;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;

/**
 * Действие: Переход на URL
 */
@Slf4j
@Component
public class NavigateToAction extends BaseAgentAction {

    @Override
    public String getType() {
        return "NAVIGATE_TO";
    }

    @Override
    public AgentAction execute(WebDriver driver) {
        String resolvedUrl = resolveNavigationUrl(driver, target);
        log.info("Executing NavigateToAction to: {} (resolved: {})", target, resolvedUrl);

        try {
            // Скриншот до
            String screenshotBefore = takeScreenshotBefore(driver);
            
            driver.get(resolvedUrl);
            
            // Ждем загрузки страницы
            getWait(driver).until(webDriver -> 
                ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete"));
            
            // Скриншот после
            String screenshotAfter = takeScreenshotAfter(driver);

            AgentAction logEntry = createActionLog("NAVIGATE_TO", true,
                    String.format("Успешно перешел на: %s", resolvedUrl));
            logEntry.setScreenshotBefore(screenshotBefore);
            logEntry.setScreenshotAfter(screenshotAfter);
            return logEntry;

        } catch (Exception e) {
            log.error("NavigateToAction failed: {}", e.getMessage());
            return createActionLog("NAVIGATE_TO", false,
                    String.format("Ошибка перехода на %s: %s", resolvedUrl, e.getMessage()));
        }
    }

    private String resolveNavigationUrl(WebDriver driver, String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }

        String currentUrl = driver.getCurrentUrl();
        try {
            java.net.URI base = java.net.URI.create(currentUrl);
            String path = trimmed.startsWith("/") ? trimmed : trimmed;
            return base.resolve(path).toString();
        } catch (Exception e) {
            int schemeEnd = currentUrl.indexOf("://");
            int pathStart = schemeEnd >= 0 ? currentUrl.indexOf('/', schemeEnd + 3) : currentUrl.indexOf('/');
            String origin = pathStart > 0 ? currentUrl.substring(0, pathStart) : currentUrl;
            return origin + (trimmed.startsWith("/") ? trimmed : "/" + trimmed);
        }
    }
}
