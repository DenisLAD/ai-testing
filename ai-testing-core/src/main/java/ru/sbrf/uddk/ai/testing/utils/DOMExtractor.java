package ru.sbrf.uddk.ai.testing.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.util.List;
import java.util.Map;

public class DOMExtractor {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static List<Map<String, Object>> extractRelevantDom(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        String script = """
                const elements = Array.from(document.querySelectorAll('*'));
                return elements.filter(el => el.offsetParent !== null).map(el => ({
                    tag: el.tagName.toLowerCase(),
                    id: el.id || null,
                    className: el.className ? el.className.split(' ')[0] : null, // первая часть класса
                    text: (el.innerText || '').trim().slice(0, 60),
                    type: el.getAttribute('type') || null,
                    ariaLabel: el.getAttribute('aria-label') || null,
                    placeholder: el.getAttribute('placeholder') || null,
                    tagAndText: `<${el.tagName.toLowerCase()}>` +
                                (el.id ? '#' + el.id : '') +
                                (el.className ? '.' + el.className.split(' ')[0] : '') +
                                (el.innerText?.trim().slice(0, 25) || '')
                }));
                """;

        String rawJson = (String) js.executeScript(script);
        try {
            return mapper.readValue(rawJson, List.class);
        } catch (Exception e) {
            throw new RuntimeException("DOM extraction failed: " + e.getMessage(), e);
        }
    }

    public static String toCompactContext(List<Map<String, Object>> elements) {
        return elements.stream()
                .limit(100) // ограничение контекста
                .map(el -> (String) el.get("tagAndText"))
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}