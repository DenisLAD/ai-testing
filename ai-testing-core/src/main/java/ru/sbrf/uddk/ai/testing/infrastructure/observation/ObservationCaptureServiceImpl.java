package ru.sbrf.uddk.ai.testing.infrastructure.observation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sbrf.uddk.ai.testing.domain.model.ActionHistory;
import ru.sbrf.uddk.ai.testing.domain.model.InteractiveElement;
import ru.sbrf.uddk.ai.testing.domain.model.Issue;
import ru.sbrf.uddk.ai.testing.domain.model.Observation;
import ru.sbrf.uddk.ai.testing.domain.observation.DOMExtractor;
import ru.sbrf.uddk.ai.testing.domain.observation.ElementScanner;
import ru.sbrf.uddk.ai.testing.domain.observation.ObservationCaptureService;
import ru.sbrf.uddk.ai.testing.domain.observation.ScreenshotService;
import ru.sbrf.uddk.ai.testing.infrastructure.webdriver.WebDriverProviderAdapter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Сервис захвата наблюдений
 * Координирует сбор информации о состоянии страницы
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObservationCaptureServiceImpl implements ObservationCaptureService {

    private final ScreenshotService screenshotService;
    private final DOMExtractor domExtractor;
    private final ElementScanner elementScanner;
    private final WebDriverProviderAdapter webDriverProvider;
    
    // Временные данные для текущей сессии
    private String currentSessionId = "default";
    private String currentGoal = "EXPLORATORY";
    private List<ActionHistory> actionHistory = Collections.emptyList();
    private List<Issue> issues = Collections.emptyList();
    private Double progress = 0.0;

    @Override
    public Observation capture() {
        try {
            String url = getCurrentUrl();
            String title = getPageTitle();
            String visibleDOM = domExtractor.extractVisibleDOM();
            String screenshot = screenshotService.captureScreenshot();
            List<InteractiveElement> elements = elementScanner.scanVisibleElements(currentSessionId);
            
            return Observation.builder()
                .url(url)
                .title(title)
                .visibleDOM(visibleDOM)
                .screenshot(screenshot)
                .elements(elements)
                .previousActions(actionHistory)
                .issues(issues)
                .progress(progress)
                .goalDescription(currentGoal)
                .timestamp(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to capture observation", e);
            return Observation.builder()
                .errorMessage(e.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
        }
    }
    
    @Override
    public String captureScreenshot() {
        return screenshotService.captureScreenshot();
    }
    
    @Override
    public String captureDOM() {
        return domExtractor.extractVisibleDOM();
    }
    
    // Конфигурационные методы
    public void setSessionContext(String sessionId, String goal) {
        this.currentSessionId = sessionId;
        this.currentGoal = goal;
    }
    
    public void setActionHistory(List<ActionHistory> history) {
        this.actionHistory = history;
    }
    
    public void setIssues(List<Issue> issues) {
        this.issues = issues;
    }
    
    public void setProgress(Double progress) {
        this.progress = progress;
    }
    
    public void clearCache() {
        if (elementScanner instanceof ElementScannerImpl scanner) {
            scanner.clearCache(currentSessionId);
        }
    }
    
    private String getCurrentUrl() {
        try {
            return webDriverProvider.get().getCurrentUrl();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private String getPageTitle() {
        try {
            return webDriverProvider.get().getTitle();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
