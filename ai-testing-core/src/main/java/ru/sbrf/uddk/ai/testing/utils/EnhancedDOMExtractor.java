package ru.sbrf.uddk.ai.testing.utils;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.util.regex.Pattern;

/**
 * Улучшенный экстрактор DOM для передачи LLM
 * 
 * Особенности:
 * - Удаляет html, head, body теги (не вводят в заблуждение)
 * - Оставляет только видимую область
 * - Добавляет маркеры структуры
 * - Сжимает до ключевых элементов
 */
@Slf4j
public class EnhancedDOMExtractor {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern EMPTY_TAG_PATTERN = Pattern.compile("<(div|span|p|li|td|th)[^>]*>\\s*</\\1>");
    
    /**
     * Извлекает оптимизированный DOM для LLM
     */
    public static String extractForLLM(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            String script = """
                    function extractDOMForLLM() {
                        // Проверяем видимость элемента
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
                        
                        // Проверяем интерактивность
                        function isInteractive(el) {
                            const tag = el.tagName.toLowerCase();
                            const interactiveTags = ['a', 'button', 'input', 'select', 'textarea', 
                                'details', 'summary', 'video', 'audio', '[contenteditable]'];
                            
                            if (interactiveTags.some(t => tag === t || tag.endsWith(t))) return true;
                            
                            const role = el.getAttribute('role');
                            const interactiveRoles = ['button', 'link', 'textbox', 'checkbox', 
                                'radio', 'select', 'menu', 'menuitem', 'tab', 'switch'];
                            
                            return role && interactiveRoles.includes(role.toLowerCase());
                        }
                        
                        // Проверяем важность элемента
                        function isImportant(el) {
                            // Интерактивные элементы
                            if (isInteractive(el)) return true;
                            
                            // Заголовки
                            const tag = el.tagName.toLowerCase();
                            if (['h1', 'h2', 'h3', 'h4', 'h5', 'h6'].includes(tag)) return true;
                            
                            // Элементы с текстом
                            if (el.textContent && el.textContent.trim().length > 0 && 
                                el.textContent.trim().length < 500) {
                                return true;
                            }
                            
                            // Элементы с изображениями
                            if (el.querySelector('img')) return true;
                            
                            return false;
                        }
                        
                        // Клонируем только важные видимые элементы
                        function cloneImportantElements(root) {
                            const fragment = document.createDocumentFragment();
                            
                            const walker = document.createTreeWalker(
                                root,
                                NodeFilter.SHOW_ELEMENT,
                                null,
                                false
                            );
                            
                            const addedElements = new Set();
                            
                            while (walker.nextNode()) {
                                const el = walker.currentNode;
                                
                                if (!isElementVisible(el)) continue;
                                
                                // Пропускаем script, style, link, meta
                                const tag = el.tagName.toLowerCase();
                                if (['script', 'style', 'link', 'meta', 'noscript'].includes(tag)) {
                                    continue;
                                }
                                
                                // Пропускаем SVG если он большой
                                if (tag === 'svg' && el.outerHTML.length > 1000) {
                                    continue;
                                }
                                
                                // Проверяем важность
                                if (!isImportant(el)) continue;
                                
                                // Избегаем дублирования родителей
                                let parent = el.parentElement;
                                let isParentAdded = false;
                                while (parent && parent !== root) {
                                    if (addedElements.has(parent)) {
                                        isParentAdded = true;
                                        break;
                                    }
                                    parent = parent.parentElement;
                                }
                                
                                if (isParentAdded) continue;
                                
                                // Клонируем элемент
                                const clone = el.cloneNode(false);
                                
                                // Копируем только важные атрибуты
                                const attrsToKeep = ['id', 'class', 'type', 'value', 'placeholder',
                                    'href', 'src', 'alt', 'title', 'role', 'aria-label',
                                    'aria-describedby', 'data-testid', 'data-qa', 'name',
                                    'for', 'tabindex', 'disabled', 'required', 'checked'];
                                
                                for (const attr of el.attributes) {
                                    if (attrsToKeep.includes(attr.name.toLowerCase())) {
                                        clone.setAttribute(attr.name, attr.value);
                                    }
                                }
                                
                                // Добавляем текстовый контент если есть
                                if (el.textContent && el.textContent.trim().length > 0) {
                                    const text = el.textContent.trim().substring(0, 200);
                                    if (!['input', 'img', 'br', 'hr'].includes(tag)) {
                                        clone.textContent = text;
                                    }
                                }
                                
                                fragment.appendChild(clone);
                                addedElements.add(el);
                            }
                            
                            return fragment;
                        }
                        
                        // Основная логика
                        const root = document.body || document.documentElement;
                        const visibleDOM = cloneImportantElements(root);
                        
                        const tempDiv = document.createElement('div');
                        tempDiv.appendChild(visibleDOM);
                        
                        return tempDiv.innerHTML;
                    }
                    
                    return extractDOMForLLM();
                    """;
            
            String result = (String) js.executeScript(script);
            
            if (result == null || result.isEmpty()) {
                return extractFallback(driver);
            }
            
            // Пост-обработка
            result = postProcess(result);
            
            return result;
            
        } catch (Exception e) {
            log.error("Enhanced DOM extraction failed", e);
            return extractFallback(driver);
        }
    }
    
    /**
     * Пост-обработка DOM
     */
    private static String postProcess(String dom) {
        if (dom == null || dom.isEmpty()) {
            return "";
        }
        
        String result = dom;
        
        // 1. Удаляем html, head, body теги (оставляем только содержимое)
        result = result.replaceAll("(?i)<html[^>]*>", "");
        result = result.replaceAll("(?i)</html>", "");
        result = result.replaceAll("(?i)<head[^>]*>.*?</head>", "");
        result = result.replaceAll("(?i)<body[^>]*>", "");
        result = result.replaceAll("(?i)</body>", "");
        
        // 2. Удаляем пустые теги
        result = EMPTY_TAG_PATTERN.matcher(result).replaceAll("");
        
        // 3. Нормализуем пробелы
        result = WHITESPACE_PATTERN.matcher(result).replaceAll(" ");
        
        // 4. Удаляем комментарии
        result = result.replaceAll("<!--.*?-->", "");
        
        // 5. Добавляем маркеры начала и конца
        result = "<!-- VISIBLE DOM START -->\n" + result.trim() + "\n<!-- VISIBLE DOM END -->";
        
        return result;
    }
    
    /**
     * Fallback метод
     */
    private static String extractFallback(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            String script = """
                    return document.body ? document.body.innerHTML : document.documentElement.innerHTML;
                    """;
            
            String result = (String) js.executeScript(script);
            return result != null ? postProcess(result) : "";
            
        } catch (Exception e) {
            log.error("Fallback DOM extraction failed", e);
            return "";
        }
    }
    
    /**
     * Извлекает компактное представление DOM (список элементов)
     */
    public static String extractCompactDOM(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            String script = """
                    function getCompactDOM() {
                        const elements = [];
                        const allElements = document.querySelectorAll('*');
                        
                        for (const el of allElements) {
                            const rect = el.getBoundingClientRect();
                            const style = window.getComputedStyle(el);
                            
                            // Проверяем видимость
                            const isVisible = (
                                el.offsetParent !== null &&
                                rect.width > 0 &&
                                rect.height > 0 &&
                                style.display !== 'none' &&
                                style.visibility !== 'hidden'
                            );
                            
                            if (!isVisible) continue;
                            
                            const tag = el.tagName.toLowerCase();
                            
                            // Пропускаем контейнеры без контента
                            if (['div', 'span'].includes(tag)) {
                                const hasText = el.textContent && el.textContent.trim().length > 0;
                                const hasChildren = el.children.length > 0;
                                const isInteractive = el.onclick || el.getAttribute('role');
                                
                                if (!hasText && !hasChildren && !isInteractive) continue;
                            }
                            
                            // Собираем информацию
                            const info = {
                                tag: tag,
                                id: el.id || null,
                                classes: el.className ? el.className.split(' ').filter(c => c) : [],
                                text: (el.innerText || el.textContent || '').trim().substring(0, 100),
                                role: el.getAttribute('role'),
                                ariaLabel: el.getAttribute('aria-label'),
                                href: el.href,
                                src: el.src,
                                type: el.type,
                                value: el.value,
                                placeholder: el.placeholder
                            };
                            
                            // Формируем строковое представление
                            let repr = '<' + tag;
                            if (info.id) repr += ' id="' + info.id + '"';
                            if (info.classes.length > 0) repr += ' class="' + info.classes[0] + '"';
                            if (info.role) repr += ' role="' + info.role + '"';
                            if (info.ariaLabel) repr += ' aria-label="' + info.ariaLabel + '"';
                            if (info.href && info.href !== '#') repr += ' href="' + info.href + '"';
                            if (info.type) repr += ' type="' + info.type + '"';
                            if (info.value) repr += ' value="' + info.value + '"';
                            if (info.placeholder) repr += ' placeholder="' + info.placeholder + '"';
                            repr += '>';
                            
                            if (info.text) {
                                repr += ' "' + info.text.substring(0, 50) + '"';
                            }
                            
                            repr += '</' + tag + '>';
                            
                            elements.push(repr);
                        }
                        
                        return elements.slice(0, 100).join('\\n');
                    }
                    
                    return getCompactDOM();
                    """;
            
            return (String) js.executeScript(script);
            
        } catch (Exception e) {
            log.error("Compact DOM extraction failed", e);
            return "";
        }
    }
}
