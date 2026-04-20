package ru.sbrf.uddk.ai.testing.infrastructure.observation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;
import ru.sbrf.uddk.ai.testing.domain.model.ElementBounds;
import ru.sbrf.uddk.ai.testing.domain.model.InteractiveElement;
import ru.sbrf.uddk.ai.testing.domain.observation.ElementScanner;
import ru.sbrf.uddk.ai.testing.infrastructure.webdriver.WebDriverProviderAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис сканирования элементов
 * Реализация по умолчанию
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElementScannerImpl implements ElementScanner {

    private final WebDriverProviderAdapter webDriverProvider;
    private final Map<String, List<InteractiveElement>> elementCache = new ConcurrentHashMap<>();

    @Override
    public List<InteractiveElement> scanVisibleElements(String sessionId) {
        String cacheKey = sessionId + "_" + getCurrentUrl();
        
        return elementCache.computeIfAbsent(cacheKey, key -> doScanVisibleElements());
    }
    
    @Override
    public boolean isVisible(String selector) {
        try {
            WebDriver driver = webDriverProvider.get();
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            String script = """
                    function checkVisibility(selector) {
                        const el = document.querySelector(selector);
                        if (!el) return false;
                        
                        const rect = el.getBoundingClientRect();
                        const style = window.getComputedStyle(el);
                        
                        return (
                            rect.width > 0 &&
                            rect.height > 0 &&
                            style.display !== 'none' &&
                            style.visibility !== 'hidden' &&
                            style.opacity !== '0'
                        );
                    }
                    return checkVisibility(arguments[0]);
                    """;
            
            return (Boolean) js.executeScript(script, selector);
            
        } catch (Exception e) {
            log.error("Failed to check visibility", e);
            return false;
        }
    }
    
    public void clearCache(String sessionId) {
        elementCache.keySet().removeIf(key -> key.startsWith(sessionId + "_"));
    }
    
    private String getCurrentUrl() {
        try {
            return webDriverProvider.get().getCurrentUrl();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private List<InteractiveElement> doScanVisibleElements() {
        try {
            WebDriver driver = webDriverProvider.get();
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            String script = """
                    function getVisibleInteractiveElements() {
                        const elements = [];
                        const allElements = document.querySelectorAll('*');
                        
                        for (const el of allElements) {
                            const rect = el.getBoundingClientRect();
                            const style = window.getComputedStyle(el);
                            
                            const isVisible = (
                                rect.width > 10 &&
                                rect.height > 10 &&
                                rect.top >= 0 &&
                                rect.left >= 0 &&
                                rect.bottom <= (window.innerHeight || document.documentElement.clientHeight) &&
                                rect.right <= (window.innerWidth || document.documentElement.clientWidth) &&
                                style.display !== 'none' &&
                                style.visibility !== 'hidden' &&
                                style.opacity !== '0'
                            );
                            
                            if (!isVisible) continue;
                            
                            const tagName = el.tagName.toLowerCase();
                            const isInteractiveByTag = [
                                'a', 'button', 'input', 'select', 'textarea',
                                'details', 'summary', 'video', 'audio'
                            ].includes(tagName);
                            
                            const hasRole = el.getAttribute('role');
                            const interactiveRoles = ['button', 'link', 'textbox', 'checkbox', 'radio'];
                            const hasInteractiveRole = hasRole && interactiveRoles.includes(hasRole.toLowerCase());
                            
                            const hasClickableHandler = el.onclick || el.getAttribute('onclick');
                            
                            if (!isInteractiveByTag && !hasInteractiveRole && !hasClickableHandler) continue;
                            
                            elements.push({
                                tagName: tagName,
                                id: el.id || '',
                                className: el.className ? el.className.split(' ')[0] : '',
                                text: (el.innerText || el.textContent || '').trim().substring(0, 100),
                                type: el.type || '',
                                placeholder: el.placeholder || '',
                                href: el.href || '',
                                selector: generateSelector(el),
                                bounds: {
                                    x: Math.round(rect.x),
                                    y: Math.round(rect.y),
                                    width: Math.round(rect.width),
                                    height: Math.round(rect.height),
                                    top: Math.round(rect.top),
                                    right: Math.round(rect.right),
                                    bottom: Math.round(rect.bottom),
                                    left: Math.round(rect.left)
                                }
                            });
                        }
                        
                        return elements;
                    }
                    
                    function generateSelector(el) {
                        if (el.id) return '#' + el.id;
                        if (el.className) return el.tagName.toLowerCase() + '.' + el.className.split(' ')[0];
                        return el.tagName.toLowerCase();
                    }
                    
                    return getVisibleInteractiveElements();
                    """;
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawElements = (List<Map<String, Object>>) js.executeScript(script);
            
            return rawElements.stream()
                .map(this::mapToInteractiveElement)
                .toList();
            
        } catch (Exception e) {
            log.error("Failed to scan visible elements", e);
            return new ArrayList<>();
        }
    }
    
    private InteractiveElement mapToInteractiveElement(Map<String, Object> raw) {
        return InteractiveElement.builder()
            .tagName((String) raw.get("tagName"))
            .id((String) raw.get("id"))
            .className((String) raw.get("className"))
            .text((String) raw.get("text"))
            .type((String) raw.get("type"))
            .placeholder((String) raw.get("placeholder"))
            .selector((String) raw.get("selector"))
            .visible(true)
            .enabled(true)
            .bounds(mapToBounds((Map<String, Object>) raw.get("bounds")))
            .build();
    }
    
    private ElementBounds mapToBounds(Map<String, Object> raw) {
        if (raw == null) {
            return ElementBounds.builder().build();
        }
        
        return ElementBounds.builder()
            .x(getInt(raw, "x"))
            .y(getInt(raw, "y"))
            .width(getInt(raw, "width"))
            .height(getInt(raw, "height"))
            .top(getInt(raw, "top"))
            .right(getInt(raw, "right"))
            .bottom(getInt(raw, "bottom"))
            .left(getInt(raw, "left"))
            .build();
    }
    
    private int getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number num) {
            return num.intValue();
        }
        return 0;
    }
}
