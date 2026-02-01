package ru.sbrf.uddk.ai.testing.service;

import ru.sbrf.uddk.ai.testing.entity.ElementBounds;
import ru.sbrf.uddk.ai.testing.entity.InteractiveElement;
import ru.sbrf.uddk.ai.testing.entity.TestSession;
import ru.sbrf.uddk.ai.testing.entity.consts.InteractionType;
import ru.sbrf.uddk.ai.testing.model.AgentObservation;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ObservationService implements InitializingBean, DisposableBean {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    Map<String, Set<String>> sessionElementCache = new ConcurrentHashMap<>();
    @Value("${app.screenshots.dir:./screenshots}")
    private String screenshotDir;
    @Value("${app.screenshots.format:png}")
    private String screenshotFormat;
    @Value("${app.screenshots.max-size-kb:500}")
    private int maxScreenshotSizeKb;
    @Setter(onMethod_ = @Autowired)
    private SeleniumSupplierService seleniumServiceSupplier;
    private boolean cleanAttributes = true;
    private int maxSizePage = 16000;

    private void createScreenshotDirectory() {
        try {
            Path dirPath = Paths.get(screenshotDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                log.info("Created screenshot directory: {}", screenshotDir);
            }
        } catch (IOException e) {
            log.error("Failed to create screenshot directory", e);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        createScreenshotDirectory();
    }

    @Override
    public void destroy() throws Exception {

    }

    public AgentObservation captureObservation(WebDriver driver, TestSession session) {
        AgentObservation observation = new AgentObservation();

        try {
            // Базовая информация
            observation.setUrl(driver.getCurrentUrl());
            observation.setGoalDescription(session.getDescription());
            observation.setPageTitle(driver.getTitle());
            observation.setTimestamp(LocalDateTime.now());

            // Визуальная информация
            observation.setScreenshot(takeScreenshot(driver, null, session.getId().toString()));

            // DOM информация
            observation.setPageSource(extractRelevantHTML(driver));
            observation.setDomSnapshot(takeDomSnapshot(driver));

            // Элементы на странице
            observation.setVisibleElements(scanVisibleElements(driver, session.getId().toString()));

            // Контекст сессии
            observation.setPreviousActions(session.getRecentActions(10));
            observation.setDiscoveredIssues(session.getDiscoveredIssues());
            observation.setGoalProgress(calculateProgress(session, driver));
            observation.setWebDriver(driver);

        } catch (Exception e) {
            log.error("Failed to capture observation", e);
            observation.setErrorMessage(e.getMessage());
        }

        return observation;
    }

    private double calculateProgress(TestSession session, WebDriver driver) {
        try {
            return Math.min(1.0, session.getActions().size() / 30.0);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public String extractVisibleDOM(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            String script = """
                    function extractVisibleDOM() {
                        // Функция проверки видимости элемента
                        function isElementVisible(el) {
                            if (!el) return false;
                            
                            const rect = el.getBoundingClientRect();
                            if (rect.width === 0 || rect.height === 0) {
                                return false;
                            }
                            
                            // Проверяем, что элемент в viewport
                            const isInViewport = (
                                rect.top >= 0 &&
                                rect.left >= 0 &&
                                rect.bottom <= (window.innerHeight || document.documentElement.clientHeight) &&
                                rect.right <= (window.innerWidth || document.documentElement.clientWidth)
                            );
                            
                            if (!isInViewport) {
                                return false;
                            }
                            
                            // Проверяем стили видимости
                            const style = window.getComputedStyle(el);
                            if (style.display === 'none' || 
                                style.visibility === 'hidden' || 
                                style.opacity === '0') {
                                return false;
                            }
                            
                            return true;
                        }
                        
                        // Функция для клонирования только видимых элементов
                        function cloneVisibleElements(node) {
                            if (node.nodeType === Node.TEXT_NODE) {
                                // Сохраняем только текст видимых элементов
                                return node.cloneNode(true);
                            }
                            
                            if (node.nodeType !== Node.ELEMENT_NODE) {
                                return null;
                            }
                            
                            const element = node;
                            
                            // Пропускаем скрытые элементы
                            if (!isElementVisible(element)) {
                                return null;
                            }
                            
                            // Пропускаем скрипты и стили если нужно
                            const tagName = element.tagName.toLowerCase();
                            if (tagName === 'script' || tagName === 'style' || tagName === 'link') {
                                return null;
                            }
                            
                            // Клонируем элемент
                            const clone = element.cloneNode(false);
                            
                            // Копируем только важные атрибуты
                            const attributes = element.attributes;
                            const keepAttrs = [
                                'id', 'name', 'class', 'type', 'value', 'placeholder',
                                'href', 'src', 'alt', 'title', 'role', 'aria-*',
                                'data-testid', 'data-qa', 'data-cy', 'data-id',
                                'for', 'tabindex', 'disabled', 'readonly', 'required',
                                'checked', 'selected', 'multiple', 'maxlength', 'min', 'max'
                            ];
                            
                            for (let i = 0; i < attributes.length; i++) {
                                const attr = attributes[i];
                                const attrName = attr.name.toLowerCase();
                                
                                const shouldKeep = keepAttrs.some(keepAttr => {
                                    if (keepAttr.endsWith('*')) {
                                        return attrName.startsWith(keepAttr.slice(0, -1));
                                    }
                                    return attrName === keepAttr;
                                });
                                
                                if (shouldKeep) {
                                    clone.setAttribute(attr.name, attr.value);
                                }
                            }
                            
                            // Рекурсивно клонируем детей
                            for (let child of element.childNodes) {
                                const childClone = cloneVisibleElements(child);
                                if (childClone) {
                                    clone.appendChild(childClone);
                                }
                            }
                            
                            // Если элемент пустой после клонирования детей, но сам важен
                            // (например, input или img), оставляем его
                            if (clone.childNodes.length === 0 && 
                                ['input', 'img', 'br', 'hr', 'meta', 'link'].includes(tagName)) {
                                return clone;
                            }
                            
                            // Если элемент имеет видимый текст или детей
                            if (clone.childNodes.length > 0 || 
                                (element.textContent && element.textContent.trim().length > 0)) {
                                return clone;
                            }
                            
                            return null;
                        }
                        
                        // Начинаем с body или documentElement
                        const startElement = document.body || document.documentElement;
                        const visibleDOM = cloneVisibleElements(startElement);
                        
                        return visibleDOM ? visibleDOM.outerHTML : '';
                    }
                                    
                    return extractVisibleDOM();
                    """;

            String visibleDOM = (String) js.executeScript(script);

            // Если JavaScript не сработал, fallback
            if (visibleDOM == null || visibleDOM.isEmpty()) {
                visibleDOM = extractVisibleDOMFallback(driver);
            }

            // Оптимизируем размер
            visibleDOM = optimizeDOMSize(visibleDOM);

            log.debug("Extracted visible DOM: {} characters", visibleDOM.length());
            return visibleDOM;

        } catch (Exception e) {
            log.error("Failed to extract visible DOM, using fallback", e);
            return extractVisibleDOMFallback(driver);
        }
    }

    // Fallback метод для извлечения видимого DOM
    private String extractVisibleDOMFallback(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Более простой подход: находим все видимые элементы и строим из них DOM
            String script = """
                    function getVisibleElementsHTML() {
                        const allElements = document.querySelectorAll('*');
                        const visibleElements = [];
                        
                        for (const el of allElements) {
                            const rect = el.getBoundingClientRect();
                            const style = window.getComputedStyle(el);
                            
                            const isVisible = (
                                rect.width > 0 &&
                                rect.height > 0 &&
                                rect.top >= 0 &&
                                rect.left >= 0 &&
                                rect.bottom <= window.innerHeight &&
                                rect.right <= window.innerWidth &&
                                style.display !== 'none' &&
                                style.visibility !== 'hidden' &&
                                style.opacity !== '0'
                            );
                            
                            if (isVisible) {
                                // Берем только интерактивные элементы или элементы с текстом
                                const tagName = el.tagName.toLowerCase();
                                const isInteractive = [
                                    'a', 'button', 'input', 'select', 'textarea',
                                    'form', 'nav', 'header', 'footer', 'section',
                                    'article', 'main', 'aside', 'h1', 'h2', 'h3',
                                    'h4', 'h5', 'h6', 'p', 'span', 'div', 'li',
                                    'ul', 'ol', 'table', 'tr', 'td', 'th'
                                ].includes(tagName);
                                
                                const hasText = el.textContent && el.textContent.trim().length > 0;
                                const hasChildren = el.children.length > 0;
                                
                                if (isInteractive || hasText || hasChildren) {
                                    visibleElements.push(el);
                                }
                            }
                        }
                        
                        // Создаем фрагмент с видимыми элементами
                        const fragment = document.createDocumentFragment();
                        const addedIds = new Set();
                        
                        for (const el of visibleElements) {
                            // Избегаем дублирования родителей/детей
                            if (!addedIds.has(el.id) && !isChildOfAdded(el, addedIds)) {
                                const clone = el.cloneNode(true);
                                fragment.appendChild(clone);
                                if (el.id) addedIds.add(el.id);
                            }
                        }
                        
                        // Создаем временный div для получения HTML
                        const tempDiv = document.createElement('div');
                        tempDiv.appendChild(fragment);
                        
                        return tempDiv.innerHTML;
                    }
                                    
                    function isChildOfAdded(element, addedIds) {
                        let parent = element.parentElement;
                        while (parent) {
                            if (parent.id && addedIds.has(parent.id)) {
                                return true;
                            }
                            parent = parent.parentElement;
                        }
                        return false;
                    }
                                    
                    return getVisibleElementsHTML();
                    """;

            String result = (String) js.executeScript(script);
            return result != null ? optimizeDOMSize(result) : "";

        } catch (Exception e) {
            log.error("Fallback DOM extraction failed", e);
            return extractRelevantHTML(driver);
        }
    }

    // НОВЫЙ МЕТОД: Извлечение оптимизированного HTML для AI
    public String extractOptimizedHTML(WebDriver driver) {
        try {
            // Получаем видимый DOM
            String visibleDOM = extractVisibleDOM(driver);

            // Если видимый DOM слишком мал, добавляем контекст
            if (visibleDOM.length() < 1000) {
                String context = extractPageContext(driver);
                return visibleDOM + "\n<!-- Context information -->\n" + context;
            }

            return visibleDOM;

        } catch (Exception e) {
            log.error("Failed to extract optimized HTML", e);
            return extractRelevantHTML(driver);
        }
    }

    // Извлечение контекстной информации о странице
    private String extractPageContext(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            String script = """
                    function getPageContext() {
                        const context = {
                            url: window.location.href,
                            title: document.title,
                            metaDescription: document.querySelector('meta[name="description"]')?.content || '',
                            viewport: document.querySelector('meta[name="viewport"]')?.content || '',
                            language: document.documentElement.lang || 'en',
                            forms: document.forms.length,
                            links: document.links.length,
                            images: document.images.length,
                            scripts: document.scripts.length,
                            stylesheets: document.styleSheets.length,
                            cookies: document.cookie.length,
                            localStorage: Object.keys(localStorage).length,
                            sessionStorage: Object.keys(sessionStorage).length,
                            windowSize: {
                                width: window.innerWidth,
                                height: window.innerHeight
                            },
                            screenSize: {
                                width: screen.width,
                                height: screen.height
                            },
                            devicePixelRatio: window.devicePixelRatio,
                            userAgent: navigator.userAgent,
                            platform: navigator.platform,
                            online: navigator.onLine
                        };
                        
                        // Информация о структуре страницы
                        const structure = {
                            headers: {
                                h1: document.querySelectorAll('h1').length,
                                h2: document.querySelectorAll('h2').length,
                                h3: document.querySelectorAll('h3').length
                            },
                            sections: document.querySelectorAll('section, article, main, aside').length,
                            navigation: document.querySelectorAll('nav').length,
                            footers: document.querySelectorAll('footer').length,
                            lists: {
                                ul: document.querySelectorAll('ul').length,
                                ol: document.querySelectorAll('ol').length
                            },
                            tables: document.querySelectorAll('table').length
                        };
                        
                        // Собираем текстовый контент
                        const textContent = [];
                        const textElements = document.querySelectorAll('h1, h2, h3, p, li, td, th, span, div');
                        for (const el of textElements) {
                            if (el.textContent && el.textContent.trim().length > 0) {
                                textContent.push(el.textContent.trim().substring(0, 100));
                            }
                            if (textContent.length >= 10) break;
                        }
                        
                        return JSON.stringify({
                            context: context,
                            structure: structure,
                            textSamples: textContent.slice(0, 10)
                        }, null, 2);
                    }
                    return getPageContext();
                    """;

            String context = (String) js.executeScript(script);
            return context != null ? context : "{}";

        } catch (Exception e) {
            log.error("Failed to extract page context", e);
            return "{}";
        }
    }

    // Оптимизация размера DOM
    private String optimizeDOMSize(String dom) {
        if (dom == null || dom.isEmpty()) {
            return "";
        }

        try {
            // 1. Удаляем лишние пробелы и переносы строк
            String optimized = WHITESPACE_PATTERN.matcher(dom).replaceAll(" ");

            // 2. Удаляем комментарии если DOM слишком большой
            if (optimized.length() > maxSizePage * 0.8) {
                optimized = optimized.replaceAll("<!--.*?-->", "");
            }

            // 3. Обрезаем если все еще слишком большой
            if (optimized.length() > maxSizePage) {
                optimized = optimized.substring(0, maxSizePage) + "... [TRUNCATED]";
            }

            // 4. Удаляем пустые теги
            optimized = removeEmptyTags(optimized);

            // 5. Минимизируем атрибуты если включена очистка
            if (cleanAttributes) {
                optimized = minimizeAttributes(optimized);
            }

            log.debug("DOM optimized: {} -> {} characters", dom.length(), optimized.length());
            return optimized;

        } catch (Exception e) {
            log.error("Failed to optimize DOM size", e);
            // Возвращаем обрезанную версию
            return dom.length() > maxSizePage ?
                    dom.substring(0, maxSizePage) + "... [TRUNCATED]" : dom;
        }
    }

    // Удаление пустых тегов
    private String removeEmptyTags(String html) {
        // Паттерны для пустых тегов (без текста и без детей с контентом)
        String[] emptyTagPatterns = {
                "<div[^>]*>\\s*</div>",
                "<span[^>]*>\\s*</span>",
                "<p[^>]*>\\s*</p>",
                "<li[^>]*>\\s*</li>",
                "<td[^>]*>\\s*</td>",
                "<th[^>]*>\\s*</th>"
        };

        String result = html;
        for (String pattern : emptyTagPatterns) {
            result = result.replaceAll(pattern, "");
        }

        return result;
    }

    // Минимизация атрибутов
    private String minimizeAttributes(String html) {
        // Упрощаем атрибуты style и class
        html = html.replaceAll("style=\"[^\"]*\"", "style=\"\"");
        html = html.replaceAll("class=\"[^\"]*\"", "");

        // Удаляем data-атрибуты которые не критичны
        html = html.replaceAll("data-[a-z-]*=\"[^\"]*\"", "");

        // Удаляем пустые атрибуты
        html = html.replaceAll("\\s+[a-z-]+=\"\"", "");

        return html;
    }

    // Обновленный метод для релевантного HTML (теперь использует оптимизированную версию)
    private String extractRelevantHTML(WebDriver driver) {
        return extractOptimizedHTML(driver);
    }

    // НОВЫЙ МЕТОД: Получение DOM снапшота с видимыми элементами
    public String takeDomSnapshot(WebDriver driver) {
        return extractVisibleDOM(driver);
    }

    // Обновленный метод сканирования видимых элементов (использует ту же логику)
    public List<InteractiveElement> scanVisibleElements(WebDriver driver, String sessionId) {
        // Используем улучшенную версию из предыдущего ответа
        // с дополнительной оптимизацией для работы с видимыми элементами

        List<InteractiveElement> elements = new ArrayList<>();
        String cacheKey = sessionId + "_" + driver.getCurrentUrl();

        try {
            // Получаем видимые элементы через JavaScript
            JavascriptExecutor js = (JavascriptExecutor) driver;

            String script = """
                    function getVisibleInteractiveElements() {
                        const allElements = document.querySelectorAll('*');
                        const interactiveElements = [];
                        
                        for (const el of allElements) {
                            // Проверка видимости
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
                            
                            // Проверка интерактивности
                            const tagName = el.tagName.toLowerCase();
                            const isInteractiveByTag = [
                                'a', 'button', 'input', 'select', 'textarea',
                                'details', 'summary', 'video', 'audio'
                            ].includes(tagName);
                            
                            const hasRole = el.getAttribute('role');
                            const isInteractiveByRole = [
                                'button', 'link', 'checkbox', 'radio', 
                                'menuitem', 'tab', 'slider'
                            ].includes(hasRole);
                            
                            const hasClickHandler = el.onclick || el.getAttribute('onclick');
                            const hasTabIndex = el.tabIndex >= 0;
                            const cursorStyle = style.cursor;
                            const hasPointerCursor = cursorStyle === 'pointer' || cursorStyle === 'hand';
                            
                            const isInteractive = (
                                isInteractiveByTag || 
                                isInteractiveByRole || 
                                hasClickHandler || 
                                hasTabIndex || 
                                hasPointerCursor
                            );
                            
                            if (isInteractive) {
                                interactiveElements.push(el);
                            }
                        }
                        
                        return interactiveElements;
                    }
                    return getVisibleInteractiveElements();
                    """;

            @SuppressWarnings("unchecked")
            List<WebElement> webElements = (List<WebElement>) js.executeScript(script);

            if (webElements != null) {
                for (WebElement webElement : webElements) {
                    try {
                        InteractiveElement element = mapToInteractiveElement(webElement, driver, sessionId);
                        String elementSignature = generateElementSignature(element);
                        Set<String> cachedSignatures = sessionElementCache.computeIfAbsent(
                                cacheKey, k -> new HashSet<>());

                        if (!cachedSignatures.contains(elementSignature)) {
                            elements.add(element);
                            cachedSignatures.add(elementSignature);

                            if (elements.size() >= 50) {
                                break;
                            }
                        }
                    } catch (StaleElementReferenceException e) {
                        log.debug("Element became stale, skipping");
                    } catch (Exception e) {
                        log.debug("Failed to process element: {}", e.getMessage());
                    }
                }
            }

            log.info("Found {} visible interactive elements", elements.size());

        } catch (Exception e) {
            log.error("Failed to scan visible elements", e);
        }

        return elements;
    }

    private InteractionType determineInteractionType(WebElement element, WebDriver driver) {
        try {
            String tagName = element.getTagName().toLowerCase();
            String type = element.getAttribute("type");
            String role = element.getAttribute("role");

            // Проверяем по тегу и типу
            if ("a".equals(tagName)) {
                return InteractionType.NAVIGATION;
            }

            if ("button".equals(tagName) || "button".equals(role)) {
                return InteractionType.CLICKABLE;
            }

            if ("input".equals(tagName)) {
                if ("checkbox".equals(type) || "radio".equals(type)) {
                    return InteractionType.CHECKABLE;
                }
                if ("submit".equals(type) || "button".equals(type) || "image".equals(type)) {
                    return InteractionType.CLICKABLE;
                }
                if ("range".equals(type)) {
                    return InteractionType.SCROLLABLE;
                }
                if ("file".equals(type)) {
                    return InteractionType.OTHER;
                }
                return InteractionType.TYPEABLE;
            }

            if ("textarea".equals(tagName)) {
                return InteractionType.TYPEABLE;
            }

            if ("select".equals(tagName)) {
                return InteractionType.SELECTABLE;
            }

            // Проверяем наличие обработчиков событий
            String onclick = element.getAttribute("onclick");
            if (onclick != null && !onclick.isEmpty()) {
                return InteractionType.CLICKABLE;
            }

            // Проверяем стиль курсора
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String cursor = (String) js.executeScript("""
                    return window.getComputedStyle(arguments[0]).cursor;
                    """, element);

            if ("pointer".equals(cursor) || "hand".equals(cursor)) {
                return InteractionType.CLICKABLE;
            }

            // Проверяем tabindex
            String tabindex = element.getAttribute("tabindex");
            if (tabindex != null && !tabindex.equals("-1")) {
                return InteractionType.CLICKABLE;
            }

            return InteractionType.OTHER;

        } catch (Exception e) {
            log.debug("Failed to determine interaction type: {}", e.getMessage());
            return InteractionType.OTHER;
        }
    }

    // Генерация сигнатуры элемента для кэширования
    private String generateElementSignature(InteractiveElement element) {
        return String.format("%s|%s|%s|%s",
                element.getTagName(),
                element.getSelector() != null ? element.getSelector() : "",
                element.getText() != null ? element.getText().hashCode() : 0,
                element.getIdAttr() != null ? element.getIdAttr() : ""
        );
    }

    // Очистка старых записей кэша
    private void cleanupOldCacheEntries(String sessionId, String currentCacheKey) {
        try {
            sessionElementCache.keySet().removeIf(key ->
                    key.startsWith(sessionId + "_") && !key.equals(currentCacheKey));
        } catch (Exception e) {
            log.debug("Failed to cleanup cache: {}", e.getMessage());
        }
    }

    private String getElementText(WebElement element, WebDriver driver) {
        try {
            // Пробуем разные способы получения текста
            String text = element.getText().trim();

            if (text.isEmpty()) {
                // Для input элементов берем value
                if ("input".equals(element.getTagName()) || "textarea".equals(element.getTagName())) {
                    text = element.getAttribute("value");
                }
                // Для ссылок берем текст или href
                else if ("a".equals(element.getTagName())) {
                    text = element.getAttribute("text") != null ?
                            element.getAttribute("text") :
                            element.getAttribute("href");
                }
                // Пробуем получить через JavaScript
                else {
                    JavascriptExecutor js = (JavascriptExecutor) driver;
                    text = (String) js.executeScript("""
                            return arguments[0].textContent || 
                                   arguments[0].innerText || 
                                   arguments[0].getAttribute('aria-label') || 
                                   arguments[0].getAttribute('title') || 
                                   arguments[0].getAttribute('alt') || 
                                   '';
                            """, element);
                }
            }

            return text != null ? text.trim() : "";

        } catch (Exception e) {
            log.debug("Failed to get element text: {}", e.getMessage());
            return "";
        }
    }

    // Генерация CSS селектора
    private String generateCssSelector(WebElement element, WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            return (String) js.executeScript("""
                    function getCssSelector(element) {
                        if (!element) return '';
                        
                        // Если есть ID
                        if (element.id) {
                            return '#' + element.id.replace(/\\s+/g, '#');
                        }
                        
                        // Если есть уникальный data-атрибут
                        const dataAttrs = ['data-testid', 'data-qa', 'data-cy', 'data-id'];
                        for (const attr of dataAttrs) {
                            const value = element.getAttribute(attr);
                            if (value) {
                                return '[' + attr + '="' + value + '"]';
                            }
                        }
                        
                        // Если есть name
                        if (element.name) {
                            return element.tagName.toLowerCase() + '[name="' + element.name + '"]';
                        }
                        
                        // Генерация по классам
                        if (element.className) {
                            const classes = element.className.trim().split(/\\s+/);
                            if (classes.length > 0) {
                                const classSelector = '.' + classes.join('.');
                                const withClass = document.querySelectorAll(element.tagName.toLowerCase() + classSelector);
                                if (withClass.length === 1) {
                                    return element.tagName.toLowerCase() + classSelector;
                                }
                            }
                        }
                        
                        // Генерация по пути
                        const path = [];
                        while (element && element.nodeType === Node.ELEMENT_NODE) {
                            let selector = element.tagName.toLowerCase();
                            
                            if (element.id) {
                                selector += '#' + element.id;
                                path.unshift(selector);
                                break;
                            }
                            
                            const siblings = element.parentNode ? 
                                Array.from(element.parentNode.children) : [];
                            const index = siblings.indexOf(element) + 1;
                            
                            if (index > 1) {
                                selector += ':nth-child(' + index + ')';
                            }
                            
                            path.unshift(selector);
                            element = element.parentNode;
                        }
                        
                        return path.join(' > ');
                    }
                    return getCssSelector(arguments[0]);
                    """, element);
        } catch (Exception e) {
            log.debug("Failed to generate CSS selector: {}", e.getMessage());
            return element.getTagName().toLowerCase();
        }
    }

    // Генерация XPath
    private String generateXPath(WebElement element, WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            return (String) js.executeScript("""
                    function getXPath(element) {
                        if (!element) return '';
                        
                        // Если есть ID
                        if (element.id) {
                            return '//' + element.tagName.toLowerCase() + '[@id="' + element.id + '"]';
                        }
                        
                        // Если есть name
                        if (element.name) {
                            return '//' + element.tagName.toLowerCase() + '[@name="' + element.name + '"]';
                        }
                        
                        // Рекурсивная генерация
                        if (element === document.body) {
                            return '/html/body';
                        }
                        
                        let ix = 0;
                        const siblings = element.parentNode.children;
                        
                        for (let i = 0; i < siblings.length; i++) {
                            const sibling = siblings[i];
                            if (sibling === element) {
                                return getXPath(element.parentNode) + '/' + 
                                       element.tagName.toLowerCase() + '[' + (ix + 1) + ']';
                            }
                            if (sibling.tagName === element.tagName) {
                                ix++;
                            }
                        }
                        
                        return '';
                    }
                    return getXPath(arguments[0]);
                    """, element);
        } catch (Exception e) {
            log.debug("Failed to generate XPath: {}", e.getMessage());
            return "";
        }
    }

    private List<WebElement> findInteractiveElementsWithJS(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Комплексный поиск всех потенциально интерактивных элементов
            String script = """
                    function findInteractiveElements() {
                        const selectors = [
                            // Основные интерактивные элементы
                            'a', 'button', 'input', 'select', 'textarea',
                            
                            // Элементы с ARIA ролями
                            '[role="button"]', '[role="link"]', '[role="checkbox"]',
                            '[role="radio"]', '[role="menuitem"]', '[role="tab"]',
                            '[role="slider"]', '[role="combobox"]', '[role="listbox"]',
                            
                            // Элементы с обработчиками событий
                            '[onclick]', '[onchange]', '[onsubmit]', '[onfocus]',
                            '[onblur]', '[onkeydown]', '[onkeyup]',
                            
                            // Элементы с tabindex (кроме -1)
                            '[tabindex]:not([tabindex="-1"])',
                            
                            // Распространенные классы кнопок и ссылок
                            '.btn', '.button', '.link', '.nav-link', '.menu-item',
                            '.dropdown-item', '.tab-link', '.accordion-header',
                            
                            // Формы и поля ввода
                            'form', 'fieldset', 'label[for]',
                            
                            // Медиа элементы
                            'video', 'audio', '[controls]',
                            
                            // Детали и summary
                            'details', 'summary'
                        ];
                        
                        const allElements = new Set();
                        
                        // Ищем элементы по всем селекторам
                        selectors.forEach(selector => {
                            try {
                                const elements = document.querySelectorAll(selector);
                                elements.forEach(el => {
                                    // Проверяем, что элемент в видимой области
                                    const rect = el.getBoundingClientRect();
                                    if (rect.width > 0 && rect.height > 0) {
                                        allElements.add(el);
                                    }
                                });
                            } catch (e) {
                                // Игнорируем ошибки невалидных селекторов
                            }
                        });
                        
                        // Также ищем элементы, которые могут быть интерактивными по контексту
                        const contextualElements = [
                            ...document.querySelectorAll('div, span, li, td, th, article, section')
                        ].filter(el => {
                            // Элементы, которые выглядят как кнопки
                            const style = window.getComputedStyle(el);
                            const cursor = style.cursor;
                            const hasPointer = cursor === 'pointer' || cursor === 'hand';
                            
                            // Элементы с обработчиками событий
                            const hasEventListeners = el.onclick || 
                                el.getAttribute('onclick') || 
                                el.hasAttribute('data-toggle');
                            
                            // Элементы с интерактивным содержимым
                            const hasInteractiveChildren = el.querySelector('a, button, input');
                            
                            return hasPointer || hasEventListeners || hasInteractiveChildren;
                        });
                        
                        contextualElements.forEach(el => allElements.add(el));
                        
                        return Array.from(allElements);
                    }
                                    
                    return findInteractiveElements();
                    """;

            @SuppressWarnings("unchecked")
            List<WebElement> elements = (List<WebElement>) js.executeScript(script);

            return elements != null ? elements : new ArrayList<>();

        } catch (Exception e) {
            log.warn("JavaScript search failed, falling back to traditional methods", e);
            return findInteractiveElementsTraditional(driver);
        }
    }

    private List<WebElement> findInteractiveElementsTraditional(WebDriver driver) {
        List<WebElement> elements = new ArrayList<>();
        Set<String> uniqueIds = new HashSet<>();

        String[] selectors = {
                // Базовые интерактивные элементы
                "a[href]",
                "button",
                "input:not([type='hidden'])",
                "select",
                "textarea",

                // Элементы с ARIA
                "[role='button']",
                "[role='link']",
                "[role='checkbox']",
                "[role='radio']",
                "[role='menuitem']",

                // Элементы с обработчиками
                "[onclick]",
                "[onchange]",
                "[onsubmit]",

                // Элементы доступные для табуляции
                "[tabindex]:not([tabindex='-1'])",

                // Распространенные классы
                ".btn",
                ".button",
                ".nav-link",
                ".dropdown-toggle",
                ".accordion-button"
        };

        for (String selector : selectors) {
            try {
                List<WebElement> foundElements = driver.findElements(By.cssSelector(selector));
                for (WebElement element : foundElements) {
                    try {
                        // Проверяем уникальность по ID или комбинации атрибутов
                        String elementId = element.getAttribute("id");
                        String elementKey = elementId != null && !elementId.isEmpty() ?
                                "id:" + elementId :
                                generateElementKey(element);

                        if (!uniqueIds.contains(elementKey)) {
                            elements.add(element);
                            uniqueIds.add(elementKey);
                        }
                    } catch (StaleElementReferenceException e) {
                        log.debug("Element became stale while processing");
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to find elements with selector {}: {}", selector, e.getMessage());
            }
        }

        return elements;
    }

    private String generateElementKey(WebElement element) {
        try {
            String tag = element.getTagName();
            String text = element.getText().substring(0, Math.min(20, element.getText().length()));
            String type = element.getAttribute("type");
            String name = element.getAttribute("name");
            String classes = element.getAttribute("class");

            return String.format("%s|%s|%s|%s|%s",
                    tag, text, type, name, classes);
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private boolean isElementVisibleAndInteractable(WebElement element, WebDriver driver) {
        try {
            // Проверяем базовую видимость и доступность
            if (!element.isDisplayed() || !element.isEnabled()) {
                return false;
            }

            // Проверяем размеры элемента
            org.openqa.selenium.Dimension size = element.getSize();
            if (size.getWidth() < 5 || size.getHeight() < 5) {
                log.debug("Element too small: {}x{}", size.getWidth(), size.getHeight());
                return false;
            }

            // Проверяем, что элемент не перекрыт
            org.openqa.selenium.Point location = element.getLocation();
            JavascriptExecutor js = (JavascriptExecutor) driver;

            Boolean isVisible = (Boolean) js.executeScript("""
                    function isElementVisible(el) {
                        const rect = el.getBoundingClientRect();
                        if (rect.width === 0 || rect.height === 0) {
                            return false;
                        }
                        
                        // Проверяем, что элемент в viewport
                        const isInViewport = (
                            rect.top >= 0 &&
                            rect.left >= 0 &&
                            rect.bottom <= (window.innerHeight || document.documentElement.clientHeight) &&
                            rect.right <= (window.innerWidth || document.documentElement.clientWidth)
                        );
                        
                        if (!isInViewport) {
                            return false;
                        }
                        
                        // Проверяем, что элемент не перекрыт
                        const centerX = rect.left + rect.width / 2;
                        const centerY = rect.top + rect.height / 2;
                        
                        const topElement = document.elementFromPoint(centerX, centerY);
                        return topElement === el || el.contains(topElement);
                    }
                    return isElementVisible(arguments[0]);
                    """, element);

            return Boolean.TRUE.equals(isVisible);

        } catch (StaleElementReferenceException e) {
            log.debug("Element became stale during visibility check");
            return false;
        } catch (Exception e) {
            log.debug("Visibility check failed: {}", e.getMessage());
            return false;
        }
    }

    private InteractiveElement mapToInteractiveElement(WebElement webElement, WebDriver driver, String sessionId) {
        InteractiveElement element = new InteractiveElement();

        try {
            element.setSessionId(sessionId);
            element.setTagName(webElement.getTagName().toLowerCase());

            // Получаем текст элемента с учетом различных случаев
            String text = getElementText(webElement, driver);
            element.setText(text);

            // Базовые атрибуты
            element.setIdAttr(webElement.getAttribute("id"));
            element.setName(webElement.getAttribute("name"));
            element.setType(webElement.getAttribute("type"));
            element.setPlaceholder(webElement.getAttribute("placeholder"));
            element.setClasses(webElement.getAttribute("class"));

            // Генерируем селекторы
            element.setSelector(generateCssSelector(webElement, driver));
            element.setXpath(generateXPath(webElement, driver));

            // Координаты и размеры
            try {
                org.openqa.selenium.Point location = webElement.getLocation();
                Dimension size = webElement.getSize();
                element.setBounds(new ElementBounds(
                        location.getX(),
                        location.getY(),
                        size.getWidth(),
                        size.getHeight()
                ));
            } catch (Exception e) {
                log.debug("Failed to get element bounds");
                element.setBounds(new ElementBounds(0, 0, 0, 0));
            }

            // Статус элемента
            element.setIsVisible(true);
            element.setIsEnabled(webElement.isEnabled());
            element.setIsInteractable(true);

            // Определяем тип взаимодействия
            element.setInteractionType(determineInteractionType(webElement, driver));

            // Собираем важные атрибуты
            Map<String, String> attributes = new HashMap<>();
            String[] importantAttrs = {
                    "id", "name", "type", "value", "placeholder",
                    "href", "src", "alt", "title", "role",
                    "aria-label", "aria-describedby", "aria-hidden",
                    "disabled", "readonly", "required", "tabindex",
                    "data-testid", "data-qa", "data-cy", "data-id",
                    "onclick", "onchange", "onsubmit"
            };

            for (String attr : importantAttrs) {
                String value = webElement.getAttribute(attr);
                if (value != null && !value.trim().isEmpty()) {
                    attributes.put(attr, value.trim());
                }
            }

            element.setAttributes(attributes);
            element.setDiscoveredAt(LocalDateTime.now());
            element.setTimesInteracted(0);

            log.debug("Mapped element: {} ({}), selector: {}",
                    element.getTagName(), text, element.getSelector());

        } catch (StaleElementReferenceException e) {
            log.debug("Element became stale during mapping");
            // Возвращаем минимальную информацию
            element.setTagName("unknown");
            element.setText("");
            element.setSelector("stale_element");
        } catch (Exception e) {
            log.warn("Failed to map WebElement to InteractiveElement", e);
            element.setTagName("error");
            element.setText("Mapping error: " + e.getMessage());
        }

        return element;
    }


    public String takeScreenshot(WebDriver driver) {
        return takeScreenshot(driver, null, null);
    }

    public String takeScreenshot(WebDriver driver, WebElement elementToHighlight, String sessionId) {
        try {
            if (Objects.nonNull(elementToHighlight)) {
                highlightElement(driver, elementToHighlight, "red", "2px");
                Thread.sleep(200); // Даем время для отрисовки
            }
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            String filename = String.format("screenshot_%s.%s",
                    sessionId != null ? sessionId + "_" + timestamp : timestamp,
                    screenshotFormat);

            Path destination = Paths.get(screenshotDir, filename);
            Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
            optimizeImageSize(destination);
            return convertToBase64(destination);

        } catch (Exception e) {
            log.error("Failed to take screenshot", e);
            return null;
        } finally {
            if (Objects.nonNull(elementToHighlight)) {
                try {
                    removeHighlight(driver, elementToHighlight);
                } catch (Exception e) {
                    log.warn("Failed to remove highlight", e);
                }
            }
        }
    }

    private void highlightElement(WebDriver driver, WebElement element, String color, String borderWidth) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String originalStyle = element.getAttribute("style");

            js.executeScript(
                    "arguments[0].setAttribute('data-original-style', arguments[1]);",
                    element, originalStyle
            );

            String highlightStyle = String.format(
                    "border: %s solid %s !important; background-color: rgba(255,0,0,0.1) !important;",
                    borderWidth, color
            );

            js.executeScript(
                    "arguments[0].setAttribute('style', arguments[1]);",
                    element, highlightStyle
            );

        } catch (Exception e) {
            log.warn("Failed to highlight element", e);
        }
    }

    private void removeHighlight(WebDriver driver, WebElement element) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String originalStyle = (String) js.executeScript(
                    "return arguments[0].getAttribute('data-original-style');",
                    element
            );

            if (originalStyle != null) {
                js.executeScript(
                        "arguments[0].setAttribute('style', arguments[1]);",
                        element, originalStyle
                );
            } else {
                js.executeScript(
                        "arguments[0].removeAttribute('style');",
                        element
                );
            }

            js.executeScript(
                    "arguments[0].removeAttribute('data-original-style');",
                    element
            );

        } catch (Exception e) {
            log.warn("Failed to remove highlight", e);
        }
    }

    private void optimizeImageSize(Path imagePath) {
        try {
            long sizeKb = Files.size(imagePath) / 1024;

            if (sizeKb > maxScreenshotSizeKb) {
                log.debug("Optimizing image size: {}KB -> target {}KB", sizeKb, maxScreenshotSizeKb);

                BufferedImage image = ImageIO.read(imagePath.toFile());

                double ratio = (double) maxScreenshotSizeKb / sizeKb;
                int newWidth = (int) (image.getWidth() * Math.sqrt(ratio));
                int newHeight = (int) (image.getHeight() * Math.sqrt(ratio));

                BufferedImage resized = new BufferedImage(newWidth, newHeight, image.getType());
                Graphics2D g = resized.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, 0, 0, newWidth, newHeight, null);
                g.dispose();

                ImageIO.write(resized, screenshotFormat, imagePath.toFile());

                log.debug("Image optimized: {}KB -> {}KB",
                        sizeKb, Files.size(imagePath) / 1024);
            }

        } catch (Exception e) {
            log.warn("Failed to optimize image size", e);
        }
    }

    private String convertToBase64(Path imagePath) {
        try {
            byte[] fileContent = Files.readAllBytes(imagePath);
            String base64 = Base64.getEncoder().encodeToString(fileContent);

            if (base64.length() > maxScreenshotSizeKb * 1024) {
                return "file:" + imagePath.toString();
            }

            return "data:image/" + screenshotFormat + ";base64," + base64;

        } catch (IOException e) {
            log.error("Failed to convert image to base64", e);
            return "file:" + imagePath;
        }
    }

}
