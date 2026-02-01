package ru.sbrf.uddk.ai.testing.service.actions;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ExploreMenuAction extends BaseAgentAction {
    private static final Set<String> MENU_SELECTORS = Set.of(
            "nav",
            ".menu",
            ".navbar",
            ".navigation",
            "[role='navigation']",
            "[role='menu']",
            "[role='tablist']",
            ".nav",
            ".header",
            "[class*='menu']",
            "[class*='nav']"
    );

    private static final Set<String> MENU_ITEM_SELECTORS = Set.of(
            "a[href]",
            "[role='menuitem']",
            "[role='tab']",
            "[role='button']",
            ".menu-item",
            ".nav-item",
            ".dropdown-item",
            "li > a",
            "button:not([type='submit'])"
    );

    private static final Set<String> DROPDOWN_SELECTORS = Set.of(
            ".dropdown",
            ".submenu",
            "[aria-haspopup='true']",
            "[data-toggle='dropdown']"
    );

    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing ExploreMenuAction");

        try {
            // 1. Поиск всех меню на странице
            List<WebElement> menus = findMenus(driver);

            if (menus.isEmpty()) {
                log.info("No menus found on the page");
                return createActionLog("EXPLORE_MENU", false,
                        "Меню не найдено на странице");
            }

            log.info("Found {} menus on the page", menus.size());

            // 2. Анализ структуры меню
            MenuAnalysis analysis = analyzeMenus(menus);

            // 3. Выбор стратегии исследования
            ExplorationStrategy strategy = chooseStrategy(analysis);

            // 4. Выполнение исследования
            ExplorationResult result = executeStrategy(driver, analysis, strategy);

            // 5. Создание отчета
            return createExplorationReport(result, analysis);

        } catch (Exception e) {
            log.error("ExploreMenuAction failed", e);
            return createActionLog("EXPLORE_MENU", false,
                    "Ошибка исследования меню: " + e.getMessage());
        }
    }

    private List<WebElement> findMenus(WebDriver driver) {
        List<WebElement> allMenus = new ArrayList<>();

        for (String selector : MENU_SELECTORS) {
            try {
                List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                elements = elements.stream()
                        .filter(WebElement::isDisplayed)
                        .toList();
                allMenus.addAll(elements);
            } catch (Exception e) {
                log.debug("No elements found with selector: {}", selector);
            }
        }

        // Удаляем дубликаты (элементы, которые содержатся внутри других меню)
        List<WebElement> uniqueMenus = new ArrayList<>();
        for (WebElement menu : allMenus) {
            boolean isContained = false;
            for (WebElement otherMenu : allMenus) {
                if (menu != otherMenu && isContainedWithin(menu, otherMenu)) {
                    isContained = true;
                    break;
                }
            }
            if (!isContained) {
                uniqueMenus.add(menu);
            }
        }

        return uniqueMenus;
    }

    private boolean isContainedWithin(WebElement element, WebElement container) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) ((WebDriver) element);
            return (Boolean) js.executeScript(
                    "return arguments[1].contains(arguments[0]);", element, container);
        } catch (Exception e) {
            return false;
        }
    }

    private MenuAnalysis analyzeMenus(List<WebElement> menus) {
        MenuAnalysis analysis = new MenuAnalysis();
        analysis.totalMenus = menus.size();

        for (int i = 0; i < menus.size(); i++) {
            WebElement menu = menus.get(i);
            MenuInfo menuInfo = analyzeSingleMenu(menu, i);
            analysis.menus.add(menuInfo);

            // Обновляем статистику
            analysis.totalItems += menuInfo.items.size();
            analysis.totalDropdowns += menuInfo.dropdowns.size();
            analysis.totalClickableItems += menuInfo.clickableItems;
            analysis.hasComplexMenu |= menuInfo.hasDropdowns;
        }

        log.info("Menu analysis: {} menus, {} items, {} dropdowns",
                analysis.totalMenus, analysis.totalItems, analysis.totalDropdowns);

        return analysis;
    }

    private MenuInfo analyzeSingleMenu(WebElement menu, int index) {
        MenuInfo info = new MenuInfo();
        info.index = index;
        info.location = getElementLocation(menu);

        try {
            // Поиск всех элементов меню
            info.items = findAllMenuItems(menu);
            info.clickableItems = countClickableItems(info.items);

            // Поиск выпадающих меню
            info.dropdowns = findDropdowns(menu);
            info.hasDropdowns = !info.dropdowns.isEmpty();

            // Анализ структуры
            info.structure = analyzeMenuStructure(menu);

            // Определение типа меню
            info.type = determineMenuType(menu, info);

        } catch (Exception e) {
            log.warn("Failed to analyze menu {}: {}", index, e.getMessage());
        }

        return info;
    }

    private List<MenuItem> findAllMenuItems(WebElement menu) {
        List<MenuItem> items = new ArrayList<>();

        for (String selector : MENU_ITEM_SELECTORS) {
            try {
                List<WebElement> elements = menu.findElements(By.cssSelector(selector));
                for (WebElement element : elements) {
                    if (element.isDisplayed() && element.isEnabled()) {
                        MenuItem item = createMenuItem(element, selector);
                        if (isMenuItemValid(item)) {
                            items.add(item);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("No items found with selector {} in menu: {}", selector, e.getMessage());
            }
        }

        // Удаляем дубликаты
        return items.stream()
                .distinct()
                .sorted(Comparator.comparingInt(MenuItem::getY).thenComparingInt(MenuItem::getX))
                .collect(Collectors.toList());
    }

    private MenuItem createMenuItem(WebElement element, String selectorType) {
        MenuItem item = new MenuItem();
        item.element = element;
        item.text = element.getText().trim();
        item.tagName = element.getTagName();
        item.href = element.getAttribute("href");
        item.selector = generateSelector(element);
        item.type = selectorType;
        item.location = getElementLocation(element);

        // Проверяем, является ли элементом выпадающего меню
        item.isDropdownTrigger = isDropdownTrigger(element);

        // Определяем роль и доступность
        item.role = element.getAttribute("role");
        item.ariaLabel = element.getAttribute("aria-label");
        item.tabIndex = element.getAttribute("tabindex");
        item.isClickable = isElementClickable(element);

        return item;
    }

    private boolean isMenuItemValid(MenuItem item) {
        // Фильтруем невалидные элементы
        if (item.text.isEmpty() && item.ariaLabel == null && item.href == null) {
            return false;
        }

        // Игнорируем слишком маленькие элементы (возможно, иконки без текста)
        if (item.location.width < 10 && item.location.height < 10 && item.text.isEmpty()) {
            return false;
        }

        return true;
    }

    private int countClickableItems(List<MenuItem> items) {
        return (int) items.stream()
                .filter(item -> item.isClickable)
                .count();
    }

    private List<WebElement> findDropdowns(WebElement menu) {
        List<WebElement> dropdowns = new ArrayList<>();

        for (String selector : DROPDOWN_SELECTORS) {
            try {
                List<WebElement> elements = menu.findElements(By.cssSelector(selector));
                elements = elements.stream()
                        .filter(el -> el.isDisplayed() && el.isEnabled())
                        .collect(Collectors.toList());
                dropdowns.addAll(elements);
            } catch (Exception e) {
                log.debug("No dropdowns found with selector: {}", selector);
            }
        }

        return dropdowns;
    }

    private MenuStructure analyzeMenuStructure(WebElement menu) {
        MenuStructure structure = new MenuStructure();

        try {
            // Определяем ориентацию меню
            List<WebElement> items = menu.findElements(By.cssSelector("li, .menu-item, [role='menuitem']"));
            if (items.size() > 1) {
                WebElement first = items.get(0);
                WebElement second = items.get(1);

                Rectangle firstRect = first.getRect();
                Rectangle secondRect = second.getRect();

                if (Math.abs(firstRect.y - secondRect.y) < 10) {
                    structure.orientation = "horizontal";
                } else if (Math.abs(firstRect.x - secondRect.x) < 10) {
                    structure.orientation = "vertical";
                } else {
                    structure.orientation = "mixed";
                }
            }

            // Проверяем наличие вложенных меню
            structure.hasNestedMenus = !menu.findElements(By.cssSelector("ul ul, ol ol, nav nav")).isEmpty();

            // Проверяем наличие мега-меню
            structure.hasMegaMenu = menu.findElements(By.cssSelector(".mega-menu, .mega-dropdown")).size() > 0;

        } catch (Exception e) {
            log.debug("Failed to analyze menu structure: {}", e.getMessage());
        }

        return structure;
    }

    private String determineMenuType(WebElement menu, MenuInfo info) {
        // Определяем тип меню на основе анализа
        if (info.hasDropdowns) {
            return "DROPDOWN_MENU";
        }

        if (info.items.size() > 7) {
            return "MEGA_MENU";
        }

        if (info.structure.orientation != null) {
            return info.structure.orientation.toUpperCase() + "_MENU";
        }

        return "SIMPLE_MENU";
    }

    private ExplorationStrategy chooseStrategy(MenuAnalysis analysis) {
        ExplorationStrategy strategy = new ExplorationStrategy();

        if (analysis.totalMenus == 0) {
            strategy.type = StrategyType.NO_MENU;
        } else if (analysis.totalItems > 20) {
            strategy.type = StrategyType.SAMPLE_EXPLORATION;
            strategy.sampleSize = Math.min(5, analysis.totalItems / 4);
        } else if (analysis.hasComplexMenu) {
            strategy.type = StrategyType.DROPDOWN_EXPLORATION;
        } else {
            strategy.type = StrategyType.FULL_EXPLORATION;
        }

        // Выбираем приоритетное меню
        if (!analysis.menus.isEmpty()) {
            Optional<MenuInfo> mainMenu = analysis.menus.stream()
                    .filter(m -> m.items.size() > 2)
                    .max(Comparator.comparingInt(m -> m.items.size()));

            strategy.targetMenuIndex = mainMenu.map(m -> m.index).orElse(0);
        }

        log.info("Chosen strategy: {} for menu {}", strategy.type, strategy.targetMenuIndex);

        return strategy;
    }

    private ExplorationResult executeStrategy(WebDriver driver, MenuAnalysis analysis, ExplorationStrategy strategy) {
        ExplorationResult result = new ExplorationResult();
        result.strategy = strategy.type;

        switch (strategy.type) {
            case NO_MENU:
                result.message = "Меню не обнаружено";
                result.success = false;
                break;

            case FULL_EXPLORATION:
                result = exploreFullMenu(driver, analysis.menus.get(strategy.targetMenuIndex));
                break;

            case SAMPLE_EXPLORATION:
                result = exploreSampleMenu(driver, analysis.menus.get(strategy.targetMenuIndex), strategy.sampleSize);
                break;

            case DROPDOWN_EXPLORATION:
                result = exploreDropdownMenu(driver, analysis.menus.get(strategy.targetMenuIndex));
                break;
        }

        return result;
    }

    private ExplorationResult exploreFullMenu(WebDriver driver, MenuInfo menuInfo) {
        ExplorationResult result = new ExplorationResult();
        result.exploredItems = new ArrayList<>();
        result.discoveredIssues = new ArrayList<>();

        log.info("Exploring full menu with {} items", menuInfo.items.size());

        int clickedCount = 0;
        int maxClicks = Math.min(10, menuInfo.items.size()); // Ограничиваем количество кликов

        for (int i = 0; i < menuInfo.items.size() && clickedCount < maxClicks; i++) {
            MenuItem item = menuInfo.items.get(i);

            if (!item.isClickable) {
                log.debug("Skipping non-clickable item: {}", item.text);
                continue;
            }

            try {
                log.info("Clicking menu item {}: {}", i + 1, item.text);

                // Запоминаем текущий URL перед кликом
                String previousUrl = driver.getCurrentUrl();

                // Кликаем на элемент
                item.element.click();

                // Ждем загрузки или изменений
                Thread.sleep(1000);

                String currentUrl = driver.getCurrentUrl();

                // Анализируем результат клика
                MenuClickResult clickResult = analyzeMenuClick(
                        previousUrl, currentUrl, item, driver);

                result.exploredItems.add(clickResult);
                clickedCount++;

                // Проверяем наличие проблем
                if (clickResult.hasIssue) {
                    result.discoveredIssues.add(clickResult.issueDescription);
                }

                // Если мы перешли на другую страницу, возвращаемся назад
                if (!previousUrl.equals(currentUrl) && !currentUrl.contains("#")) {
                    log.info("Navigated to new page, going back to menu");
                    driver.navigate().back();
                    Thread.sleep(1500); // Ждем загрузки предыдущей страницы
                }

                // Если открылось выпадающее меню, исследуем его
                if (clickResult.openedDropdown) {
                    exploreDropdownContent(driver, item);
                    // Закрываем выпадающее меню (клик в другое место)
                    new Actions(driver).moveByOffset(10, 10).click().perform();
                    Thread.sleep(500);
                }

            } catch (Exception e) {
                log.error("Failed to click menu item {}: {}", item.text, e.getMessage());
                result.discoveredIssues.add("Ошибка при клике на '" + item.text + "': " + e.getMessage());
            }
        }

        result.success = clickedCount > 0;
        result.message = String.format("Исследовано %d из %d пунктов меню",
                clickedCount, menuInfo.items.size());

        return result;
    }

    private ExplorationResult exploreSampleMenu(WebDriver driver, MenuInfo menuInfo, int sampleSize) {
        ExplorationResult result = new ExplorationResult();
        result.exploredItems = new ArrayList<>();
        result.discoveredIssues = new ArrayList<>();

        log.info("Exploring sample of {} items from menu with {} items",
                sampleSize, menuInfo.items.size());

        // Выбираем случайные элементы для исследования
        List<MenuItem> sampleItems = selectSampleItems(menuInfo.items, sampleSize);

        for (MenuItem item : sampleItems) {
            try {
                log.info("Clicking sample menu item: {}", item.text);

                String previousUrl = driver.getCurrentUrl();
                item.element.click();
                Thread.sleep(1000);

                String currentUrl = driver.getCurrentUrl();
                MenuClickResult clickResult = analyzeMenuClick(
                        previousUrl, currentUrl, item, driver);

                result.exploredItems.add(clickResult);

                if (clickResult.hasIssue) {
                    result.discoveredIssues.add(clickResult.issueDescription);
                }

                // Возвращаемся если ушли со страницы
                if (!previousUrl.equals(currentUrl) && !currentUrl.contains("#")) {
                    driver.navigate().back();
                    Thread.sleep(1500);
                }

            } catch (Exception e) {
                log.error("Failed to click sample item {}: {}", item.text, e.getMessage());
                result.discoveredIssues.add("Ошибка при клике на '" + item.text + "': " + e.getMessage());
            }
        }

        result.success = !result.exploredItems.isEmpty();
        result.message = String.format("Исследовано %d случайных пунктов меню", result.exploredItems.size());

        return result;
    }

    private ExplorationResult exploreDropdownMenu(WebDriver driver, MenuInfo menuInfo) {
        ExplorationResult result = new ExplorationResult();
        result.exploredItems = new ArrayList<>();
        result.discoveredIssues = new ArrayList<>();

        log.info("Exploring dropdown menu with {} dropdowns", menuInfo.dropdowns.size());

        // Сначала исследуем триггеры выпадающих меню
        List<MenuItem> dropdownTriggers = menuInfo.items.stream()
                .filter(item -> item.isDropdownTrigger)
                .collect(Collectors.toList());

        for (MenuItem trigger : dropdownTriggers) {
            try {
                log.info("Opening dropdown: {}", trigger.text);

                // Открываем выпадающее меню
                trigger.element.click();
                Thread.sleep(800); // Ждем открытия

                // Ищем открытое выпадающее меню
                WebElement dropdownContent = findOpenDropdownContent(driver);

                if (dropdownContent != null) {
                    // Исследуем содержимое выпадающего меню
                    List<MenuItem> dropdownItems = findDropdownItems(dropdownContent);

                    log.info("Found {} items in dropdown", dropdownItems.size());

                    // Кликаем на первый доступный элемент в выпадающем меню
                    if (!dropdownItems.isEmpty()) {
                        MenuItem firstItem = dropdownItems.get(0);
                        firstItem.element.click();
                        Thread.sleep(1000);

                        MenuClickResult clickResult = new MenuClickResult();
                        clickResult.itemText = trigger.text + " -> " + firstItem.text;
                        clickResult.action = "OPEN_DROPDOWN_AND_CLICK";
                        clickResult.success = true;
                        clickResult.openedDropdown = true;

                        result.exploredItems.add(clickResult);

                        // Возвращаемся если нужно
                        driver.navigate().back();
                        Thread.sleep(1500);
                    }

                    // Закрываем выпадающее меню
                    new Actions(driver).moveByOffset(10, 10).click().perform();
                    Thread.sleep(500);

                } else {
                    log.warn("Dropdown content not found after clicking trigger");
                    result.discoveredIssues.add("Выпадающее меню не открылось после клика на '" + trigger.text + "'");
                }

            } catch (Exception e) {
                log.error("Failed to explore dropdown: {}", e.getMessage());
                result.discoveredIssues.add("Ошибка исследования выпадающего меню: " + e.getMessage());
            }
        }

        result.success = !result.exploredItems.isEmpty();
        result.message = String.format("Исследовано %d выпадающих меню", result.exploredItems.size());

        return result;
    }

    private List<MenuItem> selectSampleItems(List<MenuItem> items, int sampleSize) {
        if (items.size() <= sampleSize) {
            return new ArrayList<>(items);
        }

        // Выбираем стратегически важные элементы + случайные
        List<MenuItem> sample = new ArrayList<>();

        // Всегда включаем первый и последний элементы
        sample.add(items.get(0));
        sample.add(items.get(items.size() - 1));

        // Включаем элементы с определенными ключевыми словами
        List<MenuItem> importantItems = items.stream()
                .filter(item -> containsImportantKeywords(item.text))
                .limit(2)
                .collect(Collectors.toList());
        sample.addAll(importantItems);

        // Добавляем случайные элементы до достижения sampleSize
        Random random = new Random();
        while (sample.size() < sampleSize && sample.size() < items.size()) {
            MenuItem randomItem = items.get(random.nextInt(items.size()));
            if (!sample.contains(randomItem)) {
                sample.add(randomItem);
            }
        }

        return sample;
    }

    private boolean containsImportantKeywords(String text) {
        if (text == null) return false;

        String lowerText = text.toLowerCase();
        String[] keywords = {"home", "main", "products", "services", "about", "contact",
                "login", "register", "shop", "cart", "search", "help"};

        for (String keyword : keywords) {
            if (lowerText.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private MenuClickResult analyzeMenuClick(String previousUrl, String currentUrl,
                                             MenuItem item, WebDriver driver) {
        MenuClickResult result = new MenuClickResult();
        result.itemText = item.text;
        result.previousUrl = previousUrl;
        result.currentUrl = currentUrl;
        result.action = "CLICK";

        try {
            // Проверяем, изменился ли URL
            boolean urlChanged = !previousUrl.equals(currentUrl);
            result.urlChanged = urlChanged;

            // Проверяем, открылось ли выпадающее меню
            result.openedDropdown = isDropdownOpen(driver, item);

            // Проверяем наличие ошибок на странице
            result.hasError = checkForPageErrors(driver);

            // Проверяем загрузку страницы
            result.pageLoaded = checkPageLoaded(driver);

            // Определяем успешность
            result.success = result.pageLoaded && !result.hasError;

            // Формируем описание результата
            if (result.openedDropdown) {
                result.description = "Открылось выпадающее меню";
            } else if (urlChanged) {
                result.description = "Переход на новую страницу: " + currentUrl;
            } else if (currentUrl.contains("#")) {
                result.description = "Прокрутка к якорю на странице";
            } else {
                result.description = "Клик выполнен, видимых изменений нет";
            }

            // Проверяем наличие проблем
            if (!result.pageLoaded) {
                result.hasIssue = true;
                result.issueDescription = "Страница не загрузилась после клика на '" + item.text + "'";
            } else if (result.hasError) {
                result.hasIssue = true;
                result.issueDescription = "Обнаружены ошибки после клика на '" + item.text + "'";
            }

        } catch (Exception e) {
            log.error("Failed to analyze menu click result: {}", e.getMessage());
            result.success = false;
            result.hasIssue = true;
            result.issueDescription = "Ошибка анализа результата клика: " + e.getMessage();
        }

        return result;
    }

    private void exploreDropdownContent(WebDriver driver, MenuItem trigger) {
        try {
            WebElement dropdown = findOpenDropdownContent(driver);
            if (dropdown != null) {
                log.info("Exploring dropdown content");

                // Находим все элементы в выпадающем меню
                List<WebElement> dropdownItems = dropdown.findElements(By.cssSelector(
                        "a, button, [role='menuitem']"));

                // Кликаем на первый элемент если он есть
                if (!dropdownItems.isEmpty()) {
                    WebElement firstItem = dropdownItems.get(0);
                    if (firstItem.isDisplayed() && firstItem.isEnabled()) {
                        firstItem.click();
                        Thread.sleep(1000);

                        // Возвращаемся назад если перешли на другую страницу
                        driver.navigate().back();
                        Thread.sleep(1500);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to explore dropdown content: {}", e.getMessage());
        }
    }

    private WebElement findOpenDropdownContent(WebDriver driver) {
        try {
            // Ищем видимые выпадающие меню
            List<WebElement> dropdowns = driver.findElements(By.cssSelector(
                    ".dropdown-menu.show, .dropdown-menu[style*='display: block'], " +
                            "[role='menu'][style*='display: block'], .submenu.show"));

            return dropdowns.stream()
                    .filter(WebElement::isDisplayed)
                    .findFirst()
                    .orElse(null);

        } catch (Exception e) {
            return null;
        }
    }

    private List<MenuItem> findDropdownItems(WebElement dropdown) {
        List<MenuItem> items = new ArrayList<>();

        try {
            List<WebElement> elements = dropdown.findElements(By.cssSelector(
                    "a, button, [role='menuitem'], [role='menuitemradio'], [role='menuitemcheckbox']"));

            for (WebElement element : elements) {
                if (element.isDisplayed() && element.isEnabled()) {
                    MenuItem item = createMenuItem(element, "DROPDOWN_ITEM");
                    items.add(item);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to find dropdown items: {}", e.getMessage());
        }

        return items;
    }

    private AgentAction createExplorationReport(
            ExplorationResult result, MenuAnalysis analysis) {

        StringBuilder message = new StringBuilder();

        if (!result.success) {
            message.append("Исследование меню не удалось: ").append(result.message);
        } else {
            message.append(result.message).append("\n");

            if (!result.exploredItems.isEmpty()) {
                message.append("Исследованные пункты:\n");
                for (MenuClickResult itemResult : result.exploredItems) {
                    message.append("  • ").append(itemResult.itemText)
                            .append(" - ").append(itemResult.description).append("\n");
                }
            }

            message.append("Статистика: ")
                    .append(analysis.totalMenus).append(" меню, ")
                    .append(analysis.totalItems).append(" пунктов, ")
                    .append(analysis.totalDropdowns).append(" выпадающих меню\n");
        }

        if (!result.discoveredIssues.isEmpty()) {
            message.append("\nОбнаруженные проблемы:\n");
            for (String issue : result.discoveredIssues) {
                message.append("  • ").append(issue).append("\n");
            }
        }

        return createActionLog("EXPLORE_MENU", result.success, message.toString());
    }

    // Вспомогательные методы
    private String generateSelector(WebElement element) {
        try {
            String id = element.getAttribute("id");
            if (id != null && !id.isEmpty()) {
                return "#" + id;
            }

            String className = element.getAttribute("class");
            if (className != null && !className.isEmpty()) {
                String[] classes = className.split("\\s+");
                if (classes.length > 0) {
                    return "." + classes[0];
                }
            }

            return element.getTagName();
        } catch (Exception e) {
            return "element";
        }
    }

    private boolean isDropdownTrigger(WebElement element) {
        String ariaHasPopup = element.getAttribute("aria-haspopup");
        String dataToggle = element.getAttribute("data-toggle");
        String className = element.getAttribute("class");

        return "true".equals(ariaHasPopup) ||
                "dropdown".equals(dataToggle) ||
                (className != null && className.contains("dropdown"));
    }

    private boolean isElementClickable(WebElement element) {
        try {
            return element.isEnabled() && element.isDisplayed() &&
                    element.getSize().getWidth() > 0 &&
                    element.getSize().getHeight() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Rectangle getElementLocation(WebElement element) {
        try {
            return element.getRect();
        } catch (Exception e) {
            return new Rectangle(0, 0, 0, 0);
        }
    }

    private boolean isDropdownOpen(WebDriver driver, MenuItem item) {
        try {
            // Проверяем, появились ли дополнительные элементы рядом
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String script = """
                    function hasDropdownOpen(element) {
                        const rect = element.getBoundingClientRect();
                        const belowX = rect.left + rect.width / 2;
                        const belowY = rect.bottom + 5;
                        
                        const elementBelow = document.elementFromPoint(belowX, belowY);
                        return elementBelow && 
                               elementBelow !== element && 
                               !element.contains(elementBelow) &&
                               (elementBelow.classList.contains('dropdown-menu') || 
                                elementBelow.getAttribute('role') === 'menu');
                    }
                    return hasDropdownOpen(arguments[0]);
                    """;

            return (Boolean) js.executeScript(script, item.element);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkForPageErrors(WebDriver driver) {
        try {
            // Проверяем наличие ошибок на странице
            List<WebElement> errors = driver.findElements(By.cssSelector(
                    ".error, .alert-danger, [role='alert'], .has-error"));

            // Проверяем статус страницы через JavaScript
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String pageStatus = (String) js.executeScript("""
                    if (document.title && document.title.toLowerCase().includes('error')) return 'error';
                    if (document.body.innerText.toLowerCase().includes('404')) return '404';
                    if (document.body.innerText.toLowerCase().includes('500')) return '500';
                    return 'ok';
                    """);

            return !errors.isEmpty() || !"ok".equals(pageStatus);

        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkPageLoaded(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String readyState = (String) js.executeScript("return document.readyState");
            return "complete".equals(readyState);
        } catch (Exception e) {
            return false;
        }
    }

    private enum StrategyType {
        NO_MENU,
        FULL_EXPLORATION,
        SAMPLE_EXPLORATION,
        DROPDOWN_EXPLORATION
    }

    // Вспомогательные классы
    private static class MenuAnalysis {
        int totalMenus;
        int totalItems;
        int totalDropdowns;
        int totalClickableItems;
        boolean hasComplexMenu;
        List<MenuInfo> menus = new ArrayList<>();
    }

    private static class MenuInfo {
        int index;
        String type;
        Rectangle location;
        List<MenuItem> items = new ArrayList<>();
        List<WebElement> dropdowns = new ArrayList<>();
        boolean hasDropdowns;
        int clickableItems;
        MenuStructure structure = new MenuStructure();
    }

    private static class MenuItem {
        WebElement element;
        String text;
        String tagName;
        String href;
        String selector;
        String type;
        Rectangle location;
        boolean isDropdownTrigger;
        String role;
        String ariaLabel;
        String tabIndex;
        boolean isClickable;

        int getX() {
            return location.x;
        }

        int getY() {
            return location.y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MenuItem menuItem = (MenuItem) o;
            return Objects.equals(selector, menuItem.selector);
        }

        @Override
        public int hashCode() {
            return Objects.hash(selector);
        }
    }

    private static class MenuStructure {
        String orientation;
        boolean hasNestedMenus;
        boolean hasMegaMenu;
    }

    private static class ExplorationStrategy {
        StrategyType type;
        int targetMenuIndex = 0;
        int sampleSize = 3;
    }

    private static class ExplorationResult {
        boolean success;
        String message;
        StrategyType strategy;
        List<MenuClickResult> exploredItems = new ArrayList<>();
        List<String> discoveredIssues = new ArrayList<>();
    }

    private static class MenuClickResult {
        String itemText;
        String action;
        String previousUrl;
        String currentUrl;
        String description;
        boolean success;
        boolean urlChanged;
        boolean openedDropdown;
        boolean hasError;
        boolean pageLoaded;
        boolean hasIssue;
        String issueDescription;
    }
}
