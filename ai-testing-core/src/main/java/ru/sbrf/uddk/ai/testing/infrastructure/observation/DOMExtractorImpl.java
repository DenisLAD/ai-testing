package ru.sbrf.uddk.ai.testing.infrastructure.observation;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;
import ru.sbrf.uddk.ai.testing.domain.observation.DOMExtractor;
import ru.sbrf.uddk.ai.testing.infrastructure.webdriver.WebDriverProviderAdapter;

import java.util.regex.Pattern;

/**
 * Сервис извлечения DOM
 * Реализация по умолчанию
 */
@Slf4j
@Component
public class DOMExtractorImpl implements DOMExtractor {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern EMPTY_TAG_PATTERN = Pattern.compile("<(div|span|p|li|td|th)[^>]*>\\s*</\\1>");
    private final WebDriverProviderAdapter webDriverProvider;
    private final int maxSizePage = 16000;

    public DOMExtractorImpl(WebDriverProviderAdapter webDriverProvider) {
        this.webDriverProvider = webDriverProvider;
    }

    @Override
    public String extractVisibleDOM() {
        try {
            WebDriver driver = webDriverProvider.get();
            JavascriptExecutor js = (JavascriptExecutor) driver;

            String script = """
                    function extractVisibleDOM() {
                        function isElementVisible(el) {
                            if (!el || el.nodeType !== 1) return false;
                            const rect = el.getBoundingClientRect();
                            if (rect.width === 0 || rect.height === 0) return false;
                            
                            const isInViewport = (
                                rect.top >= -100 &&
                                rect.left >= -100 &&
                                rect.bottom <= (window.innerHeight + 100) &&
                                rect.right <= (window.innerWidth + 100)
                            );
                            
                            if (!isInViewport) return false;
                            
                            const style = window.getComputedStyle(el);
                            if (style.display === 'none' ||
                                style.visibility === 'hidden' ||
                                style.opacity === '0') {
                                return false;
                            }
                            
                            return true;
                        }
                        
                        function cloneVisibleElements(node) {
                            if (node.nodeType === Node.TEXT_NODE) {
                                return node.cloneNode(true);
                            }
                            
                            if (node.nodeType !== Node.ELEMENT_NODE) {
                                return null;
                            }
                            
                            const element = node;
                            if (!isElementVisible(element)) return null;
                            
                            const tagName = element.tagName.toLowerCase();
                            if (['script', 'style', 'link', 'meta', 'noscript'].includes(tagName)) {
                                return null;
                            }
                            
                            const clone = element.cloneNode(false);
                            const keepAttrs = [
                                'id', 'name', 'class', 'type', 'value', 'placeholder',
                                'href', 'src', 'alt', 'title', 'role', 'aria-label',
                                'data-testid', 'data-qa', 'data-cy', 'for', 'tabindex'
                            ];
                            
                            for (let i = 0; i < element.attributes.length; i++) {
                                const attr = element.attributes[i];
                                if (keepAttrs.includes(attr.name.toLowerCase())) {
                                    clone.setAttribute(attr.name, attr.value);
                                }
                            }
                            
                            for (let child of element.childNodes) {
                                const childClone = cloneVisibleElements(child);
                                if (childClone) {
                                    clone.appendChild(childClone);
                                }
                            }
                            
                            if (clone.childNodes.length === 0 &&
                                ['input', 'img', 'br', 'hr'].includes(tagName)) {
                                return clone;
                            }
                            
                            if (clone.childNodes.length > 0 ||
                                (element.textContent && element.textContent.trim().length > 0)) {
                                return clone;
                            }
                            
                            return null;
                        }
                        
                        const startElement = document.body || document.documentElement;
                        const visibleDOM = cloneVisibleElements(startElement);
                        return visibleDOM ? visibleDOM.outerHTML : '';
                    }
                    
                    return extractVisibleDOM();
                    """;

            String visibleDOM = (String) js.executeScript(script);
            
            if (visibleDOM == null || visibleDOM.isEmpty()) {
                return extractFallback();
            }
            
            return optimizeForAI(visibleDOM);
            
        } catch (Exception e) {
            log.error("Failed to extract visible DOM", e);
            return extractFallback();
        }
    }
    
    @Override
    public String extractCompactDOM() {
        try {
            WebDriver driver = webDriverProvider.get();
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            String script = """
                    function getCompactDOM() {
                        const elements = [];
                        const allElements = document.querySelectorAll('*');
                        
                        for (const el of allElements) {
                            if (el.offsetParent === null) continue;
                            
                            const rect = el.getBoundingClientRect();
                            if (rect.width < 10 || rect.height < 10) continue;
                            
                            const tag = el.tagName.toLowerCase();
                            const isInteractive = [
                                'a', 'button', 'input', 'select', 'textarea',
                                'details', 'summary'
                            ].includes(tag);
                            
                            const hasText = el.textContent && el.textContent.trim().length > 0;
                            
                            if (!isInteractive && !hasText) continue;
                            
                            let repr = '<' + tag;
                            if (el.id) repr += ' id="' + el.id + '"';
                            if (el.className) repr += ' class="' + el.className.split(' ')[0] + '"';
                            if (el.getAttribute('role')) repr += ' role="' + el.getAttribute('role') + '"';
                            if (el.getAttribute('aria-label')) repr += ' aria-label="' + el.getAttribute('aria-label') + '"';
                            if (el.href && el.href !== '#') repr += ' href="' + el.href + '"';
                            if (el.type) repr += ' type="' + el.type + '"';
                            if (el.value) repr += ' value="' + el.value + '"';
                            repr += '>';
                            
                            const text = (el.innerText || '').trim().substring(0, 50);
                            if (text) repr += ' "' + text + '"';
                            
                            elements.push(repr);
                        }
                        
                        return elements.slice(0, 100).join('\\n');
                    }
                    
                    return getCompactDOM();
                    """;
            
            return (String) js.executeScript(script);
            
        } catch (Exception e) {
            log.error("Failed to extract compact DOM", e);
            return "";
        }
    }
    
    @Override
    public String optimizeForAI(String dom) {
        if (dom == null || dom.isEmpty()) {
            return "";
        }
        
        String result = dom;
        
        // 1. Удаляем html, head, body теги
        result = result.replaceAll("(?i)<html[^>]*>", "");
        result = result.replaceAll("(?i)</html>", "");
        result = result.replaceAll("(?i)<head[^>]*>.*?</head>", "");
        result = result.replaceAll("(?i)<body[^>]*>", "");
        result = result.replaceAll("(?i)</body>", "");
        
        // 2. Нормализуем пробелы
        result = WHITESPACE_PATTERN.matcher(result).replaceAll(" ");
        
        // 3. Удаляем комментарии
        result = result.replaceAll("<!--.*?-->", "");
        
        // 4. Удаляем пустые теги
        result = EMPTY_TAG_PATTERN.matcher(result).replaceAll("");
        
        // 5. Обрезаем если слишком большой
        if (result.length() > maxSizePage) {
            result = result.substring(0, maxSizePage) + "... [TRUNCATED]";
        }
        
        // 6. Добавляем маркеры
        result = "<!-- VISIBLE DOM START -->\n" + result.trim() + "\n<!-- VISIBLE DOM END -->";
        
        return result;
    }
    
    private String extractFallback() {
        try {
            WebDriver driver = webDriverProvider.get();
            JavascriptExecutor js = (JavascriptExecutor) driver;
            return (String) js.executeScript("return document.body ? document.body.innerHTML : '';");
        } catch (Exception e) {
            log.error("Fallback DOM extraction failed", e);
            return "";
        }
    }
}
