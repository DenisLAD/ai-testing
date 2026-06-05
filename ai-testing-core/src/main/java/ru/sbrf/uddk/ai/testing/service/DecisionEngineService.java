package ru.sbrf.uddk.ai.testing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import ru.sbrf.uddk.ai.testing.entity.DiscoveredIssue;
import ru.sbrf.uddk.ai.testing.entity.InteractiveElement;
import ru.sbrf.uddk.ai.testing.entity.consts.IssueSeverity;
import ru.sbrf.uddk.ai.testing.entity.consts.IssueType;
import ru.sbrf.uddk.ai.testing.domain.action.ActionFactory;
import ru.sbrf.uddk.ai.testing.domain.action.TestAgentAction;
import ru.sbrf.uddk.ai.testing.model.AgentObservation;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DecisionEngineService {

    @Setter(onMethod_ = @Autowired)
    private ChatClient chatClient;

    @Setter(onMethod_ = @Autowired)
    private ActionFactory actionFactory;

    @Setter(onMethod_ = @Autowired)
    private ObjectMapper objectMapper;

    private static final int PROMPT_ELEMENT_LIMIT = 25;
    private static final Set<String> NOISE_TAGS = Set.of("svg", "path", "g");
    private static final Pattern GOAL_PHRASE_PATTERN = Pattern.compile("[«\"']([^«\"']{2,})[»\"']");

    public static String removeStyleAndScriptTagsOptimized(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Предварительно компилируем паттерны для производительности
        Pattern[] patterns = {
                Pattern.compile("(?is)<style\\b[^>]*>.*?</style>"),  // (?is) - case insensitive + dotall
                Pattern.compile("(?is)<script\\b[^>]*>.*?</script>"),
                Pattern.compile("(?i)<(style|script)\\b[^>]*/>"),
                Pattern.compile("(?i)<(style|script)\\b[^>]*>")
        };

        String result = input;
        for (Pattern pattern : patterns) {
            result = pattern.matcher(result).replaceAll("");
        }

        return result;
    }

    public TestAgentAction decideNextAction(AgentObservation observation) {
        try {
            if (isFormHeavyPage(observation.getVisibleElements())) {
                Decision preempt = new Decision();
                List<AgentAction> history = observation.getPreviousActions() != null
                        ? observation.getPreviousActions()
                        : List.of();
                Set<String> excluded = collectRecentClickTargets(history, 6);
                if (tryAdvanceFormWorkflow(preempt, observation, observation.getGoalDescription(), excluded)
                        || tryAdvanceFormWorkflow(preempt, observation, observation.getGoalDescription(), Set.of())) {
                    log.info("Preempted AI with form workflow: action={}, target={}",
                            preempt.getAction(), truncate(preempt.getTarget(), 60));
                    return actionFactory.create(toDomainDecision(preempt));
                }
                Decision fallback = new Decision();
                applyGenericLoopFallback(fallback, observation);
                if (fallback.getAction() != null && !fallback.getAction().isBlank()) {
                    log.info("Blocked AI on form page, using fallback: action={}", fallback.getAction());
                    return actionFactory.create(toDomainDecision(fallback));
                }
            }

            String prompt = buildDecisionPrompt(observation);
            log.debug("Sending prompt to AI: {}", prompt.substring(0, Math.min(500, prompt.length())));

            String aiResponse = chatClient.prompt().system("no_think").user(prompt).call().content();
            log.debug("AI Response: {}", aiResponse);

            Pattern pattern = Pattern.compile("(?i)<think>.*?</think>", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(aiResponse);
            String result = matcher.replaceAll("");

            Decision decision = parseDecision(result, observation);
            steerNavigationTowardGoal(decision, observation);
            refineClickTarget(decision, observation);
            refineNavigateTarget(decision, observation);
            suppressDistractingNavigation(decision, observation);
            detectAndBreakLoop(decision, observation);
            log.info("AI Decision: action={}, target={}, reason={}",
                    decision.getAction(), decision.getTarget(), decision.getReason());

            return actionFactory.create(toDomainDecision(decision));

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("405")) {
                log.error("LLM API returned 405 — проверьте SPRING_AI_OPENAI_BASE_URL "
                        + "(должен быть OpenAI-совместимый endpoint, напр. https://openrouter.ai/api/v1)", e);
            } else {
                log.error("Failed to decide next action", e);
            }
            return getFallbackAction(observation);
        }
    }

    private String buildDecisionPrompt(AgentObservation observation) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("""
                Ты автономный агент для тестирования веб-приложений.
                Твоя задача: принимать решения о следующих действиях для тестирования.

                === ИНФОРМАЦИЯ О СТРАНИЦЕ ===
                URL: %s
                Заголовок: %s
                Цель тестирования: %s
                Прогресс: %.1f%%

                """.formatted(
                observation.getUrl(),
                observation.getPageTitle(),
                observation.getGoalDescription(),
                observation.getGoalProgress() != null ? observation.getGoalProgress() * 100 : 0
        ));

        if (isFormHeavyPage(observation.getVisibleElements())) {
            prompt.append("""

                    === СТРАНИЦА С ФОРМОЙ ===
                    На странице много полей формы — работай с элементами в основной области контента.
                    Чекбоксы групп параметров — это label, не input дерева (input#_*-item-N).
                    Элементы input#_*-item-N — чекбоксы дерева (Kendo TreeView), не параметры формы.
                    Не кликай disabled-кнопки: сначала выполни предварительные шаги из цели (чекбоксы, дерево).
                    Для чекбоксов параметров кликай label; для дерева — input#_*-item-N.
                    Не уходи навигацией сайдбара, пока форма не заполнена по цели.
                    Основная кнопка действия может стать доступной только после выбора в дереве.

                    """);
        }

        List<InteractiveElement> promptElements = prepareElementsForPrompt(
                observation.getVisibleElements(),
                observation.getGoalDescription(),
                observation.getUrl()
        );

        if (!promptElements.isEmpty()) {
            List<InteractiveElement> goalMatches = findGoalMatchingElements(promptElements, observation.getGoalDescription());
            if (!goalMatches.isEmpty()) {
                prompt.append("=== ЭЛЕМЕНТЫ, СОВПАДАЮЩИЕ С ЦЕЛЬЮ (уже на странице) ===\n");
                prompt.append(formatElements(goalMatches, goalMatches.size()));
                prompt.append("\nЕсли цель упоминает эти пункты — кликай по ним напрямую, не прокручивай страницу.\n\n");
            }

            int totalFound = observation.getVisibleElements() != null ? observation.getVisibleElements().size() : promptElements.size();
            prompt.append("=== ВИДИМЫЕ ИНТЕРАКТИВНЫЕ ЭЛЕМЕНТЫ (")
                    .append(totalFound)
                    .append(" найдено, показаны ключевые ")
                    .append(Math.min(PROMPT_ELEMENT_LIMIT, promptElements.size()))
                    .append(") ===\n");
            prompt.append("Используй селектор из списка для CLICK/TYPE. Не выдумывай элементы, которых нет в списке.\n");
            prompt.append(formatElements(promptElements, PROMPT_ELEMENT_LIMIT));
            prompt.append("\n\n");
        } else {
            prompt.append("Нет видимых интерактивных элементов на странице.\n\n");
        }

        prompt.append("""
                === ВИДИМЫЙ DOM (сокращён) ===
                (Показаны только видимые интерактивные элементы, теги html/head/body удалены)
                %s

                """.formatted(removeStyleAndScriptTagsOptimized(observation.getPageSource())));

        // История действий
        if (observation.getPreviousActions() != null && !observation.getPreviousActions().isEmpty()) {
            prompt.append("История последних действий:\n");
            prompt.append(formatActionHistory(observation.getPreviousActions()));
            prompt.append("\n\n");
        }

        // Обнаруженные проблемы
        if (observation.getDiscoveredIssues() != null && !observation.getDiscoveredIssues().isEmpty()) {
            prompt.append("Уже обнаруженные проблемы:\n");
            prompt.append(formatIssues(observation.getDiscoveredIssues()));
            prompt.append("\n\n");
        }

        // Инструкции по выбору действия
        prompt.append("""
                Доступные типы действий:
                1. CLICK - кликнуть на элемент (указать селектор в target)
                2. TYPE - ввести текст в поле (указать селектор в target, текст в value)
                3. NAVIGATE_BACK - вернуться на предыдущую страницу
                4. NAVIGATE_FORWARD - перейти вперед
                5. NAVIGATE_TO - перейти по URL (указать URL в target)
                6. ASSERT_PRESENCE - проверить наличие элемента
                7. ASSERT_TEXT - проверить текст элемента
                8. SCROLL_UP - прокрутить вверх
                9. SCROLL_DOWN - прокрутить вниз
                10. REFRESH - обновить страницу
                11. EXPLORE_MENU - исследовать меню/навигацию
                12. EXPLORE_FORMS - исследовать формы на странице
                13. TEST_VALIDATION - проверить валидацию полей
                14. REPORT_ISSUE - сообщить о проблеме (указать описание в value)
                15. COMPLETE - завершить тестирование
                            
                Критерии выбора:
                - Приоритет у новых, неисследованных элементов
                - Избегай повторения одних и тех же действий
                - Если видишь форму - исследуй ее
                - Если видишь ошибку - зафиксируй ее
                - Если прогресс > 80% - подумай о завершении
                - Если цель упоминает пункт меню, кликай по элементу с одной строкой текста (span/div) или ссылке (a), а не по родительскому контейнеру со всем меню
                - Элемент с текстом из нескольких пунктов меню (3+ строк) — контейнер, по нему не кликай
                - Раскрывающийся пункт меню: сначала клик по заголовку группы, затем по подпункту-ссылке
                - На страницах с формой не уходи назад по навигации, пока не выполнены шаги цели на текущей странице
                - Не кликай disabled-кнопки; смотри поля «Включён» и «Отмечен» у элементов
                - NAVIGATE_TO — только по href из списка видимых элементов; не выдумывай URL
                - Не используй EXPLORE_MENU/EXPLORE_FORMS если есть конкретные элементы в списке — лучше CLICK
                - Если в истории действий есть повторы — выбери ДРУГОЕ действие, не повторяй тот же клик
                            
                Верни ответ в строгом JSON формате:
                {
                  "action": "ACTION_TYPE",
                  "target": "element_selector_or_url",
                  "value": "optional_text_or_value",
                  "reason": "обоснование выбора на русском",
                  "expectedOutcome": "что ожидаешь увидеть"
                }
                            
                Если элемент не найден или страница пустая, выбери REFRESH или NAVIGATE_BACK.
                """);

        return prompt.toString();
    }

    private List<InteractiveElement> prepareElementsForPrompt(
            List<InteractiveElement> elements,
            String goalDescription,
            String pageUrl
    ) {
        if (elements == null || elements.isEmpty()) {
            return List.of();
        }

        boolean formHeavyPage = isFormHeavyPage(elements);
        boolean contentHubPage = isContentHubPage(elements, pageUrl);
        Set<String> seen = new LinkedHashSet<>();
        List<InteractiveElement> filtered = new ArrayList<>();

        for (InteractiveElement element : elements) {
            if (!isUsefulForPrompt(element)) {
                continue;
            }
            if ((formHeavyPage || contentHubPage) && isLikelySidebarNav(element, pageUrl)) {
                continue;
            }
            addUniqueElement(filtered, seen, element);
            expandMultilineElement(filtered, seen, element);
        }

        Comparator<InteractiveElement> byRelevance = formHeavyPage
                ? Comparator
                    .comparingInt((InteractiveElement el) -> formElementRelevanceScore(el, goalDescription)).reversed()
                    .thenComparingInt((InteractiveElement el) -> goalRelevanceScore(el, goalDescription)).reversed()
                : Comparator
                    .comparingInt((InteractiveElement el) -> contentHubPage && !isLikelySidebarNav(el, pageUrl) ? 50 : 0)
                    .thenComparingInt((InteractiveElement el) -> goalRelevanceScore(el, goalDescription)).reversed();

        filtered.sort(byRelevance
                .thenComparingInt(element -> actionTagPriority(element.getTagName()))
                .thenComparing(element -> Optional.ofNullable(element.getText()).orElse("")));

        return filtered;
    }

    private void addUniqueElement(List<InteractiveElement> target, Set<String> seen, InteractiveElement element) {
        String dedupeKey = buildElementDedupeKey(element);
        if (seen.add(dedupeKey)) {
            target.add(element);
        }
    }

    private void expandMultilineElement(List<InteractiveElement> target, Set<String> seen, InteractiveElement element) {
        String text = element.getText();
        if (text == null || !text.contains("\n")) {
            return;
        }
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.length() < 3) {
                continue;
            }
            InteractiveElement lineElement = copyElementWithText(element, trimmed);
            addUniqueElement(target, seen, lineElement);
        }
    }

    private InteractiveElement copyElementWithText(InteractiveElement source, String text) {
        InteractiveElement copy = new InteractiveElement();
        copy.setSessionId(source.getSessionId());
        copy.setTagName(source.getTagName());
        copy.setText(text);
        copy.setIdAttr(source.getIdAttr());
        copy.setName(source.getName());
        copy.setType(source.getType());
        copy.setPlaceholder(source.getPlaceholder());
        copy.setClasses(source.getClasses());
        copy.setSelector(source.getSelector());
        copy.setXpath(source.getXpath());
        copy.setBounds(source.getBounds());
        copy.setIsVisible(source.getIsVisible());
        copy.setIsEnabled(source.getIsEnabled());
        copy.setIsInteractable(source.getIsInteractable());
        copy.setInteractionType(source.getInteractionType());
        copy.setAttributes(source.getAttributes() != null ? new HashMap<>(source.getAttributes()) : null);
        copy.setDiscoveredAt(source.getDiscoveredAt());
        copy.setTimesInteracted(source.getTimesInteracted());
        return copy;
    }

    private List<InteractiveElement> findGoalMatchingElements(List<InteractiveElement> elements, String goalDescription) {
        return elements.stream()
                .filter(element -> goalRelevanceScore(element, goalDescription) >= 50)
                .limit(10)
                .collect(Collectors.toList());
    }

    private boolean isUsefulForPrompt(InteractiveElement element) {
        if (element == null || element.getTagName() == null) {
            return false;
        }
        if (isMenuListContainer(element)) {
            return false;
        }
        String text = Optional.ofNullable(element.getText()).orElse("").trim().toLowerCase(Locale.ROOT);
        if (text.equals("админ") || text.equals("клиент")) {
            return false;
        }
        String tag = element.getTagName().toLowerCase(Locale.ROOT);
        if (NOISE_TAGS.contains(tag)) {
            return false;
        }
        if (element.getIdAttr() != null && !element.getIdAttr().isBlank()) {
            return true;
        }
        if ("input".equals(tag) || "button".equals(tag) || "a".equals(tag) || "textarea".equals(tag)
                || "select".equals(tag) || "label".equals(tag)) {
            return true;
        }
        return text.length() >= 2;
    }

    private String buildElementDedupeKey(InteractiveElement element) {
        String text = Optional.ofNullable(element.getText()).orElse("").trim().toLowerCase(Locale.ROOT);
        if (!text.isEmpty()) {
            return element.getTagName() + "|" + text;
        }
        if (element.getIdAttr() != null && !element.getIdAttr().isBlank()) {
            return element.getTagName() + "|#" + element.getIdAttr();
        }
        return element.getTagName() + "|" + Optional.ofNullable(element.getSelector()).orElse("");
    }

    private int goalRelevanceScore(InteractiveElement element, String goalDescription) {
        if (goalDescription == null || goalDescription.isBlank()) {
            return 0;
        }
        String goal = goalDescription.toLowerCase(Locale.ROOT);
        String text = Optional.ofNullable(element.getText()).orElse("").trim().toLowerCase(Locale.ROOT);
        if (text.length() < 2) {
            return 0;
        }

        int score = 0;
        for (String line : text.split("\\R")) {
            score = Math.max(score, scoreTextAgainstGoal(line.trim().toLowerCase(Locale.ROOT), goal, goalDescription));
        }
        if (score == 0) {
            score = scoreTextAgainstGoal(text, goal, goalDescription);
        }
        return score;
    }

    private int scoreTextAgainstGoal(String text, String goal, String goalDescription) {
        if (text.length() < 2) {
            return 0;
        }
        int score = 0;
        if (goal.contains(text)) {
            score += 100;
        }
        for (String token : text.split("\\s+")) {
            if (token.length() >= 4 && goal.contains(token)) {
                score += 40;
            }
        }
        Matcher quoted = GOAL_PHRASE_PATTERN.matcher(goalDescription);
        while (quoted.find()) {
            String phrase = quoted.group(1).trim().toLowerCase(Locale.ROOT);
            if (phrase.equals(text) || text.contains(phrase) || phrase.contains(text)) {
                score += 120;
            }
        }
        if (text.equals("админ") && !goal.contains("админ")) {
            score -= 200;
        }
        return score;
    }

    private void steerNavigationTowardGoal(Decision decision, AgentObservation observation) {
        String goal = observation.getGoalDescription();
        String url = Optional.ofNullable(observation.getUrl()).orElse("");
        if (goal == null || observation.getVisibleElements() == null) {
            return;
        }

        List<String> navPhrases = filterPendingNavigationPhrases(
                extractGoalPhrases(goal),
                url,
                observation.getVisibleElements()
        );
        if (navPhrases.isEmpty()) {
            return;
        }

        if (url.contains("/admin") && !goal.toLowerCase(Locale.ROOT).contains("админ")) {
            recoverNavigationTowardGoal(decision, observation, navPhrases);
            return;
        }

        if (!isNavigationPhase(url, observation)) {
            return;
        }

        if ("EXPLORE_MENU".equals(decision.getAction()) || "EXPLORE_FORMS".equals(decision.getAction())) {
            recoverNavigationTowardGoal(decision, observation, navPhrases);
            return;
        }

        if ("CLICK".equals(decision.getAction())) {
            if (isAdminPanelButtonClick(decision, observation)) {
                recoverNavigationTowardGoal(decision, observation, navPhrases);
                return;
            }
            if (!clickAlignsWithNavigationGoal(decision, observation, navPhrases)) {
                findBestGoalNavigationTarget(observation, navPhrases)
                        .ifPresent(element -> setClickDecision(decision, element, "Коррекция навигации по цели"));
            }
        }
    }

    private boolean isNavigationGoalActive(String goal, List<InteractiveElement> elements) {
        return goal != null && !extractGoalPhrases(goal).isEmpty() && !isFormHeavyPage(elements);
    }

    private boolean isNavigationPhase(String url, AgentObservation observation) {
        if (url.contains("keycloak") || url.contains("openid-connect")) {
            return false;
        }
        return isNavigationGoalActive(observation.getGoalDescription(), observation.getVisibleElements());
    }

    private void recoverNavigationTowardGoal(
            Decision decision,
            AgentObservation observation,
            List<String> navPhrases
    ) {
        findBestGoalNavigationTarget(observation, navPhrases).ifPresentOrElse(
                element -> setClickDecision(decision, element, "Навигация к разделу из цели"),
                () -> findBestVisibleHref(observation, navPhrases).ifPresent(href -> {
                    decision.setAction("NAVIGATE_TO");
                    decision.setTarget(href);
                    decision.setReason("Переход по ссылке из видимых элементов");
                })
        );
    }

    private boolean isAdminPanelButtonClick(Decision decision, AgentObservation observation) {
        Optional<InteractiveElement> matched = findElementBySelector(observation, decision.getTarget());
        if (matched.isPresent() && isAdminPanelElement(matched.get())) {
            return true;
        }
        String target = Optional.ofNullable(decision.getTarget()).orElse("").toLowerCase(Locale.ROOT);
        return target.contains("админ") && target.contains("button");
    }

    private boolean isAdminPanelElement(InteractiveElement element) {
        return "админ".equals(Optional.ofNullable(element.getText()).orElse("").trim().toLowerCase(Locale.ROOT));
    }

    private boolean clickAlignsWithNavigationGoal(
            Decision decision,
            AgentObservation observation,
            List<String> phrases
    ) {
        Optional<InteractiveElement> matched = findElementBySelector(observation, decision.getTarget());
        if (matched.isEmpty()) {
            String reason = Optional.ofNullable(decision.getReason()).orElse("").toLowerCase(Locale.ROOT);
            return phrases.stream().anyMatch(reason::contains);
        }
        InteractiveElement element = matched.get();
        if (isMenuListContainer(element)) {
            return false;
        }
        if (!matchesAnyGoalPhrase(element, phrases)) {
            return false;
        }
        return navigationElementScore(
                element,
                observation.getGoalDescription(),
                observation.getUrl(),
                observation.getVisibleElements()
        ) >= 50;
    }

    private boolean isMenuListContainer(InteractiveElement element) {
        String text = Optional.ofNullable(element.getText()).orElse("");
        if (!text.contains("\n")) {
            return false;
        }
        long lines = Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(line -> line.length() >= 2)
                .count();
        return lines >= 3;
    }

    private boolean isPreciseNavigationTarget(InteractiveElement element) {
        if (isMenuListContainer(element) || isAdminPanelElement(element)) {
            return false;
        }
        String text = Optional.ofNullable(element.getText()).orElse("").trim();
        return text.length() >= 2 && !text.contains("\n");
    }

    private int navigationElementScore(
            InteractiveElement element,
            String goal,
            String pageUrl,
            List<InteractiveElement> allElements
    ) {
        int score = goalRelevanceScore(element, goal);
        String tag = Optional.ofNullable(element.getTagName()).orElse("").toLowerCase(Locale.ROOT);
        if ("a".equals(tag)) {
            score += 80;
            if (!hrefFromElement(element).isBlank()) {
                score += 40;
            }
        } else if ("button".equals(tag)) {
            score += 40;
        }
        if (isPreciseNavigationTarget(element)) {
            score += 120;
        }
        if (pageUrl != null && allElements != null && isContentHubPage(allElements, pageUrl)) {
            if (!isLikelySidebarNav(element, pageUrl)) {
                score += 150;
            } else {
                score -= 250;
            }
        }
        if (pageUrl != null && isCurrentPageLink(element, pageUrl)) {
            score -= 400;
        }
        if (isMenuListContainer(element)) {
            score -= 500;
        }
        return score;
    }

    private Optional<InteractiveElement> findBestGoalNavigationTarget(
            AgentObservation observation,
            List<String> goalPhrases
    ) {
        return findBestGoalNavigationTarget(observation, goalPhrases, Set.of());
    }

    private Optional<InteractiveElement> findBestGoalNavigationTarget(
            AgentObservation observation,
            List<String> goalPhrases,
            Set<String> excludedTargets
    ) {
        if (observation.getVisibleElements() == null || goalPhrases.isEmpty()) {
            return Optional.empty();
        }
        String url = Optional.ofNullable(observation.getUrl()).orElse("");
        List<String> pendingPhrases = filterPendingNavigationPhrases(goalPhrases, url, observation.getVisibleElements());
        List<String> orderedPhrases = pendingPhrases.stream()
                .distinct()
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .toList();
        for (String phrase : orderedPhrases) {
            Optional<InteractiveElement> match = observation.getVisibleElements().stream()
                    .filter(element -> !isMenuListContainer(element))
                    .filter(element -> !isAdminPanelElement(element))
                    .filter(element -> !isCurrentPageLink(element, url))
                    .filter(element -> excludedTargets.isEmpty()
                            || !excludedTargets.contains(normalizeTarget(resolveActionTarget(element))))
                    .filter(element -> {
                        String tag = Optional.ofNullable(element.getTagName()).orElse("").toLowerCase(Locale.ROOT);
                        return "a".equals(tag) || "button".equals(tag) || "div".equals(tag) || "span".equals(tag);
                    })
                    .filter(element -> matchesAnyGoalPhrase(element, List.of(phrase)))
                    .max(Comparator.comparingInt(element -> navigationElementScore(
                            element, observation.getGoalDescription(), url, observation.getVisibleElements())));
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private Optional<String> findBestVisibleHref(AgentObservation observation, List<String> goalPhrases) {
        if (observation.getVisibleElements() == null || goalPhrases.isEmpty()) {
            return Optional.empty();
        }
        String url = Optional.ofNullable(observation.getUrl()).orElse("");
        return observation.getVisibleElements().stream()
                .filter(element -> "a".equalsIgnoreCase(element.getTagName()))
                .filter(element -> !hrefFromElement(element).isBlank())
                .filter(element -> !isCurrentPageLink(element, url))
                .filter(element -> !isLikelySidebarNav(element, url) || !isContentHubPage(observation.getVisibleElements(), url))
                .filter(element -> matchesAnyGoalPhrase(element, goalPhrases))
                .max(Comparator.comparingInt(element -> navigationElementScore(
                        element, observation.getGoalDescription(), url, observation.getVisibleElements())))
                .map(this::hrefFromElement)
                .filter(href -> !href.isBlank() && !href.startsWith("#") && !href.startsWith("javascript:"));
    }

    private boolean isHrefVisibleInElements(AgentObservation observation, String targetPath) {
        String normalized = normalizeUrlPath(targetPath);
        if (normalized.isBlank() || observation.getVisibleElements() == null) {
            return false;
        }
        return observation.getVisibleElements().stream()
                .map(this::hrefFromElement)
                .map(this::normalizeUrlPath)
                .filter(href -> !href.isBlank())
                .anyMatch(href -> href.equals(normalized) || normalized.startsWith(href + "/"));
    }

    private void refineNavigateTarget(Decision decision, AgentObservation observation) {
        if (!"NAVIGATE_TO".equals(decision.getAction()) || decision.getTarget() == null) {
            return;
        }
        String target = decision.getTarget().trim();
        String url = Optional.ofNullable(observation.getUrl()).orElse("");

        if (isFormHeavyPage(observation.getVisibleElements()) && isNavigatingToParentPage(url, target)) {
            log.info("Blocked NAVIGATE_TO to parent page while form task is in progress");
            applyGenericLoopFallback(decision, observation);
            return;
        }

        List<String> goalPhrases = extractGoalPhrases(observation.getGoalDescription());
        if (!goalPhrases.isEmpty() && !isHrefVisibleInElements(observation, target)) {
            Optional<String> knownHref = findBestVisibleHref(observation, goalPhrases);
            if (knownHref.isPresent()) {
                decision.setTarget(knownHref.get());
                log.info("Corrected NAVIGATE_TO using visible href: {}", knownHref.get());
            } else {
                findBestGoalNavigationTarget(observation, goalPhrases).ifPresent(element -> {
                    setClickDecision(decision, element, "NAVIGATE_TO заменён кликом: URL не найден среди видимых ссылок");
                    log.info("Replaced guessed NAVIGATE_TO with CLICK on {}", truncate(element.getText(), 40));
                });
            }
        }
    }

    private void refineClickTarget(Decision decision, AgentObservation observation) {
        if (!"CLICK".equals(decision.getAction()) || observation.getVisibleElements() == null) {
            return;
        }

        if (isFormHeavyPage(observation.getVisibleElements())) {
            refineFormClickTarget(decision, observation);
        }

        List<String> goalPhrases = extractGoalPhrases(observation.getGoalDescription());
        if (goalPhrases.isEmpty()) {
            return;
        }

        List<String> relevantPhrases = filterPhrasesByReason(goalPhrases, decision.getReason());
        if (relevantPhrases.isEmpty()) {
            relevantPhrases = goalPhrases;
        }
        final List<String> phrasesForMatch = relevantPhrases;

        Optional<InteractiveElement> bestTarget = findBestGoalNavigationTarget(observation, phrasesForMatch);
        if (bestTarget.isEmpty() || navigationElementScore(
                bestTarget.get(),
                observation.getGoalDescription(),
                observation.getUrl(),
                observation.getVisibleElements()
        ) < 50) {
            return;
        }

        String currentTarget = decision.getTarget() != null ? decision.getTarget() : "";
        boolean ambiguousTarget = observation.getVisibleElements().stream()
                .anyMatch(element -> currentTarget.equals(element.getSelector())
                        && isMenuListContainer(element));
        boolean targetIsContainer = currentTarget.contains("MuiDrawer-root")
                || currentTarget.contains("MuiStack-root");

        if (ambiguousTarget || targetIsContainer) {
            InteractiveElement target = bestTarget.get();
            decision.setTarget(resolveActionTarget(target));
            log.info("Refined CLICK target to {} ({})", target.getText(), decision.getTarget());
        }
    }

    private void refineFormClickTarget(Decision decision, AgentObservation observation) {
        if (isSidebarNavigationClick(decision, observation)) {
            applyGenericLoopFallback(decision, observation);
            return;
        }

        if (correctInvalidFormClickTarget(decision, observation)) {
            return;
        }

        if (correctAccordionStackClick(decision, observation)) {
            return;
        }

        if (correctKendoTreePanelConfusion(decision, observation)) {
            return;
        }

        String target = Optional.ofNullable(decision.getTarget()).orElse("");
        if (target.contains("label")) {
            Optional<InteractiveElement> matched = findElementBySelector(observation, target);
            if (matched.isPresent()) {
                InteractiveElement element = matched.get();
                if (!isCheckboxElement(element)
                        || isExcludedByGoalNegation(element, observation.getGoalDescription())) {
                    applyGenericLoopFallback(decision, observation);
                }
            }
            return;
        }
        if (!target.contains("span") && !target.contains("div:nth-child")) {
            return;
        }

        findLabelForFormClick(decision, observation).ifPresent(element -> {
            decision.setTarget(resolveActionTarget(element));
            log.info("Refined form click to label: {}", truncate(element.getText(), 60));
        });
    }

    private boolean correctInvalidFormClickTarget(Decision decision, AgentObservation observation) {
        String target = Optional.ofNullable(decision.getTarget()).orElse("");
        if (target.isBlank()) {
            return false;
        }

        boolean kendoTreeMisclick = isKendoTreeItemTarget(target)
                && isFormPanelCheckboxIntent(decision.getReason(), observation.getGoalDescription());
        if (kendoTreeMisclick) {
            Set<String> excluded = collectRecentClickTargets(
                    observation.getPreviousActions() != null ? observation.getPreviousActions() : List.of(),
                    6
            );
            if (tryAdvanceFormWorkflow(decision, observation, observation.getGoalDescription(), excluded)) {
                log.info("Blocked kendo tree misclick for form-panel checkbox intent");
                return true;
            }
        }

        boolean inventedId = target.contains("-export-btn") || target.matches("(?i)#_r_\\w+-export-btn");
        boolean unknownTarget = findElementBySelector(observation, target).isEmpty();
        boolean headingSpan = findElementBySelector(observation, target)
                .map(element -> {
                    String text = Optional.ofNullable(element.getText()).orElse("").toLowerCase(Locale.ROOT);
                    return "span".equalsIgnoreCase(element.getTagName())
                            && text.contains("экспорт настроек");
                })
                .orElse(false);

        if (!inventedId && !unknownTarget && !headingSpan) {
            Optional<InteractiveElement> matched = findElementBySelector(observation, target);
            if (matched.isPresent() && isExcludedByGoalNegation(matched.get(), observation.getGoalDescription())) {
                applyGenericLoopFallback(decision, observation);
                log.info("Blocked undesired export checkbox click, using form workflow");
                return true;
            }
            return false;
        }

        Set<String> excluded = collectRecentClickTargets(
                observation.getPreviousActions() != null ? observation.getPreviousActions() : List.of(),
                6
        );
        if (tryAdvanceFormWorkflow(decision, observation, observation.getGoalDescription(), excluded)) {
            log.info("Corrected invalid form click target to {}", truncate(decision.getTarget(), 60));
            return true;
        }

        if (hasTreeItemSelected(observation)
                && findPrimarySubmitButton(observation, observation.getGoalDescription(), true).isEmpty()) {
            decision.setAction("SCROLL_DOWN");
            decision.setTarget(null);
            decision.setReason("Прокрутка к основной кнопке формы");
            return true;
        }

        if (unknownTarget || inventedId) {
            applyGenericLoopFallback(decision, observation);
            return true;
        }
        return false;
    }

    private boolean correctAccordionStackClick(Decision decision, AgentObservation observation) {
        if (!"CLICK".equals(decision.getAction())) {
            return false;
        }
        String target = Optional.ofNullable(decision.getTarget()).orElse("");
        Optional<InteractiveElement> matched = findElementBySelector(observation, target);
        if (matched.isEmpty()) {
            return false;
        }
        InteractiveElement element = matched.get();
        String tag = Optional.ofNullable(element.getTagName()).orElse("").toLowerCase(Locale.ROOT);
        String text = Optional.ofNullable(element.getText()).orElse("").toLowerCase(Locale.ROOT);
        boolean accordionStack = "div".equals(tag)
                && text.contains("модели данных")
                && text.contains("storage")
                && text.contains("страницы");
        if (!accordionStack) {
            return false;
        }
        Set<String> excluded = collectRecentClickTargets(
                observation.getPreviousActions() != null ? observation.getPreviousActions() : List.of(),
                6
        );
        if (tryAdvanceFormWorkflow(decision, observation, observation.getGoalDescription(), excluded)) {
            log.info("Redirected accordion stack click to {}", truncate(decision.getTarget(), 60));
            return true;
        }
        findCollapsedAccordionForGoal(observation, observation.getGoalDescription()).ifPresent(button -> {
            setClickDecision(decision, button, "Форма: раскрыть секцию по цели");
            log.info("Redirected accordion stack click to goal accordion");
        });
        return "CLICK".equals(decision.getAction());
    }

    private boolean correctKendoTreePanelConfusion(Decision decision, AgentObservation observation) {
        if (!"CLICK".equals(decision.getAction())) {
            return false;
        }
        String target = Optional.ofNullable(decision.getTarget()).orElse("");
        if (!isKendoTreeItemTarget(target)) {
            return false;
        }
        Optional<InteractiveElement> matched = findElementBySelector(observation, target);
        if (matched.isEmpty() || !isKendoTreeCheckbox(matched.get())) {
            return false;
        }

        Set<String> excluded = collectRecentClickTargets(
                observation.getPreviousActions() != null ? observation.getPreviousActions() : List.of(),
                6
        );

        if (isFormPanelCheckboxIntent(decision.getReason(), observation.getGoalDescription())) {
            if (tryAdvanceFormWorkflow(decision, observation, observation.getGoalDescription(), excluded)) {
                log.info("Redirected form-panel checkbox intent from kendo tree to form workflow");
                return true;
            }
        }

        return false;
    }

    private static final int MIN_TREE_ITEM_INDEX = 0;

    private boolean isKendoTreeItemTarget(String target) {
        return target != null && target.matches("(?i)#_r_.*-item-\\d+");
    }

    private boolean isFormPanelCheckboxIntent(String reason, String goal) {
        String combined = (Optional.ofNullable(reason).orElse("") + " "
                + Optional.ofNullable(goal).orElse("")).toLowerCase(Locale.ROOT);
        boolean panelCheckbox = combined.contains("параметр")
                || combined.contains("секци")
                || combined.contains("блок")
                || combined.contains("label")
                || combined.contains("чекбокс");
        boolean treeIntent = combined.contains("дерев")
                || combined.contains("item-")
                || combined.contains("treeview")
                || combined.contains("схем");
        return panelCheckbox && !treeIntent;
    }

    private void suppressDistractingNavigation(Decision decision, AgentObservation observation) {
        String url = Optional.ofNullable(observation.getUrl()).orElse("");
        if (!isFormHeavyPage(observation.getVisibleElements())
                && !isContentHubPage(observation.getVisibleElements(), url)) {
            return;
        }

        if ("NAVIGATE_TO".equals(decision.getAction())) {
            String target = Optional.ofNullable(decision.getTarget()).orElse("");
            if (isNavigatingToParentPage(url, target) || isLikelySidebarHref(target, url)) {
                applyGenericLoopFallback(decision, observation);
                log.info("Suppressed distracting NAVIGATE_TO on content page");
            }
            return;
        }

        if ("CLICK".equals(decision.getAction()) && isSidebarNavigationClick(decision, observation)) {
            applyGenericLoopFallback(decision, observation);
            log.info("Suppressed sidebar navigation click on content page");
        }
    }

    private void detectAndBreakLoop(Decision decision, AgentObservation observation) {
        List<AgentAction> actions = observation.getPreviousActions();
        if (actions == null || actions.size() < 2) {
            return;
        }

        String signature = decision.getAction() + "|" + normalizeTarget(decision.getTarget());
        int repeats = countRecentMatching(actions, signature, 6);
        boolean navPingPong = detectNavigationPingPong(actions, 5);
        boolean labelLoop = isFormHeavyPage(observation.getVisibleElements()) && countRecentLabelClicks(actions, 8) >= 2;
        boolean failedClickLoop = countRecentFailedClicks(actions, 8) >= 2;

        if (repeats < 2 && !navPingPong && !labelLoop && !failedClickLoop) {
            return;
        }

        log.warn("Action loop detected (repeats={}, pingPong={}), applying generic fallback", repeats, navPingPong);
        applyGenericLoopFallback(decision, observation);
    }

    private void applyGenericLoopFallback(Decision decision, AgentObservation observation) {
        List<AgentAction> history = observation.getPreviousActions() != null
                ? observation.getPreviousActions()
                : List.of();
        String goal = observation.getGoalDescription();
        String url = Optional.ofNullable(observation.getUrl()).orElse("");

        Set<String> excludedTargets = collectRecentClickTargets(history, 6);
        boolean formHeavy = isFormHeavyPage(observation.getVisibleElements());

        if (formHeavy && tryAdvanceFormWorkflow(decision, observation, goal, excludedTargets)) {
            return;
        }
        if (formHeavy && tryAdvanceFormWorkflow(decision, observation, goal, Set.of())) {
            return;
        }

        if (!formHeavy && isNavigationGoalActive(goal, observation.getVisibleElements())) {
            List<String> navPhrases = filterPendingNavigationPhrases(
                    extractGoalPhrases(goal),
                    url,
                    observation.getVisibleElements()
            );
            Optional<InteractiveElement> navTarget = findBestGoalNavigationTarget(observation, navPhrases, excludedTargets);
            if (navTarget.isPresent()) {
                setClickDecision(decision, navTarget.get(), "Обход зацикливания: точный переход по цели");
                return;
            }
            findBestVisibleHref(observation, navPhrases).ifPresent(href -> {
                decision.setAction("NAVIGATE_TO");
                decision.setTarget(href);
                decision.setReason("Обход зацикливания: переход по видимой ссылке");
            });
            if ("NAVIGATE_TO".equals(decision.getAction()) || "CLICK".equals(decision.getAction())) {
                return;
            }
        }

        if (!areFormPrerequisitesMet(observation, goal)) {
            findBestGoalCheckbox(observation, goal, excludedTargets).ifPresent(checkbox ->
                    setClickDecision(decision, checkbox, "Обход зацикливания: отметить чекбокс по цели"));
        }

        if ("CLICK".equals(decision.getAction())) {
            return;
        }

        findPrimarySubmitButton(observation, goal, false).ifPresent(button ->
                setClickDecision(decision, button, "Обход зацикливания: основное действие"));

        if ("CLICK".equals(decision.getAction())) {
            return;
        }

        Optional<InteractiveElement> enabledGoalButton = findEnabledGoalButton(observation, goal);
        if (enabledGoalButton.isPresent()) {
            setClickDecision(decision, enabledGoalButton.get(), "Обход зацикливания: нажать доступную кнопку по цели");
            return;
        }

        boolean accordionAttemptsExhausted = countRecentFormStepClicks(history, "раскрыть секцию", 15) >= 2;
        if (!accordionAttemptsExhausted) {
            Optional<InteractiveElement> accordionButton = findCollapsedAccordionForGoal(observation, goal);
            if (accordionButton.isPresent()) {
                setClickDecision(decision, accordionButton.get(), "Обход зацикливания: раскрыть секцию по цели");
                return;
            }
        }

        if (formHeavy && goalNeedsTreeSelection(goal)) {
            Optional<InteractiveElement> treeItem = findSelectableTreeItem(observation, goal, excludedTargets);
            if (treeItem.isPresent()) {
                setClickDecision(decision, treeItem.get(), "Обход зацикливания: отметить элемент в дереве");
                return;
            }
            if (countRecentActionType(history, "SCROLL_DOWN", 10) < 4) {
                decision.setAction("SCROLL_DOWN");
                decision.setTarget(null);
                decision.setReason("Обход зацикливания: прокрутка к дереву");
                return;
            }
        }

        if (!historyContainsAction(history, "SCROLL_DOWN")) {
            decision.setAction("SCROLL_DOWN");
            decision.setTarget(null);
            decision.setReason("Обход зацикливания: прокрутка формы для поиска элементов");
            return;
        }

        List<String> goalPhrases = extractGoalPhrases(goal);
        findBestGoalNavigationTarget(observation, goalPhrases).ifPresent(element ->
                setClickDecision(decision, element, "Обход зацикливания: переход к элементу из цели"));
    }

    private boolean tryAdvanceFormWorkflow(
            Decision decision,
            AgentObservation observation,
            String goal,
            Set<String> excludedTargets
    ) {
        if (observation.getVisibleElements() == null) {
            return false;
        }

        List<AgentAction> formHistory = observation.getPreviousActions() != null
                ? observation.getPreviousActions()
                : List.of();
        if (hasSuccessfulFormPrimaryAction(formHistory) || isPrimarySubmitInProgress(observation, goal)) {
            decision.setAction("COMPLETE");
            decision.setTarget(null);
            decision.setReason("Форма: основное действие выполнено");
            return true;
        }

        boolean prerequisitesMet = areFormPrerequisitesMet(observation, goal);
        boolean needsTree = goalNeedsTreeSelection(goal);

        if (!prerequisitesMet) {
            Optional<InteractiveElement> checkbox = findBestGoalCheckbox(observation, goal, excludedTargets);
            if (checkbox.isPresent()) {
                setClickDecision(decision, checkbox.get(), "Форма: отметить обязательный чекбокс");
                return true;
            }
        }

        if (prerequisitesMet && needsTree) {
            boolean accordionAttemptsExhausted = countRecentFormStepClicks(formHistory, "раскрыть секцию", 10) >= 2;
            if (!hasExpandedGoalAccordion(observation, goal) && !accordionAttemptsExhausted) {
                Optional<InteractiveElement> accordion = findCollapsedAccordionForGoal(observation, goal);
                if (accordion.isPresent()) {
                    String target = normalizeTarget(resolveActionTarget(accordion.get()));
                    if (excludedTargets.isEmpty() || !excludedTargets.contains(target)) {
                        setClickDecision(decision, accordion.get(), "Форма: раскрыть секцию по цели");
                        return true;
                    }
                }
            }

            if (accordionAttemptsExhausted
                    && !hasVisibleTreeCheckboxes(observation)
                    && countRecentActionType(formHistory, "SCROLL_DOWN", 10) < 2) {
                decision.setAction("SCROLL_DOWN");
                decision.setTarget(null);
                decision.setReason("Форма: прокрутка к дереву выбора");
                return true;
            }

            if (!isPrimarySubmitReady(observation, goal) && !isTreeSelectionSatisfied(observation, formHistory)) {
                Optional<InteractiveElement> treeItem = findSelectableTreeItem(observation, goal, excludedTargets);
                if (treeItem.isPresent()) {
                    setClickDecision(decision, treeItem.get(), "Форма: отметить элемент в дереве");
                    return true;
                }
            }
        }

        boolean readyForSubmit = !needsTree || isTreeSelectionSatisfied(observation, formHistory);
        if (readyForSubmit && countRecentPrimaryActionAttempts(formHistory, 10) < 3) {
            Optional<InteractiveElement> primaryButton = findPrimarySubmitButton(observation, goal, false);
            if (primaryButton.isPresent()) {
                String target = normalizeTarget(resolveActionTarget(primaryButton.get()));
                if (excludedTargets.isEmpty()
                        || !excludedTargets.contains(target)
                        || canRetryFailedClick(formHistory, target)) {
                    setClickDecision(decision, primaryButton.get(), "Форма: основное действие");
                    return true;
                }
            }
        }

        if (prerequisitesMet && needsTree && isTreeSelectionSatisfied(observation, formHistory)) {
            Optional<InteractiveElement> disabledPrimary = findPrimarySubmitButton(observation, goal, true)
                    .filter(button -> !Boolean.TRUE.equals(button.getIsEnabled()));
            if (disabledPrimary.isPresent()
                    && countRecentTreeLabelClicks(formHistory, 12) < 1
                    && countRecentTreeCheckboxClicks(formHistory, 12) >= 1) {
                Optional<InteractiveElement> treeLabel = findTreeItemLabel(observation);
                if (treeLabel.isPresent()) {
                    setClickDecision(decision, treeLabel.get(), "Форма: выбор в дереве через подпись");
                    return true;
                }
            }
        }

        if (prerequisitesMet && needsTree && !isPrimarySubmitReady(observation, goal)) {
            if (isTreeSelectionSatisfied(observation, formHistory)
                    && countRecentActionType(formHistory, "SCROLL_DOWN", 10) < 3) {
                decision.setAction("SCROLL_DOWN");
                decision.setTarget(null);
                decision.setReason("Форма: прокрутка к основной кнопке");
                return true;
            }
            if (isTreeSelectionSatisfied(observation, formHistory)
                    && countRecentActionType(formHistory, "SCROLL_DOWN", 10) >= 2) {
                Optional<InteractiveElement> submit = findPrimarySubmitButton(observation, goal, true);
                if (submit.isPresent()) {
                    setClickDecision(decision, submit.get(), "Форма: основное действие");
                    return true;
                }
                buildPrimaryButtonXPath(observation, goal).ifPresent(xpath -> {
                    decision.setAction("CLICK");
                    decision.setTarget(xpath);
                    decision.setReason("Форма: основное действие (xpath)");
                });
                if ("CLICK".equals(decision.getAction())) {
                    return true;
                }
            }
        }

        if (prerequisitesMet && needsTree && isTreeSelectionSatisfied(observation, formHistory)
                && isPrimarySubmitReady(observation, goal)
                && countRecentPrimaryActionAttempts(formHistory, 10) >= 1
                && !hasSuccessfulFormPrimaryAction(formHistory)) {
            buildPrimaryButtonXPath(observation, goal).ifPresent(xpath -> {
                decision.setAction("CLICK");
                decision.setTarget(xpath);
                decision.setReason("Форма: повтор основного действия (xpath)");
            });
            if ("CLICK".equals(decision.getAction())) {
                return true;
            }
        }

        if (!prerequisitesMet) {
            Optional<InteractiveElement> checkbox = findBestGoalCheckbox(observation, goal, excludedTargets);
            if (checkbox.isPresent()) {
                setClickDecision(decision, checkbox.get(), "Форма: отметить чекбокс по цели");
                return true;
            }
        }

        return false;
    }

    private void setClickDecision(Decision decision, InteractiveElement element, String reason) {
        decision.setAction("CLICK");
        decision.setTarget(resolveActionTarget(element));
        decision.setReason(reason);
    }

    private boolean isFormHeavyPage(List<InteractiveElement> elements) {
        if (elements == null) {
            return false;
        }
        long formCheckboxes = elements.stream().filter(this::isCheckboxElement).count();
        boolean hasSubmitButton = elements.stream()
                .filter(element -> "button".equalsIgnoreCase(element.getTagName()))
                .anyMatch(element -> Optional.ofNullable(element.getText()).orElse("").trim().length() >= 4);
        boolean hasTree = elements.stream().anyMatch(this::isTreeViewItem);
        return formCheckboxes >= 2 || (formCheckboxes >= 1 && (hasSubmitButton || hasTree));
    }

    private int formElementRelevanceScore(InteractiveElement element, String goalDescription) {
        int score = goalRelevanceScore(element, goalDescription);
        String tag = Optional.ofNullable(element.getTagName()).orElse("").toLowerCase(Locale.ROOT);
        if ("label".equals(tag)) {
            score += 40;
        }
        if (isCheckboxElement(element) && !isCheckboxChecked(element)) {
            score += 60;
        }
        if ("button".equals(tag)) {
            if (Boolean.TRUE.equals(element.getIsEnabled())) {
                score += 30;
            } else {
                score -= 100;
            }
        }
        return score;
    }

    private boolean isCheckboxElement(InteractiveElement element) {
        if (isKendoTreeCheckbox(element)) {
            return false;
        }
        String tag = Optional.ofNullable(element.getTagName()).orElse("").toLowerCase(Locale.ROOT);
        if ("input".equals(tag) && "checkbox".equalsIgnoreCase(element.getType())) {
            return true;
        }
        Map<String, String> attrs = element.getAttributes();
        if (attrs != null && "checkbox".equals(attrs.get("role"))) {
            return true;
        }
        if ("label".equals(tag)) {
            if (isInputFieldLabel(element)) {
                return false;
            }
            return attrs != null && "true".equals(attrs.get("has-checkbox"));
        }
        return false;
    }

    private boolean isInputFieldLabel(InteractiveElement element) {
        String id = Optional.ofNullable(element.getIdAttr()).orElse("");
        if (id.endsWith("-label") || id.matches("_r_\\d+-label")) {
            return true;
        }
        String text = Optional.ofNullable(element.getText()).orElse("").toLowerCase(Locale.ROOT);
        return text.contains("добавить код");
    }

    private boolean isCheckboxChecked(InteractiveElement element) {
        Map<String, String> attrs = element.getAttributes();
        if (attrs == null) {
            return false;
        }
        return "true".equalsIgnoreCase(attrs.get("checked"))
                || "true".equalsIgnoreCase(attrs.get("aria-checked"));
    }

    private Optional<InteractiveElement> findBestGoalCheckbox(
            AgentObservation observation,
            String goal,
            Set<String> excludedTargets
    ) {
        if (observation.getVisibleElements() == null) {
            return Optional.empty();
        }
        String url = observation.getUrl();
        return observation.getVisibleElements().stream()
                .filter(this::isCheckboxElement)
                .filter(element -> !isCheckboxChecked(element))
                .filter(element -> isCheckboxActionRequired(element, goal))
                .filter(element -> !isLikelySidebarNav(element, url))
                .filter(element -> excludedTargets.isEmpty()
                        || !excludedTargets.contains(normalizeTarget(resolveActionTarget(element))))
                .max(Comparator.comparingInt(element -> formElementRelevanceScore(element, goal)));
    }

    private boolean isExcludedByGoalNegation(InteractiveElement element, String goal) {
        if (goal == null || element == null) {
            return false;
        }
        String text = Optional.ofNullable(element.getText()).orElse("").toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return false;
        }
        for (String term : extractGoalNegationTerms(goal)) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractGoalNegationTerms(String goal) {
        List<String> terms = new ArrayList<>();
        if (goal == null || goal.isBlank()) {
            return terms;
        }
        Matcher includeBlock = Pattern.compile(
                "(?i)не\\s+включать[:\\s]+([^\\n.]+)"
        ).matcher(goal);
        if (includeBlock.find()) {
            for (String part : includeBlock.group(1).split("[,;]")) {
                String term = part.trim().toLowerCase(Locale.ROOT);
                if (term.length() >= 3) {
                    terms.add(term);
                }
            }
        }
        Matcher without = Pattern.compile("(?i)без\\s+([\\p{L}\\d][\\p{L}\\d\\s]{2,40})").matcher(goal);
        while (without.find()) {
            String term = without.group(1).trim().toLowerCase(Locale.ROOT);
            if (term.length() >= 3) {
                terms.add(term);
            }
        }
        return terms;
    }

    private boolean areFormPrerequisitesMet(AgentObservation observation, String goal) {
        if (observation.getVisibleElements() == null) {
            return true;
        }
        return observation.getVisibleElements().stream()
                .filter(this::isCheckboxElement)
                .noneMatch(element -> isCheckboxActionRequired(element, goal));
    }

    private boolean isCheckboxActionRequired(InteractiveElement element, String goal) {
        if (element == null || isCheckboxChecked(element)) {
            return false;
        }
        if (isCheckboxPreservedByGoal(element, goal) || isExcludedByGoalNegation(element, goal)) {
            return false;
        }
        if (goalNeedsTreeSelection(goal) && isFormScopeParameterCheckbox(element)) {
            return false;
        }
        if (goalNeedsTreeSelection(goal)) {
            return isCheckboxExplicitlyEnabledInGoal(element, goal);
        }
        return isCheckboxExplicitlyEnabledInGoal(element, goal)
                || goalRelevanceScore(element, goal) >= 50;
    }

    private boolean isFormScopeParameterCheckbox(InteractiveElement element) {
        if (element == null || !"label".equalsIgnoreCase(element.getTagName())) {
            return false;
        }
        String text = Optional.ofNullable(element.getText()).orElse("").toLowerCase(Locale.ROOT);
        return text.contains("метаданные (схемы")
                || text.contains("данные выбранных")
                || text.contains("storage")
                || text.contains("workflow")
                || text.contains("интеграции");
    }

    private boolean isCheckboxPreservedByGoal(InteractiveElement element, String goal) {
        if (goal == null || element == null) {
            return false;
        }
        String text = Optional.ofNullable(element.getText()).orElse("").toLowerCase(Locale.ROOT);
        Matcher matcher = Pattern.compile(
                "«([^»]+)»[^\\n]{0,60}(?:уже включён|уже включен|не менять)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ).matcher(goal);
        while (matcher.find()) {
            if (checkboxTextMatchesPhrase(text, matcher.group(1))) {
                return true;
            }
        }
        return false;
    }

    private boolean isCheckboxExplicitlyEnabledInGoal(InteractiveElement element, String goal) {
        if (goal == null || element == null) {
            return false;
        }
        String text = Optional.ofNullable(element.getText()).orElse("").toLowerCase(Locale.ROOT);
        Matcher matcher = Pattern.compile(
                "(?i)(?:включ|отмет|enable|check)[^«\\n]{0,40}«([^»]+)»"
        ).matcher(goal);
        while (matcher.find()) {
            if (checkboxTextMatchesPhrase(text, matcher.group(1))) {
                return true;
            }
        }
        return false;
    }

    private boolean checkboxTextMatchesPhrase(String checkboxText, String goalPhrase) {
        String phrase = goalPhrase.trim().toLowerCase(Locale.ROOT);
        if (phrase.isBlank() || checkboxText.isBlank()) {
            return false;
        }
        if (checkboxText.contains(phrase) || phrase.contains(checkboxText)) {
            return true;
        }
        int prefixLen = Math.min(phrase.length(), 14);
        return checkboxText.contains(phrase.substring(0, prefixLen));
    }

    private boolean hasExpandedGoalAccordion(AgentObservation observation, String goal) {
        if (hasVisibleTreeCheckboxes(observation)) {
            return true;
        }
        if (observation.getVisibleElements() == null) {
            return false;
        }
        return observation.getVisibleElements().stream()
                .filter(element -> "button".equalsIgnoreCase(element.getTagName()))
                .filter(element -> {
                    String selector = Optional.ofNullable(element.getSelector()).orElse("")
                            .toLowerCase(Locale.ROOT);
                    return selector.contains("> h3 >") || selector.contains("h3 > button")
                            || selector.contains("accordionsummary");
                })
                .anyMatch(element -> {
                    if (goalRelevanceScore(element, goal) < 30) {
                        return false;
                    }
                    Map<String, String> attrs = element.getAttributes();
                    if (attrs != null && "true".equalsIgnoreCase(attrs.get("aria-expanded"))) {
                        return true;
                    }
                    String selector = Optional.ofNullable(element.getSelector()).orElse("")
                            .toLowerCase(Locale.ROOT);
                    return selector.contains("mui-expanded");
                });
    }

    private int countRecentFormStepClicks(List<AgentAction> history, String reasonFragment, int window) {
        return (int) history.stream()
                .sorted((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()))
                .limit(window)
                .filter(action -> "CLICK".equals(action.getActionType()))
                .filter(action -> Optional.ofNullable(action.getReason()).orElse("").contains(reasonFragment))
                .count();
    }

    private boolean isPrimarySubmitReady(AgentObservation observation, String goal) {
        return findPrimarySubmitButton(observation, goal, false).isPresent();
    }

    private boolean hasSuccessfulFormPrimaryAction(List<AgentAction> history) {
        return history.stream()
                .filter(action -> "CLICK".equals(action.getActionType()))
                .anyMatch(action -> action.getResult() != null
                        && Boolean.TRUE.equals(action.getResult().getSuccess())
                        && Optional.ofNullable(action.getReason()).orElse("").contains("Форма: основное действие"));
    }

    private boolean isPrimarySubmitInProgress(AgentObservation observation, String goal) {
        return findPrimarySubmitButton(observation, goal, true)
                .map(button -> isLoadingButtonText(button.getText()))
                .orElse(false);
    }

    private boolean isLoadingButtonText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT).trim();
        return normalized.contains("…")
                || normalized.endsWith("...")
                || normalized.matches(".*\\b(загруз|отправ|сохран|обработ|выполн|экспорт|импорт|скач).*");
    }

    private boolean canRetryFailedClick(List<AgentAction> history, String target) {
        String normalized = normalizeTarget(target);
        return history.stream()
                .sorted((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()))
                .filter(action -> "CLICK".equals(action.getActionType()))
                .filter(action -> normalized.equals(normalizeTarget(action.getTargetSelector())))
                .findFirst()
                .map(action -> action.getResult() == null || !Boolean.TRUE.equals(action.getResult().getSuccess()))
                .orElse(false);
    }

    private int countRecentPrimaryActionAttempts(List<AgentAction> history, int window) {
        return (int) history.stream()
                .sorted((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()))
                .limit(window)
                .filter(action -> "CLICK".equals(action.getActionType()))
                .filter(action -> Optional.ofNullable(action.getReason()).orElse("").contains("Форма: основное действие"))
                .count();
    }

    private boolean hasVisibleTreeCheckboxes(AgentObservation observation) {
        if (observation.getVisibleElements() == null) {
            return false;
        }
        return observation.getVisibleElements().stream().anyMatch(this::isSelectableTreeItem);
    }

    private boolean hasTreeItemSelected(AgentObservation observation) {
        if (observation.getVisibleElements() == null) {
            return false;
        }
        return observation.getVisibleElements().stream()
                .filter(this::isKendoTreeCheckbox)
                .filter(this::isSelectableTreeItem)
                .anyMatch(this::isCheckboxChecked);
    }

    private boolean isSelectableTreeItem(InteractiveElement element) {
        return isKendoTreeCheckbox(element) && kendoTreeItemIndex(element) >= MIN_TREE_ITEM_INDEX;
    }

    private int kendoTreeItemIndex(InteractiveElement element) {
        String id = Optional.ofNullable(element.getIdAttr()).orElse("");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-item-(\\d+)$").matcher(id);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return Integer.MAX_VALUE;
    }

    private boolean isKendoTreeCheckbox(InteractiveElement element) {
        if (!"input".equalsIgnoreCase(element.getTagName())) {
            return false;
        }
        if (!"checkbox".equalsIgnoreCase(element.getType())) {
            return false;
        }
        String id = Optional.ofNullable(element.getIdAttr()).orElse("");
        return id.contains("-item-");
    }

    private boolean goalNeedsTreeSelection(String goal) {
        if (goal == null) {
            return false;
        }
        String normalized = goal.toLowerCase(Locale.ROOT);
        return normalized.contains("дерев")
                || normalized.contains("treeview")
                || normalized.contains("узл")
                || (normalized.contains("выбра") && (normalized.contains("схем") || normalized.contains("чекбокс")));
    }

    private boolean isTreeViewItem(InteractiveElement element) {
        if (isKendoTreeCheckbox(element)) {
            return true;
        }
        String tag = Optional.ofNullable(element.getTagName()).orElse("").toLowerCase(Locale.ROOT);
        String selector = Optional.ofNullable(element.getSelector()).orElse("").toLowerCase(Locale.ROOT);
        Map<String, String> attrs = element.getAttributes();
        return ("li".equals(tag) && selector.contains("treeview"))
                || selector.contains("k-treeview-item")
                || (attrs != null && "treeitem".equals(attrs.get("role")));
    }

    private Optional<InteractiveElement> findPrimarySubmitButton(
            AgentObservation observation,
            String goal,
            boolean includeDisabled
    ) {
        if (observation.getVisibleElements() == null) {
            return Optional.empty();
        }
        List<InteractiveElement> candidates = observation.getVisibleElements().stream()
                .filter(element -> "button".equalsIgnoreCase(element.getTagName()))
                .filter(element -> !isAuxiliaryFormButton(element))
                .filter(element -> isGoalTerminalActionButton(element, goal))
                .toList();
        Optional<InteractiveElement> enabled = candidates.stream()
                .filter(element -> Boolean.TRUE.equals(element.getIsEnabled()))
                .max(Comparator.comparingInt(element -> goalRelevanceScore(element, goal)));
        if (enabled.isPresent() || !includeDisabled) {
            return enabled;
        }
        return candidates.stream()
                .max(Comparator.comparingInt(element -> goalRelevanceScore(element, goal)));
    }

    private boolean isAuxiliaryFormButton(InteractiveElement element) {
        String text = Optional.ofNullable(element.getText()).orElse("").toLowerCase(Locale.ROOT);
        String selector = Optional.ofNullable(element.getSelector()).orElse("").toLowerCase(Locale.ROOT);
        if (selector.contains("h3 >") || selector.contains("accordionsummary")) {
            return true;
        }
        return text.startsWith("добавить") || text.contains("добавить код");
    }

    private boolean isGoalTerminalActionButton(InteractiveElement element, String goal) {
        if (goal == null || element == null) {
            return false;
        }
        String text = Optional.ofNullable(element.getText()).orElse("").toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return false;
        }
        Matcher quoted = Pattern.compile("«([^»]+)»").matcher(goal);
        while (quoted.find()) {
            String phrase = quoted.group(1).toLowerCase(Locale.ROOT);
            if (phrase.matches(".*(скачать|сохран|отправ|экспорт|импорт|zip|пакет).*")
                    && checkboxTextMatchesPhrase(text, phrase)) {
                return true;
            }
        }
        String goalLower = goal.toLowerCase(Locale.ROOT);
        boolean goalWantsSubmit = goalLower.contains("скачать")
                || goalLower.contains("zip")
                || goalLower.contains("пакет")
                || goalLower.contains("экспорт настроек");
        boolean buttonIsSubmit = text.contains("скачать")
                || text.contains("пакет")
                || text.contains("zip")
                || text.contains("экспорт");
        return goalWantsSubmit && buttonIsSubmit;
    }

    private Optional<String> buildPrimaryButtonXPath(AgentObservation observation, String goal) {
        return findPrimarySubmitButton(observation, goal, true)
                .map(button -> {
                    String selector = Optional.ofNullable(button.getSelector()).orElse("");
                    if (!selector.isBlank() && isSpecificCssSelector(selector)) {
                        return selector;
                    }
                    String text = Optional.ofNullable(button.getText()).orElse("").trim();
                    if (text.length() < 4) {
                        return "";
                    }
                    String fragment = normalizeRepeatedButtonText(text);
                    fragment = fragment.substring(0, Math.min(24, fragment.length())).replace("'", "");
                    return "//button[contains(., '" + fragment + "')]";
                })
                .filter(target -> !target.isBlank());
    }

    private boolean isSpecificCssSelector(String selector) {
        String normalized = selector.toLowerCase(Locale.ROOT);
        return normalized.contains(">")
                || normalized.startsWith("#")
                || normalized.startsWith("input")
                || normalized.startsWith("button.");
    }

    private String normalizeRepeatedButtonText(String text) {
        String trimmed = text.trim();
        if (trimmed.length() < 16) {
            return trimmed;
        }
        for (int len = 12; len <= trimmed.length() / 2; len++) {
            String prefix = trimmed.substring(0, len).trim();
            if (trimmed.startsWith(prefix + " " + prefix)
                    || trimmed.startsWith(prefix + prefix)) {
                return prefix;
            }
        }
        return trimmed;
    }

    private Optional<InteractiveElement> findTreeItemLabel(AgentObservation observation) {
        if (observation.getVisibleElements() == null) {
            return Optional.empty();
        }
        return observation.getVisibleElements().stream()
                .filter(element -> "span".equalsIgnoreCase(element.getTagName()))
                .filter(element -> {
                    String selector = Optional.ofNullable(element.getSelector()).orElse("").toLowerCase(Locale.ROOT);
                    return selector.contains("k-treeview-item");
                })
                .filter(element -> {
                    String text = Optional.ofNullable(element.getText()).orElse("").trim();
                    return text.length() > 3 && !text.matches("\\(\\d+\\)");
                })
                .findFirst();
    }

    private int countRecentTreeLabelClicks(List<AgentAction> history, int window) {
        return (int) history.stream()
                .sorted((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()))
                .limit(window)
                .filter(action -> "CLICK".equals(action.getActionType()))
                .map(action -> Optional.ofNullable(action.getTargetSelector()).orElse("").toLowerCase(Locale.ROOT))
                .filter(target -> target.contains("k-treeview-item"))
                .count();
    }

    private Optional<InteractiveElement> findCollapsedAccordionForGoal(AgentObservation observation, String goal) {
        if (observation.getVisibleElements() == null || hasExpandedGoalAccordion(observation, goal)) {
            return Optional.empty();
        }
        List<InteractiveElement> candidates = observation.getVisibleElements().stream()
                .filter(element -> "button".equalsIgnoreCase(element.getTagName()))
                .filter(element -> {
                    String selector = Optional.ofNullable(element.getSelector()).orElse("").toLowerCase(Locale.ROOT);
                    Map<String, String> attrs = element.getAttributes();
                    boolean expanded = selector.contains("mui-expanded")
                            || (attrs != null && "true".equalsIgnoreCase(attrs.get("aria-expanded")));
                    return (selector.contains("> h3 >") || selector.contains("h3 > button")
                            || selector.contains("accordionsummary"))
                            && !expanded;
                })
                .filter(element -> goalRelevanceScore(element, goal) >= 30)
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (goalNeedsTreeSelection(goal)) {
            Optional<InteractiveElement> treeSection = candidates.stream()
                    .filter(element -> {
                        String text = Optional.ofNullable(element.getText()).orElse("").toLowerCase(Locale.ROOT);
                        return text.contains("модел") || text.contains("metadata")
                                || text.contains("состав пакета");
                    })
                    .max(Comparator.comparingInt(element -> goalRelevanceScore(element, goal)));
            if (treeSection.isPresent()) {
                return treeSection;
            }
        }
        return candidates.stream()
                .max(Comparator.comparingInt(element -> goalRelevanceScore(element, goal)));
    }

    private Optional<InteractiveElement> findSelectableTreeItem(
            AgentObservation observation,
            String goal,
            Set<String> excludedTargets
    ) {
        if (observation.getVisibleElements() == null) {
            return Optional.empty();
        }
        boolean accordionReady = hasExpandedGoalAccordion(observation, goal)
                || countRecentFormStepClicks(
                observation.getPreviousActions() != null ? observation.getPreviousActions() : List.of(),
                "раскрыть секцию",
                10
        ) >= 1;
        if (!accordionReady && !hasVisibleTreeCheckboxes(observation)) {
            return Optional.empty();
        }
        List<InteractiveElement> treeCheckboxes = observation.getVisibleElements().stream()
                .filter(this::isSelectableTreeItem)
                .filter(element -> excludedTargets.isEmpty()
                        || !excludedTargets.contains(normalizeTarget(resolveActionTarget(element))))
                .sorted(Comparator.comparingInt(this::kendoTreeItemIndex))
                .toList();
        return treeCheckboxes.stream()
                .filter(element -> !isCheckboxChecked(element))
                .min(Comparator.comparingInt(this::kendoTreeItemIndex));
    }

    private boolean isTreeSelectionSatisfied(AgentObservation observation, List<AgentAction> history) {
        return hasTreeItemSelected(observation) || countRecentTreeCheckboxClicks(history, 15) >= 1;
    }

    private int countRecentTreeCheckboxClicks(List<AgentAction> history, int window) {
        return (int) history.stream()
                .sorted((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()))
                .limit(window)
                .filter(action -> "CLICK".equals(action.getActionType()))
                .map(action -> Optional.ofNullable(action.getTargetSelector()).orElse(""))
                .filter(target -> target.contains("-item-"))
                .count();
    }

    private Optional<InteractiveElement> findEnabledGoalButton(AgentObservation observation, String goal) {
        return findPrimarySubmitButton(observation, goal, false);
    }

    private Optional<InteractiveElement> findAccordionButtonForGoal(AgentObservation observation, String goal) {
        if (observation.getVisibleElements() == null) {
            return Optional.empty();
        }
        return observation.getVisibleElements().stream()
                .filter(element -> "button".equalsIgnoreCase(element.getTagName()))
                .filter(element -> {
                    String selector = Optional.ofNullable(element.getSelector()).orElse("");
                    return selector.contains("> h3 >") || selector.contains("h3 > button");
                })
                .filter(element -> goalRelevanceScore(element, goal) >= 30)
                .findFirst();
    }

    private boolean isContentHubPage(List<InteractiveElement> elements, String url) {
        if (elements == null || elements.isEmpty()) {
            return false;
        }
        long contentLinks = elements.stream()
                .filter(element -> "a".equalsIgnoreCase(element.getTagName()))
                .filter(element -> !hrefFromElement(element).isBlank())
                .filter(element -> !isLikelySidebarNav(element, url))
                .count();
        return contentLinks >= 3;
    }

    private boolean isCurrentPageLink(InteractiveElement element, String url) {
        String href = normalizeUrlPath(hrefFromElement(element));
        String current = normalizeUrlPath(url);
        if (href.isBlank() || current.isBlank()) {
            return false;
        }
        return href.equals(current);
    }

    private List<String> filterPendingNavigationPhrases(
            List<String> phrases,
            String url,
            List<InteractiveElement> elements
    ) {
        if (phrases == null || phrases.isEmpty() || elements == null || elements.isEmpty()) {
            return phrases != null ? phrases : List.of();
        }
        String current = normalizeUrlPath(url);
        if (current.isBlank() || current.equals("/app") || current.endsWith("/app")) {
            return phrases;
        }

        List<String> pending = new ArrayList<>();
        for (String phrase : phrases) {
            List<InteractiveElement> matches = elements.stream()
                    .filter(element -> matchesAnyGoalPhrase(element, List.of(phrase)))
                    .toList();
            if (matches.isEmpty()) {
                pending.add(phrase);
                continue;
            }
            boolean hasContentTarget = matches.stream()
                    .filter(element -> !isLikelySidebarNav(element, url))
                    .filter(element -> !isCurrentPageLink(element, url))
                    .anyMatch(this::isPreciseNavigationTarget);
            boolean onlySidebarOrCurrent = matches.stream()
                    .allMatch(element -> isLikelySidebarNav(element, url) || isCurrentPageLink(element, url));
            if (hasContentTarget || !onlySidebarOrCurrent) {
                pending.add(phrase);
            }
        }
        return pending.isEmpty() ? phrases : pending;
    }

    private Set<String> collectRecentClickTargets(List<AgentAction> history, int window) {
        return history.stream()
                .sorted((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()))
                .limit(window)
                .filter(action -> "CLICK".equals(action.getActionType()))
                .map(action -> normalizeTarget(action.getTargetSelector()))
                .filter(target -> !target.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isLikelySidebarNav(InteractiveElement element, String currentUrl) {
        String selector = Optional.ofNullable(element.getSelector()).orElse("");
        if (selector.contains("MuiDrawer-root") || selector.contains("MuiCollapse-root")) {
            return true;
        }
        String text = Optional.ofNullable(element.getText()).orElse("");
        if (text.contains("\n") && text.split("\\R").length >= 3) {
            return true;
        }
        if (isCurrentPageLink(element, currentUrl)) {
            return true;
        }
        String href = hrefFromElement(element);
        return !href.isBlank() && currentUrl != null && isParentPath(href, currentUrl);
    }

    private boolean isLikelySidebarHref(String target, String currentUrl) {
        return target.startsWith("a[href") && isParentPath(extractHrefFromSelector(target), currentUrl);
    }

    private String extractHrefFromSelector(String selector) {
        int start = selector.indexOf('\'');
        int end = selector.lastIndexOf('\'');
        if (start >= 0 && end > start) {
            return selector.substring(start + 1, end);
        }
        return selector;
    }

    private String hrefFromElement(InteractiveElement element) {
        Map<String, String> attrs = element.getAttributes();
        return attrs != null ? attrs.getOrDefault("href", "") : "";
    }

    private boolean isNavigatingToParentPage(String currentUrl, String target) {
        return isParentPath(target, currentUrl);
    }

    private boolean isParentPath(String candidatePath, String currentUrl) {
        String parent = normalizeUrlPath(candidatePath);
        String current = normalizeUrlPath(currentUrl);
        if (parent.isBlank() || current.isBlank() || parent.equals(current)) {
            return false;
        }
        return current.startsWith(parent) && current.length() > parent.length()
                && current.charAt(parent.length()) == '/';
    }

    private String normalizeUrlPath(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String path = url.trim();
        if (path.startsWith("http")) {
            int slash = path.indexOf('/', path.indexOf("://") + 3);
            path = slash >= 0 ? path.substring(slash) : "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private int countRecentFailedClicks(List<AgentAction> actions, int window) {
        return (int) actions.stream()
                .sorted((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()))
                .limit(window)
                .filter(action -> "CLICK".equals(action.getActionType()))
                .filter(action -> action.getResult() != null && Boolean.FALSE.equals(action.getResult().getSuccess()))
                .count();
    }

    private int countRecentLabelClicks(List<AgentAction> actions, int window) {
        return (int) actions.stream()
                .sorted((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()))
                .limit(window)
                .filter(action -> "CLICK".equals(action.getActionType()))
                .filter(action -> {
                    String selector = Optional.ofNullable(action.getTargetSelector()).orElse("").toLowerCase(Locale.ROOT);
                    return selector.contains("label")
                            && !selector.contains("#_r_")
                            && !selector.contains("-label");
                })
                .count();
    }

    private boolean historyContainsAction(List<AgentAction> history, String actionType) {
        return history.stream().anyMatch(action -> actionType.equals(action.getActionType()));
    }

    private int countRecentActionType(List<AgentAction> actions, String actionType, int window) {
        return (int) actions.stream()
                .sorted((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()))
                .limit(window)
                .filter(action -> actionType.equals(action.getActionType()))
                .count();
    }

    private boolean isSidebarNavigationClick(Decision decision, AgentObservation observation) {
        if (!"CLICK".equals(decision.getAction())) {
            return false;
        }
        String target = Optional.ofNullable(decision.getTarget()).orElse("");
        String url = Optional.ofNullable(observation.getUrl()).orElse("");

        if (target.startsWith("a[") && isLikelySidebarHref(target, url)) {
            return true;
        }

        Optional<InteractiveElement> matched = findElementBySelector(observation, target);
        return matched.isPresent() && isLikelySidebarNav(matched.get(), url);
    }

    private int navigationLinkScore(InteractiveElement element, AgentObservation observation, Decision decision) {
        int score = goalRelevanceScore(element, observation.getGoalDescription());
        String href = hrefFromElement(element);
        String url = Optional.ofNullable(observation.getUrl()).orElse("");
        if (!href.isBlank() && isParentPath(href, url)) {
            score -= 200;
        }
        return score;
    }

    private Optional<InteractiveElement> findBestGoalLink(AgentObservation observation, List<String> goalPhrases) {
        return findBestGoalNavigationTarget(observation, goalPhrases)
                .filter(element -> "a".equalsIgnoreCase(element.getTagName())
                        || "button".equalsIgnoreCase(element.getTagName()));
    }

    private Optional<InteractiveElement> findLabelForFormClick(Decision decision, AgentObservation observation) {
        if (observation.getVisibleElements() == null) {
            return Optional.empty();
        }
        List<String> keywords = filterPhrasesByReason(
                extractGoalPhrases(observation.getGoalDescription()),
                decision.getReason());
        if (keywords.isEmpty()) {
            keywords = tokenizeReason(decision.getReason());
        }
        final List<String> matchTokens = keywords;
        return observation.getVisibleElements().stream()
                .filter(this::isCheckboxElement)
                .filter(element -> labelMatchesTokens(element, matchTokens))
                .max(Comparator.comparingInt(element -> formElementRelevanceScore(element, observation.getGoalDescription())));
    }

    private List<String> tokenizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return List.of();
        }
        return Arrays.stream(reason.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}]+"))
                .filter(token -> token.length() >= 4)
                .distinct()
                .limit(8)
                .collect(Collectors.toList());
    }

    private boolean labelMatchesTokens(InteractiveElement element, List<String> tokens) {
        if (tokens.isEmpty()) {
            return true;
        }
        String text = Optional.ofNullable(element.getText()).orElse("").toLowerCase(Locale.ROOT);
        return tokens.stream().anyMatch(text::contains);
    }

    private List<String> filterPhrasesByReason(List<String> goalPhrases, String reason) {
        if (reason == null || reason.isBlank()) {
            return goalPhrases;
        }
        String reasonLower = reason.toLowerCase(Locale.ROOT);
        List<String> filtered = goalPhrases.stream()
                .filter(phrase -> reasonLower.contains(phrase.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        return filtered.isEmpty() ? goalPhrases : filtered;
    }

    private Optional<InteractiveElement> findElementBySelector(AgentObservation observation, String selector) {
        if (observation.getVisibleElements() == null || selector == null) {
            return Optional.empty();
        }
        return observation.getVisibleElements().stream()
                .filter(element -> selector.equals(element.getSelector()))
                .findFirst();
    }

    private String normalizeTarget(String target) {
        return Optional.ofNullable(target).orElse("").trim().toLowerCase(Locale.ROOT);
    }

    private int countRecentMatching(List<AgentAction> actions, String signature, int window) {
        return (int) actions.stream()
                .sorted((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()))
                .limit(window)
                .filter(action -> signature.equals(action.getActionType() + "|" + normalizeTarget(action.getTargetSelector())))
                .count();
    }

    private boolean detectNavigationPingPong(List<AgentAction> actions, int window) {
        List<String> recentTargets = actions.stream()
                .sorted((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()))
                .limit(window)
                .filter(action -> "CLICK".equals(action.getActionType()) || "NAVIGATE_TO".equals(action.getActionType()))
                .map(this::normalizeNavigationTarget)
                .filter(target -> !target.isBlank())
                .collect(Collectors.toList());
        if (recentTargets.size() < 4) {
            return false;
        }
        Set<String> unique = new LinkedHashSet<>(recentTargets);
        return unique.size() == 2;
    }

    private String normalizeNavigationTarget(AgentAction action) {
        String selector = Optional.ofNullable(action.getTargetSelector()).orElse("");
        if ("NAVIGATE_TO".equals(action.getActionType())) {
            return normalizeUrlPath(selector);
        }
        if (selector.startsWith("a[href")) {
            return normalizeUrlPath(extractHrefFromSelector(selector));
        }
        return selector.toLowerCase(Locale.ROOT);
    }

    private List<String> extractGoalPhrases(String goalDescription) {
        if (goalDescription == null || goalDescription.isBlank()) {
            return List.of();
        }
        List<String> phrases = new ArrayList<>();
        Matcher quoted = GOAL_PHRASE_PATTERN.matcher(goalDescription);
        while (quoted.find()) {
            String phrase = quoted.group(1).trim().toLowerCase(Locale.ROOT);
            if (phrase.length() < 2) {
                continue;
            }
            // Credentials in quoted examples ("admin") are usually auth data, not nav targets.
            if (phrase.matches("[a-z0-9_\\-]{2,}")) {
                continue;
            }
            phrases.add(phrase);
        }
        return phrases;
    }

    private boolean matchesAnyGoalPhrase(InteractiveElement element, List<String> goalPhrases) {
        String text = Optional.ofNullable(element.getText()).orElse("");
        String href = hrefFromElement(element).toLowerCase(Locale.ROOT);
        List<String> lines = text.contains("\n")
                ? Arrays.stream(text.split("\\R")).map(String::trim).filter(line -> line.length() >= 2).toList()
                : List.of(text.trim());

        for (String phrase : goalPhrases) {
            String phraseLower = phrase.toLowerCase(Locale.ROOT);
            for (String line : lines) {
                String lineLower = line.toLowerCase(Locale.ROOT);
                if (lineLower.length() < 2) {
                    continue;
                }
                if (lineLower.equals(phraseLower)
                        || lineLower.contains(phraseLower)
                        || phraseLower.contains(lineLower)
                        || phraseTokensMatch(lineLower, phraseLower)) {
                    return true;
                }
            }
            String normalizedPhrase = phrase.replace(' ', '-').toLowerCase(Locale.ROOT);
            if (!href.isBlank()
                    && (href.contains(normalizedPhrase) || href.contains(phrase.replace(" ", "").toLowerCase(Locale.ROOT)))) {
                return true;
            }
        }
        return false;
    }

    private boolean phraseTokensMatch(String text, String phrase) {
        String[] phraseTokens = phrase.split("\\s+");
        if (phraseTokens.length < 2) {
            return false;
        }
        int matched = 0;
        for (String token : phraseTokens) {
            if (token.length() >= 3 && text.contains(token)) {
                matched++;
            }
        }
        return matched >= Math.max(2, (int) Math.ceil(phraseTokens.length * 0.75));
    }

    private int actionTagPriority(String tagName) {
        if (tagName == null) {
            return 0;
        }
        return switch (tagName.toLowerCase(Locale.ROOT)) {
            case "a", "button" -> 3;
            case "label", "input", "textarea", "select" -> 2;
            default -> 1;
        };
    }

    private String formatElements(List<InteractiveElement> elements, int limit) {
        if (elements == null || elements.isEmpty()) {
            return "Нет элементов";
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;

        for (InteractiveElement element : elements) {
            if (count >= limit) {
                sb.append("... и еще ").append(elements.size() - limit).append(" элементов\n");
                break;
            }

            sb.append(count + 1).append(". ");
            sb.append("Тип: ").append(element.getTagName());

            if (element.getText() != null && !element.getText().isEmpty()) {
                String text = element.getText();
                if (text.length() > 50) text = text.substring(0, 47) + "...";
                sb.append(", Текст: '").append(text).append("'");
            }

            if (element.getIdAttr() != null && !element.getIdAttr().isEmpty()) {
                sb.append(", ID: #").append(element.getIdAttr());
            }

            if (element.getName() != null && !element.getName().isEmpty()) {
                sb.append(", Name: ").append(element.getName());
            }

            if (element.getType() != null && !element.getType().isEmpty()) {
                sb.append(", Input type: ").append(element.getType());
            }

            if (element.getPlaceholder() != null && !element.getPlaceholder().isEmpty()) {
                sb.append(", Placeholder: '").append(element.getPlaceholder()).append("'");
            }

            if (element.getSelector() != null && !element.getSelector().isEmpty()) {
                sb.append(", Селектор: ").append(element.getSelector());
            }

            if (Boolean.FALSE.equals(element.getIsEnabled())) {
                sb.append(", Включён: нет (disabled)");
            } else if (Boolean.TRUE.equals(element.getIsEnabled()) && "button".equalsIgnoreCase(element.getTagName())) {
                sb.append(", Включён: да");
            }

            if (element.getAttributes() != null) {
                String href = element.getAttributes().get("href");
                if (href != null && !href.isBlank()) {
                    sb.append(", Href: ").append(href);
                }
                String checked = element.getAttributes().get("checked");
                String ariaChecked = element.getAttributes().get("aria-checked");
                if ("true".equalsIgnoreCase(checked) || "true".equalsIgnoreCase(ariaChecked)) {
                    sb.append(", Отмечен: да");
                } else if ("false".equalsIgnoreCase(checked) || "false".equalsIgnoreCase(ariaChecked)) {
                    sb.append(", Отмечен: нет");
                }
            }

            sb.append("\n");
            count++;
        }

        return sb.toString();
    }

    private String formatActionHistory(List<AgentAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return "Нет предыдущих действий";
        }

        StringBuilder sb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        // Берем последние 10 действий
        List<AgentAction> recentActions = actions.stream()
                .sorted((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()))
                .limit(10)
                .collect(Collectors.toList());

        Collections.reverse(recentActions); // Чтобы были в хронологическом порядке

        for (int i = 0; i < recentActions.size(); i++) {
            AgentAction action = recentActions.get(i);
            sb.append(i + 1).append(". ");
            sb.append(formatter.format(action.getTimestamp())).append(" - ");
            sb.append(action.getActionType());

            if (action.getTargetSelector() != null && !action.getTargetSelector().isEmpty()) {
                sb.append(" на '").append(truncate(action.getTargetSelector(), 40)).append("'");
            }

            if (action.getInputValue() != null && !action.getInputValue().isEmpty()) {
                sb.append(" значение: '").append(truncate(action.getInputValue(), 30)).append("'");
            }

            if (action.getResult() != null && action.getResult().getSuccess() != null) {
                sb.append(" [").append(action.getResult().getSuccess() ? "УСПЕХ" : "ОШИБКА").append("]");

                if (!action.getResult().getSuccess() && action.getResult().getMessage() != null) {
                    sb.append(": ").append(truncate(action.getResult().getMessage(), 50));
                }
            }

            sb.append("\n");
        }

        // Статистика
        long successCount = actions.stream()
                .filter(a -> a.getResult() != null && Boolean.TRUE.equals(a.getResult().getSuccess()))
                .count();

        sb.append("\nСтатистика: ").append(successCount).append("/").append(actions.size())
                .append(" успешных действий (").append(actions.size() > 0 ?
                        String.format("%.1f", (successCount * 100.0 / actions.size())) : "0")
                .append("%)");

        Map<String, Long> repeatCounts = recentActions.stream()
                .collect(Collectors.groupingBy(
                        action -> action.getActionType() + "|" + truncate(
                                Optional.ofNullable(action.getTargetSelector()).orElse(""), 40),
                        Collectors.counting()));
        repeatCounts.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2)
                .forEach(entry -> sb.append("\n⚠ ПОВТОР: ")
                        .append(entry.getKey())
                        .append(" — ")
                        .append(entry.getValue())
                        .append(" раз; выбери ДРУГОЕ действие"));

        return sb.toString();
    }

    private String formatIssues(List<DiscoveredIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return "Проблем не обнаружено";
        }

        StringBuilder sb = new StringBuilder();

        // Группируем по типу и серьезности
        Map<IssueSeverity, Long> severityCount = issues.stream()
                .collect(Collectors.groupingBy(DiscoveredIssue::getSeverity, Collectors.counting()));

        Map<IssueType, Long> typeCount = issues.stream()
                .collect(Collectors.groupingBy(DiscoveredIssue::getType, Collectors.counting()));

        sb.append("Всего проблем: ").append(issues.size()).append("\n");

        if (!severityCount.isEmpty()) {
            sb.append("По серьезности:\n");
            severityCount.entrySet().stream()
                    .sorted(Map.Entry.<IssueSeverity, Long>comparingByKey().reversed())
                    .forEach(entry -> {
                        sb.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                    });
        }

        if (!typeCount.isEmpty()) {
            sb.append("По типу:\n");
            typeCount.entrySet().stream()
                    .sorted(Map.Entry.<IssueType, Long>comparingByValue().reversed())
                    .forEach(entry -> {
                        sb.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                    });
        }

        // Последние 3 проблемы подробно
        sb.append("\nПоследние обнаруженные проблемы:\n");
        issues.stream()
                .sorted((i1, i2) -> i2.getDiscoveredAt().compareTo(i1.getDiscoveredAt()))
                .limit(3)
                .forEach(issue -> {
                    sb.append("• ").append(issue.getTitle()).append(" [").append(issue.getSeverity()).append("]\n");
                    if (issue.getDescription() != null && issue.getDescription().length() > 100) {
                        sb.append("  ").append(issue.getDescription().substring(0, 97)).append("...\n");
                    }
                });

        return sb.toString();
    }

    private Decision parseDecision(String aiResponse, AgentObservation observation) {
        try {
            String jsonResponse = extractJsonFromResponse(aiResponse);
            Decision decision = objectMapper.readValue(sanitizeJsonForParsing(jsonResponse), Decision.class);
            validateDecision(decision);
            return decision;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse AI response as JSON: {}", aiResponse, e);
            Decision lenient = parseDecisionLenient(aiResponse);
            if (lenient != null) {
                try {
                    validateDecision(lenient);
                    log.info("Recovered decision from malformed JSON: action={}, target={}",
                            lenient.getAction(), truncate(lenient.getTarget(), 60));
                    return lenient;
                } catch (Exception validationError) {
                    log.warn("Lenient decision failed validation: {}", validationError.getMessage());
                }
            }
            return getDefaultDecision(observation);
        } catch (Exception e) {
            log.error("Error parsing decision", e);
            return getDefaultDecision(observation);
        }
    }

    private String sanitizeJsonForParsing(String json) {
        if (json == null) {
            return "{}";
        }
        return json.replace('\u2019', '\'')
                .replace('\u2018', '\'');
    }

    private Decision parseDecisionLenient(String aiResponse) {
        String json = sanitizeJsonForParsing(extractJsonFromResponse(aiResponse));
        String action = extractJsonStringField(json, "action");
        if (action == null || action.isBlank()) {
            return null;
        }
        Decision decision = new Decision();
        decision.setAction(action.toUpperCase(Locale.ROOT).trim());
        decision.setTarget(extractJsonStringField(json, "target"));
        decision.setValue(extractJsonStringField(json, "value"));
        decision.setReason(extractJsonStringField(json, "reason"));
        String expectedOutcome = extractJsonStringField(json, "expectedOutcome");
        if (expectedOutcome != null && !expectedOutcome.isBlank()) {
            decision.setExpectedOutcome(expectedOutcome);
        }
        return decision;
    }

    private String extractJsonStringField(String json, String fieldName) {
        Matcher matcher = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        ).matcher(json);
        if (!matcher.find()) {
            return null;
        }
        String raw = matcher.group(1);
        return raw.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    private String extractJsonFromResponse(String response) {
        // Ищем JSON в ответе (мог быть обрамлен текстом)
        int jsonStart = response.indexOf('{');
        int jsonEnd = response.lastIndexOf('}');

        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd + 1);
        }

        // Если JSON не найден, пробуем очистить ответ
        String cleaned = response.replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

        // Если все еще не JSON, возвращаем как есть
        return cleaned.startsWith("{") ? cleaned : "{\"action\":\"REFRESH\",\"reason\":\"Не удалось распарсить ответ AI\"}";
    }

    private void validateDecision(Decision decision) {
        if (decision.getAction() == null || decision.getAction().isEmpty()) {
            throw new IllegalArgumentException("Action cannot be null or empty");
        }

        // Нормализуем действие
        decision.setAction(decision.getAction().toUpperCase().trim());

        // Проверяем поддерживаемые действия
        List<String> validActions = Arrays.asList(
                "CLICK", "TYPE", "NAVIGATE_BACK", "NAVIGATE_FORWARD",
                "NAVIGATE_TO", "ASSERT_PRESENCE", "ASSERT_TEXT",
                "SCROLL_UP", "SCROLL_DOWN", "REFRESH", "EXPLORE_MENU",
                "EXPLORE_FORMS", "TEST_VALIDATION", "REPORT_ISSUE", "COMPLETE"
        );

        if (!validActions.contains(decision.getAction())) {
            log.warn("Unknown action: {}, defaulting to REFRESH", decision.getAction());
            decision.setAction("REFRESH");
            decision.setReason("Неизвестное действие, обновляю страницу");
        }

        // Для определенных действий требуются target
        List<String> actionsRequiringTarget = Arrays.asList(
                "CLICK", "TYPE", "NAVIGATE_TO", "ASSERT_PRESENCE",
                "ASSERT_TEXT"
        );

        if (actionsRequiringTarget.contains(decision.getAction()) &&
                (decision.getTarget() == null || decision.getTarget().isEmpty())) {
            throw new IllegalArgumentException("Action " + decision.getAction() + " requires target");
        }

        // Для TYPE требуется value
        if ("TYPE".equals(decision.getAction()) &&
                (decision.getValue() == null || decision.getValue().isEmpty())) {
            decision.setValue("test"); // Значение по умолчанию для теста
        }

        // Если reason отсутствует, создаем дефолтный
        if (decision.getReason() == null || decision.getReason().isEmpty()) {
            decision.setReason(generateDefaultReason(decision));
        }
    }

    private String generateDefaultReason(Decision decision) {
        return switch (decision.getAction()) {
            case "CLICK" -> "Кликаю на элемент для проверки его функциональности";
            case "TYPE" -> "Ввожу тестовые данные в поле для проверки валидации";
            case "REFRESH" -> "Обновляю страницу для получения актуального состояния";
            case "SCROLL_DOWN" -> "Прокручиваю страницу для поиска новых элементов";
            case "EXPLORE_FORMS" -> "Исследую формы на странице для тестирования";
            case "COMPLETE" -> "Завершаю тестирование, так как достигнуты цели";
            default -> "Выполняю действие для продолжения тестирования";
        };
    }

    private Decision getDefaultDecision(AgentObservation observation) {
        if (observation != null && isFormHeavyPage(observation.getVisibleElements())) {
            Decision formDecision = new Decision();
            if (tryAdvanceFormWorkflow(formDecision, observation, observation.getGoalDescription(), Set.of())) {
                formDecision.setExpectedOutcome("Выполняется шаг формы экспорта");
                return formDecision;
            }
        }
        if (observation != null) {
            List<String> navPhrases = filterPendingNavigationPhrases(
                    extractGoalPhrases(observation.getGoalDescription()),
                    Optional.ofNullable(observation.getUrl()).orElse(""),
                    observation.getVisibleElements()
            );
            Optional<InteractiveElement> navTarget = findBestGoalNavigationTarget(observation, navPhrases);
            if (navTarget.isPresent()) {
                Decision decision = new Decision();
                setClickDecision(decision, navTarget.get(), "Восстановление после ошибки парсинга: переход по цели");
                decision.setExpectedOutcome("Откроется следующий раздел из цели тестирования");
                return decision;
            }
            Optional<String> visibleHref = findBestVisibleHref(observation, navPhrases);
            if (visibleHref.isPresent()) {
                Decision decision = new Decision();
                decision.setAction("NAVIGATE_TO");
                decision.setTarget(visibleHref.get());
                decision.setReason("Восстановление после ошибки парсинга: переход по видимой ссылке");
                decision.setExpectedOutcome("Откроется следующий раздел из цели тестирования");
                return decision;
            }
        }
        Decision decision = new Decision();
        decision.setAction("REFRESH");
        decision.setReason("Ошибка парсинга, обновляю страницу");
        decision.setExpectedOutcome("Страница будет перезагружена");
        return decision;
    }

    private TestAgentAction getFallbackAction(AgentObservation observation) {
        if (observation.getVisibleElements() == null || observation.getVisibleElements().isEmpty()) {
            Decision decision = new Decision();
            decision.setAction("REFRESH");
            decision.setReason("Нет видимых элементов, обновляю страницу");
            return actionFactory.create(toDomainDecision(decision));
        }

        List<InteractiveElement> elements = observation.getVisibleElements();
        Optional<String[]> credentials = parseCredentials(observation.getGoalDescription());

        Optional<InteractiveElement> usernameField = findInputField(elements, "username");
        if (usernameField.isPresent() && credentials.isPresent() && isFieldEmpty(usernameField.get())) {
            return buildTypeAction(usernameField.get(), credentials.get()[0], "Fallback: ввожу логин");
        }

        Optional<InteractiveElement> passwordField = findInputField(elements, "password");
        if (passwordField.isPresent() && credentials.isPresent() && isFieldEmpty(passwordField.get())) {
            return buildTypeAction(passwordField.get(), credentials.get()[1], "Fallback: ввожу пароль");
        }

        Optional<InteractiveElement> submitButton = elements.stream()
                .filter(el -> "button".equals(el.getTagName()))
                .filter(el -> {
                    String text = el.getText() != null ? el.getText().toLowerCase() : "";
                    return text.contains("войти") || text.contains("login") || text.contains("sign in");
                })
                .findFirst();
        if (submitButton.isPresent()) {
            return buildClickAction(submitButton.get(), "Fallback: нажимаю кнопку входа");
        }

        List<String> goalPhrases = extractGoalPhrases(observation.getGoalDescription());
        Optional<InteractiveElement> goalLink = elements.stream()
                .filter(el -> "a".equals(el.getTagName()))
                .filter(el -> matchesAnyGoalPhrase(el, goalPhrases))
                .max(Comparator.comparingInt(el -> goalRelevanceScore(el, observation.getGoalDescription())));
        if (goalLink.isPresent()) {
            return buildClickAction(goalLink.get(), "Fallback: перехожу по ссылке из цели тестирования");
        }

        Optional<InteractiveElement> clickableElement = elements.stream()
                .filter(el -> "a".equals(el.getTagName()) || "button".equals(el.getTagName()))
                .filter(el -> el.getText() == null || !el.getText().toLowerCase().contains("toggle"))
                .findFirst();
        if (clickableElement.isPresent()) {
            return buildClickAction(clickableElement.get(), "Fallback: кликаю на первый доступный элемент");
        }

        Decision decision = new Decision();
        decision.setAction("SCROLL_DOWN");
        decision.setReason("Fallback: прокручиваю страницу для поиска элементов");
        return actionFactory.create(toDomainDecision(decision));
    }

    private Optional<String[]> parseCredentials(String goal) {
        if (goal == null) {
            return Optional.empty();
        }
        Matcher matcher = Pattern.compile("(\\S+)\\|(\\S+)").matcher(goal);
        if (matcher.find()) {
            return Optional.of(new String[]{matcher.group(1), matcher.group(2)});
        }
        return Optional.empty();
    }

    private Optional<InteractiveElement> findInputField(List<InteractiveElement> elements, String fieldHint) {
        return elements.stream()
                .filter(el -> "input".equals(el.getTagName()) || "textarea".equals(el.getTagName()))
                .filter(el -> matchesFieldHint(el, fieldHint))
                .findFirst();
    }

    private boolean matchesFieldHint(InteractiveElement el, String fieldHint) {
        String id = el.getIdAttr() != null ? el.getIdAttr().toLowerCase() : "";
        String name = el.getName() != null ? el.getName().toLowerCase() : "";
        String type = el.getType() != null ? el.getType().toLowerCase() : "";
        String placeholder = el.getPlaceholder() != null ? el.getPlaceholder().toLowerCase() : "";
        if ("password".equals(fieldHint)) {
            return id.contains("password") || name.contains("password") || "password".equals(type);
        }
        return id.contains(fieldHint) || name.contains(fieldHint) || placeholder.contains(fieldHint)
                || ("username".equals(fieldHint) && ("text".equals(type) || "email".equals(type)));
    }

    private boolean isFieldEmpty(InteractiveElement element) {
        Map<String, String> attrs = element.getAttributes();
        if (attrs == null) {
            return true;
        }
        String value = attrs.get("value");
        return value == null || value.isBlank();
    }

    private TestAgentAction buildTypeAction(InteractiveElement element, String value, String reason) {
        Decision decision = new Decision();
        decision.setAction("TYPE");
        decision.setTarget(resolveActionTarget(element));
        decision.setValue(value);
        decision.setReason(reason);
        return actionFactory.create(toDomainDecision(decision));
    }

    private TestAgentAction buildClickAction(InteractiveElement element, String reason) {
        Decision decision = new Decision();
        decision.setAction("CLICK");
        decision.setTarget(resolveActionTarget(element));
        decision.setReason(reason);
        return actionFactory.create(toDomainDecision(decision));
    }

    /** Короткий стабильный селектор предпочтительнее длинных MUI class chains. */
    private String resolveActionTarget(InteractiveElement element) {
        if ("label".equalsIgnoreCase(element.getTagName()) && element.getSelector() != null) {
            return element.getSelector();
        }
        Map<String, String> attrs = element.getAttributes();
        if (attrs != null) {
            String href = attrs.get("href");
            if (href != null && !href.isBlank() && !href.startsWith("#")) {
                String normalizedHref = normalizeUrlPath(href);
                if (!normalizedHref.isBlank()) {
                    return "a[href='" + normalizedHref + "']";
                }
                if (href.startsWith("/")) {
                    return "a[href='" + href + "']";
                }
                return "a[href*='" + href + "']";
            }
        }
        if (element.getIdAttr() != null && !element.getIdAttr().isBlank()) {
            return "#" + element.getIdAttr();
        }
        if (element.getName() != null && !element.getName().isBlank()) {
            return element.getTagName() + "[name=\"" + element.getName() + "\"]";
        }
        if ("button".equalsIgnoreCase(element.getTagName())) {
            String text = element.getText() != null ? element.getText().toLowerCase(Locale.ROOT) : "";
            if (text.contains("войти") || text.contains("login") || text.contains("sign in")) {
                return "form#kc-form-login button[type='submit']";
            }
            String selector = element.getSelector();
            if (selector != null && !selector.isBlank() && isSpecificCssSelector(selector)) {
                return selector;
            }
            String buttonText = Optional.ofNullable(element.getText()).orElse("").trim();
            if (buttonText.length() >= 4) {
                String snippet = normalizeRepeatedButtonText(buttonText);
                snippet = snippet.length() > 40 ? snippet.substring(0, 40) : snippet;
                snippet = snippet.replace("\"", "'");
                return "//button[contains(., \"" + snippet + "\")]";
            }
        }
        String selector = element.getSelector();
        if (selector != null && !selector.isBlank()) {
            return selector;
        }
        return element.getXpath() != null ? element.getXpath() : "";
    }

    /**
     * Конвертирует внутренний Decision в domain Decision
     */
    private ru.sbrf.uddk.ai.testing.domain.model.Decision toDomainDecision(Decision decision) {
        return ru.sbrf.uddk.ai.testing.domain.model.Decision.builder()
            .action(decision.getAction())
            .target(decision.getTarget())
            .value(decision.getValue())
            .reason(decision.getReason())
            .expectedOutcome(decision.getExpectedOutcome())
            .confidence(0.9) // По умолчанию высокая уверенность
            .build();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    @Data
    public static class Decision {
        private String action;
        private String target;
        private String value;
        private String reason;
        private String expectedOutcome;

        public Decision() {
            this.expectedOutcome = "Действие будет выполнено успешно";
        }
    }
}
