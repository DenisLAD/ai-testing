package ru.sbrf.uddk.ai.testing.service;

import ru.sbrf.uddk.ai.testing.entity.ElementBounds;
import ru.sbrf.uddk.ai.testing.entity.InteractiveElement;
import ru.sbrf.uddk.ai.testing.entity.TestSession;
import ru.sbrf.uddk.ai.testing.entity.consts.InteractionType;
import ru.sbrf.uddk.ai.testing.model.AgentObservation;
import ru.sbrf.uddk.ai.testing.utils.EnhancedDOMExtractor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ObservationService implements InitializingBean, DisposableBean {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
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
            waitForPageReady(driver);

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

    // Обновленный метод для релевантного HTML (ИСПРАВЛЕНО: использует EnhancedDOMExtractor)
    private String extractRelevantHTML(WebDriver driver) {
        // Используем улучшенный экстрактор который удаляет html/head/body теги
        return EnhancedDOMExtractor.extractForLLM(driver);
    }

    // НОВЫЙ МЕТОД: Получение DOM снапшота с видимыми элементами
    public String takeDomSnapshot(WebDriver driver) {
        // Используем улучшенный экстрактор для LLM
        return EnhancedDOMExtractor.extractForLLM(driver);
    }

    /**
     * Получение компактного DOM для LLM (список элементов)
     */
    public String takeCompactDomSnapshot(WebDriver driver) {
        return ru.sbrf.uddk.ai.testing.utils.EnhancedDOMExtractor.extractCompactDOM(driver);
    }

    private boolean isFormHeavyContentPage(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object result = js.executeScript("""
                    const main = document.querySelector('main');
                    if (!main) {
                        return false;
                    }
                    const contentRoots = resolveContentRoots(main);
                    let formSignals = 0;
                    for (const root of contentRoots) {
                        formSignals += root.querySelectorAll(
                            'label, input[type="checkbox"], [role="checkbox"], h3 button'
                        ).length;
                    }
                    return formSignals >= 3;

                    function resolveContentRoots(mainElement) {
                        const roots = [];
                        if (!mainElement) {
                            return roots;
                        }
                        if (mainElement.children.length > 1) {
                            for (let i = 1; i < mainElement.children.length; i++) {
                                roots.push(mainElement.children[i]);
                            }
                            return roots;
                        }
                        const wrapper = mainElement.children[0];
                        if (wrapper && wrapper.children.length > 1) {
                            for (let i = 1; i < wrapper.children.length; i++) {
                                roots.push(wrapper.children[i]);
                            }
                        }
                        return roots;
                    }
                    """);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.debug("Form-heavy page detection failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean isMainContentLinkHub(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object result = js.executeScript("""
                    const main = document.querySelector('main');
                    if (!main) {
                        return false;
                    }
                    const drawerSelector = '.MuiDrawer-root, aside, nav, [role="navigation"]';
                    const contentRoots = resolveContentRoots(main);
                    let linkCount = 0;
                    for (const root of contentRoots) {
                        const links = root.querySelectorAll('a[href]');
                        for (const link of links) {
                            if (link.closest(drawerSelector)) {
                                continue;
                            }
                            const href = link.getAttribute('href') || '';
                            if (!href || href === '#' || href.startsWith('javascript:')) {
                                continue;
                            }
                            linkCount++;
                        }
                    }
                    return linkCount >= 4;

                    function resolveContentRoots(mainElement) {
                        const roots = [];
                        if (!mainElement) {
                            return roots;
                        }
                        if (mainElement.children.length > 1) {
                            for (let i = 1; i < mainElement.children.length; i++) {
                                roots.push(mainElement.children[i]);
                            }
                            return roots;
                        }
                        const wrapper = mainElement.children[0];
                        if (wrapper && wrapper.children.length > 1) {
                            for (let i = 1; i < wrapper.children.length; i++) {
                                roots.push(wrapper.children[i]);
                            }
                        }
                        return roots;
                    }
                    """);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.debug("Link-hub page detection failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean shouldPrioritizeMainContent(WebDriver driver) {
        return isFormHeavyContentPage(driver) || isMainContentLinkHub(driver);
    }

    // Обновленный метод сканирования видимых элементов (приоритет main content)
    public List<InteractiveElement> scanVisibleElements(WebDriver driver, String sessionId) {
        List<InteractiveElement> elements = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            boolean contentFirstScan = shouldPrioritizeMainContent(driver);

            String script = """
                    function collectVisibleInteractiveElements() {
                        const CONTENT_FIRST = %s;
                        const NOISE_TAGS = new Set(['svg', 'path', 'g', 'script', 'style', 'noscript']);
                        const INTERACTIVE_TAGS = new Set([
                            'a', 'button', 'input', 'select', 'textarea', 'label',
                            'details', 'summary', 'video', 'audio'
                        ]);
                        const INTERACTIVE_ROLES = new Set([
                            'button', 'link', 'checkbox', 'radio',
                            'menuitem', 'tab', 'slider'
                        ]);
                        const MAX_TOTAL = CONTENT_FIRST ? 100 : 80;
                        const MAX_SIDEBAR_BUDGET = CONTENT_FIRST ? 14 : 36;
                        const MAX_CONTENT_BUDGET = MAX_TOTAL - MAX_SIDEBAR_BUDGET;
                        const collected = [];
                        const seen = new Set();
                        let sidebarCount = 0;
                        let contentCount = 0;

                        function isVisible(el, relaxed) {
                            const rect = el.getBoundingClientRect();
                            const style = window.getComputedStyle(el);
                            const baseVisible = (
                                rect.width > 5 &&
                                rect.height > 5 &&
                                style.display !== 'none' &&
                                style.visibility !== 'hidden' &&
                                parseFloat(style.opacity || '1') > 0
                            );
                            if (!baseVisible) {
                                return false;
                            }
                            if (relaxed) {
                                return true;
                            }
                            const viewH = window.innerHeight || document.documentElement.clientHeight;
                            const viewW = window.innerWidth || document.documentElement.clientWidth;
                            return (
                                rect.bottom > 0 &&
                                rect.right > 0 &&
                                rect.top < viewH &&
                                rect.left < viewW
                            );
                        }

                        function isInteractive(el) {
                            const tagName = el.tagName.toLowerCase();
                            if (NOISE_TAGS.has(tagName)) {
                                return false;
                            }
                            if (INTERACTIVE_TAGS.has(tagName)) {
                                return true;
                            }
                            const role = el.getAttribute('role');
                            if (role && INTERACTIVE_ROLES.has(role)) {
                                return true;
                            }
                            if (el.onclick || el.getAttribute('onclick')) {
                                return true;
                            }
                            if (el.tabIndex >= 0) {
                                return true;
                            }
                            const cursor = window.getComputedStyle(el).cursor;
                            if (cursor === 'pointer' || cursor === 'hand') {
                                return true;
                            }
                            if (tagName === 'div' || tagName === 'li' || tagName === 'span') {
                                const text = (el.innerText || el.textContent || '').trim();
                                if (text.length >= 3 && text.length <= 200) {
                                    if (text.includes('\\n')) {
                                        return true;
                                    }
                                    if (!el.querySelector('a, button, input')) {
                                        return true;
                                    }
                                }
                            }
                            return false;
                        }

                        function elementKey(el) {
                            const tag = el.tagName.toLowerCase();
                            const id = el.id || '';
                            const href = el.getAttribute('href') || '';
                            const text = (el.innerText || el.textContent || '').trim().substring(0, 80);
                            return tag + '|' + id + '|' + href + '|' + text;
                        }

                        function canAddToBucket(bucket) {
                            if (collected.length >= MAX_TOTAL) {
                                return false;
                            }
                            if (bucket === 'sidebar') {
                                return sidebarCount < MAX_SIDEBAR_BUDGET;
                            }
                            if (bucket === 'content') {
                                return contentCount < MAX_CONTENT_BUDGET;
                            }
                            return true;
                        }

                        function addElement(el, relaxed, bucket) {
                            if (!el || seen.has(el)) {
                                return false;
                            }
                            if (!canAddToBucket(bucket)) {
                                return false;
                            }
                            if (!isVisible(el, relaxed) || !isInteractive(el)) {
                                return false;
                            }
                            const key = elementKey(el);
                            if (seen.has(key)) {
                                return false;
                            }
                            seen.add(el);
                            seen.add(key);
                            collected.push(el);
                            if (bucket === 'sidebar') {
                                sidebarCount++;
                            } else if (bucket === 'content') {
                                contentCount++;
                            }
                            return true;
                        }

                        function scanRoots(roots, relaxed, budget, bucket, selector, reverseOrder) {
                            const query = selector || (
                                'a, button, input, select, textarea, label, [role], [onclick], [tabindex], div, span, h3'
                            );
                            let addedInBatch = 0;
                            for (const root of roots) {
                                if (!root || collected.length >= MAX_TOTAL || addedInBatch >= budget) {
                                    continue;
                                }
                                let candidates = Array.from(root.querySelectorAll(query));
                                if (reverseOrder) {
                                    candidates = candidates.reverse();
                                }
                                for (const el of candidates) {
                                    if (addElement(el, relaxed, bucket)) {
                                        addedInBatch++;
                                    }
                                    if (collected.length >= MAX_TOTAL || addedInBatch >= budget) {
                                        break;
                                    }
                                }
                            }
                        }

                        function scanSidebar(roots, budget) {
                            const controlsBudget = Math.max(8, Math.ceil(budget * 0.6));
                            scanRoots(
                                roots,
                                true,
                                controlsBudget,
                                'sidebar',
                                'a, button, label, input, select, textarea, [role=link], [role=button], [role=menuitem], h3',
                                true
                            );
                            if (sidebarCount < budget) {
                                scanRoots(
                                    roots,
                                    true,
                                    budget - sidebarCount,
                                    'sidebar',
                                    'div, span, li',
                                    true
                                );
                            }
                        }

                        function isInsideDrawer(el) {
                            return !!el.closest('.MuiDrawer-root, aside, nav, [role="navigation"]');
                        }

                        function resolveContentRoots(mainElement) {
                            const roots = [];
                            if (!mainElement) {
                                return roots;
                            }
                            if (mainElement.children.length > 1) {
                                for (let i = 1; i < mainElement.children.length; i++) {
                                    roots.push(mainElement.children[i]);
                                }
                                return roots.filter(Boolean);
                            }
                            const wrapper = mainElement.children[0];
                            if (wrapper && wrapper.children.length > 1) {
                                for (let i = 1; i < wrapper.children.length; i++) {
                                    roots.push(wrapper.children[i]);
                                }
                            }
                            if (roots.length > 0) {
                                return roots;
                            }
                            return [
                                document.querySelector('[role="main"]'),
                                document.querySelector('.main-content'),
                                document.querySelector('[data-testid="main-content"]')
                            ].filter(Boolean);
                        }

                        function resolveSidebarRoots(mainElement) {
                            const roots = [];
                            const drawer = document.querySelector('.MuiDrawer-root');
                            if (drawer) {
                                roots.push(drawer);
                            }
                            if (mainElement) {
                                if (mainElement.children.length > 0) {
                                    roots.push(mainElement.children[0]);
                                }
                                const wrapper = mainElement.children.length === 1 ? mainElement.children[0] : null;
                                if (wrapper && wrapper.children.length > 0) {
                                    roots.push(wrapper.children[0]);
                                }
                            }
                            roots.push(
                                document.querySelector('aside'),
                                document.querySelector('nav'),
                                document.querySelector('[role="navigation"]')
                            );
                            return roots.filter(Boolean);
                        }

                        function scanContent(roots, budget) {
                            const linksBudget = Math.max(12, Math.ceil(budget * 0.45));
                            for (const root of roots) {
                                if (!root || collected.length >= MAX_TOTAL || contentCount >= budget) {
                                    continue;
                                }
                                let links = Array.from(root.querySelectorAll('a[href]'));
                                for (const el of links) {
                                    if (isInsideDrawer(el)) {
                                        continue;
                                    }
                                    if (addElement(el, false, 'content')) {
                                        if (contentCount >= linksBudget || collected.length >= MAX_TOTAL) {
                                            break;
                                        }
                                    }
                                }
                            }
                            const controlsBudget = Math.max(8, Math.ceil(budget * 0.35));
                            scanRoots(
                                roots,
                                false,
                                controlsBudget,
                                'content',
                                'button, input, select, textarea, label, [role=button], [role=checkbox], h3, h4',
                                false
                            );
                            if (contentCount < budget) {
                                scanRoots(
                                    roots,
                                    false,
                                    budget - contentCount,
                                    'content',
                                    'div, span, li',
                                    false
                                );
                            }
                        }

                        const mainElement = document.querySelector('main');
                        const sidebarRoots = resolveSidebarRoots(mainElement);
                        const filteredContent = resolveContentRoots(mainElement);

                        if (CONTENT_FIRST) {
                            scanContent(filteredContent, MAX_CONTENT_BUDGET);
                            scanSidebar(sidebarRoots, MAX_SIDEBAR_BUDGET);
                        } else {
                            scanSidebar(sidebarRoots, MAX_SIDEBAR_BUDGET);
                            scanContent(filteredContent, MAX_CONTENT_BUDGET);
                        }

                        const navRoots = [document.querySelector('header')].filter(Boolean);
                        scanRoots(navRoots, false, CONTENT_FIRST ? 5 : 8, 'content', null, false);

                        function exportButtonLabel(el) {
                            if (!el) {
                                return '';
                            }
                            const chunks = [
                                el.innerText,
                                el.textContent,
                                el.getAttribute('title'),
                                el.getAttribute('aria-label')
                            ];
                            for (const node of el.querySelectorAll('span, p')) {
                                chunks.push(node.textContent);
                            }
                            return chunks.filter(Boolean).join(' ').toLowerCase();
                        }

                        function isExportDownloadButton(el) {
                            const text = exportButtonLabel(el);
                            return text.includes('скачать')
                                || text.includes('пакет')
                                || (text.includes('экспорт') && text.includes('zip'));
                        }

                        function forceAddDownloadButton(el) {
                            if (!el || seen.has(el)) {
                                return false;
                            }
                            if (!isExportDownloadButton(el)) {
                                return false;
                            }
                            if (collected.length >= MAX_TOTAL) {
                                for (let i = collected.length - 1; i >= 0; i--) {
                                    const candidate = collected[i];
                                    const tag = candidate.tagName ? candidate.tagName.toLowerCase() : '';
                                    if (tag === 'div' || tag === 'span' || tag === 'li') {
                                        collected.splice(i, 1);
                                        contentCount = Math.max(0, contentCount - 1);
                                        break;
                                    }
                                }
                            }
                            return addElement(el, true, 'content');
                        }

                        function scanPriorityFormControls(roots) {
                            let treeAdded = 0;
                            const MAX_TREE_CHECKBOXES = 8;
                            for (const root of roots) {
                                if (!root) {
                                    continue;
                                }
                                const buttons = root.querySelectorAll('button');
                                for (const el of buttons) {
                                    if (el.classList.contains('MuiAccordionSummary-root')) {
                                        continue;
                                    }
                                    forceAddDownloadButton(el);
                                }
                                if (treeAdded >= MAX_TREE_CHECKBOXES) {
                                    continue;
                                }
                                const treeInputs = root.querySelectorAll('input[type="checkbox"][id*="-item-"]');
                                for (const el of treeInputs) {
                                    if (treeAdded >= MAX_TREE_CHECKBOXES || collected.length >= MAX_TOTAL) {
                                        break;
                                    }
                                    if (addElement(el, true, 'content')) {
                                        treeAdded++;
                                    }
                                }
                            }
                        }

                        if (CONTENT_FIRST && filteredContent.length > 0) {
                            scanPriorityFormControls(filteredContent);
                            for (const root of filteredContent) {
                                if (root && root.scrollHeight > root.clientHeight) {
                                    root.scrollTop = root.scrollHeight;
                                }
                            }
                            window.scrollTo(0, document.body.scrollHeight);
                            const belowFoldSelectors = 'a[href], button, h3, h4, label, [role="button"], [role="treeitem"], li.k-treeview-item, .k-treeview-item';
                            for (const root of filteredContent) {
                                const belowFold = root.querySelectorAll(belowFoldSelectors);
                                for (const el of belowFold) {
                                    if (isInsideDrawer(el)) {
                                        continue;
                                    }
                                    const tag = el.tagName ? el.tagName.toLowerCase() : '';
                                    if (tag === 'button') {
                                        forceAddDownloadButton(el);
                                        continue;
                                    }
                                    addElement(el, true, 'content');
                                    if (collected.length >= MAX_TOTAL) {
                                        break;
                                    }
                                }
                            }
                            scanPriorityFormControls(filteredContent);
                        }

                        function scanExportDownloadButtonGlobally() {
                            const main = document.querySelector('main') || document.body;
                            if (!main) {
                                return;
                            }
                            for (const el of main.querySelectorAll('button')) {
                                if (el.classList.contains('MuiAccordionSummary-root')) {
                                    continue;
                                }
                                forceAddDownloadButton(el);
                            }
                        }
                        scanExportDownloadButtonGlobally();

                        if (collected.length < MAX_TOTAL) {
                            const bodyChildren = document.body ? Array.from(document.body.children) : [];
                            for (const child of bodyChildren) {
                                if (collected.length >= MAX_TOTAL) {
                                    break;
                                }
                                if (child.tagName && ['SCRIPT', 'STYLE', 'NOSCRIPT'].includes(child.tagName)) {
                                    continue;
                                }
                                const candidates = child.querySelectorAll('a, button, input, select, textarea, [role], [onclick], [tabindex]');
                                for (const el of candidates) {
                                    addElement(el, false, 'content');
                                    if (collected.length >= MAX_TOTAL) {
                                        break;
                                    }
                                }
                            }
                        }

                        return collected;
                    }
                    return collectVisibleInteractiveElements();
                    """.formatted(contentFirstScan);

            @SuppressWarnings("unchecked")
            List<WebElement> webElements = (List<WebElement>) js.executeScript(script);

            if (webElements != null) {
                for (WebElement webElement : webElements) {
                    try {
                        String key = generateElementKey(webElement);
                        if (!seenKeys.add(key)) {
                            continue;
                        }
                        InteractiveElement element = mapToInteractiveElement(webElement, driver, sessionId);
                        elements.add(element);
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

    private String getElementText(WebElement element, WebDriver driver) {
        try {
            String tag = element.getTagName().toLowerCase();
            String text = element.getText().trim();

            if ("button".equals(tag)) {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                text = (String) js.executeScript("""
                        const el = arguments[0];
                        const parts = [
                            el.innerText,
                            el.textContent,
                            el.getAttribute('title'),
                            el.getAttribute('aria-label')
                        ];
                        for (const node of el.querySelectorAll('span, p')) {
                            parts.push(node.textContent);
                        }
                        return parts.filter(Boolean).join(' ').trim();
                        """, element);
            } else if (text.isEmpty()) {
                if ("input".equals(tag) || "textarea".equals(tag)) {
                    text = element.getAttribute("value");
                } else if ("a".equals(tag)) {
                    text = element.getAttribute("text") != null ?
                            element.getAttribute("text") :
                            element.getAttribute("href");
                } else {
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
                        
                        // Генерация по классам (SVG: className — SVGAnimatedString)
                        const classAttr = (typeof element.className === 'string'
                            ? element.className
                            : element.getAttribute('class')) || '';
                        if (classAttr) {
                            const classes = classAttr.trim().split(/\\s+/).filter(Boolean);
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
                    "aria-label", "aria-describedby", "aria-hidden", "aria-checked", "aria-expanded",
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

            enrichCheckboxState(webElement, attributes, element);

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

    private void enrichCheckboxState(WebElement webElement, Map<String, String> attributes, InteractiveElement element) {
        String tag = element.getTagName();
        try {
            if ("label".equals(tag)) {
                WebElement checkboxControl = findCheckboxControlInside(webElement);
                if (checkboxControl != null) {
                    attributes.put("has-checkbox", "true");
                    applyCheckboxStateAttributes(checkboxControl, attributes);
                }
            } else if ("input".equals(tag) && "checkbox".equals(element.getType())) {
                attributes.put("has-checkbox", "true");
                attributes.put("checked", String.valueOf(webElement.isSelected()));
                applyKendoTreeCheckboxState(webElement, attributes);
            } else if ("checkbox".equals(attributes.get("role"))) {
                attributes.put("has-checkbox", "true");
                applyCheckboxStateAttributes(webElement, attributes);
            }
        } catch (Exception e) {
            log.debug("Failed to read checkbox state: {}", e.getMessage());
        }
    }

    private WebElement findCheckboxControlInside(WebElement container) {
        try {
            return container.findElement(By.cssSelector("input[type='checkbox']"));
        } catch (NoSuchElementException ignored) {
            try {
                return container.findElement(By.cssSelector("[role='checkbox']"));
            } catch (NoSuchElementException ignored2) {
                return null;
            }
        }
    }

    private void applyKendoTreeCheckboxState(WebElement checkboxInput, Map<String, String> attributes) {
        String id = Optional.ofNullable(checkboxInput.getAttribute("id")).orElse("");
        if (!id.contains("-item-")) {
            return;
        }
        try {
            WebElement treeItem = checkboxInput.findElement(
                    By.xpath("./ancestor::li[contains(@class,'k-treeview-item')][1]")
            );
            String itemClass = Optional.ofNullable(treeItem.getAttribute("class")).orElse("");
            if (itemClass.contains("k-selected") || itemClass.contains("k-checked")) {
                attributes.put("checked", "true");
                attributes.put("aria-checked", "true");
            }
            try {
                WebElement kendoCheckbox = treeItem.findElement(By.cssSelector(".k-checkbox, .k-checkbox-wrap"));
                String kendoClass = Optional.ofNullable(kendoCheckbox.getAttribute("class")).orElse("");
                String ariaChecked = kendoCheckbox.getAttribute("aria-checked");
                if (kendoClass.contains("k-checked") || "true".equalsIgnoreCase(ariaChecked)) {
                    attributes.put("checked", "true");
                    attributes.put("aria-checked", "true");
                }
            } catch (NoSuchElementException ignored) {
                // Kendo markup variant without explicit checkbox wrapper
            }
        } catch (NoSuchElementException ignored) {
            // Not inside a Kendo tree item
        }
    }

    private void applyCheckboxStateAttributes(WebElement checkboxControl, Map<String, String> attributes) {
        try {
            if ("input".equalsIgnoreCase(checkboxControl.getTagName())) {
                attributes.put("checked", String.valueOf(checkboxControl.isSelected()));
            }
        } catch (Exception ignored) {
            // role=checkbox span has no isSelected
        }
        String ariaChecked = checkboxControl.getAttribute("aria-checked");
        if (ariaChecked != null) {
            attributes.put("aria-checked", ariaChecked);
            attributes.put("checked", ariaChecked);
        }
        String cls = checkboxControl.getAttribute("class");
        if (cls != null && cls.contains("Mui-checked")) {
            attributes.put("checked", "true");
            attributes.put("aria-checked", "true");
        }
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

    private void waitForPageReady(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            wait.until(d -> {
                JavascriptExecutor js = (JavascriptExecutor) d;
                if (!"complete".equals(js.executeScript("return document.readyState"))) {
                    return false;
                }
                Long count = (Long) js.executeScript(
                        "return document.querySelectorAll('input, button, a, select, textarea').length");
                return count != null && count > 0;
            });
        } catch (Exception e) {
            log.debug("Page ready wait finished with: {}", e.getMessage());
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
