package ru.sbrf.uddk.ai.testing.service.actions;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class ExploreFormsAction extends BaseAgentAction {
    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing ExploreFormsAction");

        try {
            // 1. Поиск всех форм на странице
            List<WebElement> forms = findForms(driver);

            if (forms.isEmpty()) {
                log.info("No forms found on the page");
                return createActionLog("EXPLORE_FORMS", false,
                        "Формы не найдены на странице");
            }

            log.info("Found {} forms on the page", forms.size());

            // 2. Анализ форм
            List<FormAnalysis> analyses = analyzeForms(forms);

            // 3. Выполнение тестирования форм
            FormTestResult result = testForms(driver, analyses);

            // 4. Создание отчета
            return createFormTestReport(result);

        } catch (Exception e) {
            log.error("ExploreFormsAction failed", e);
            return createActionLog("EXPLORE_FORMS", false,
                    "Ошибка исследования форм: " + e.getMessage());
        }
    }

    private List<WebElement> findForms(WebDriver driver) {
        List<WebElement> forms = new ArrayList<>();

        // Основные селекторы для форм
        String[] formSelectors = {
                "form",
                "[role='form']",
                "[class*='form']",
                "[id*='form']",
                ".search-form",
                ".contact-form",
                ".login-form",
                ".registration-form"
        };

        for (String selector : formSelectors) {
            try {
                List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                elements = elements.stream()
                        .filter(WebElement::isDisplayed)
                        .collect(Collectors.toList());
                forms.addAll(elements);
            } catch (Exception e) {
                log.debug("No forms found with selector: {}", selector);
            }
        }

        return forms.stream()
                .distinct()
                .collect(Collectors.toList());
    }

    private List<FormAnalysis> analyzeForms(List<WebElement> forms) {
        List<FormAnalysis> analyses = new ArrayList<>();

        for (int i = 0; i < forms.size(); i++) {
            FormAnalysis analysis = analyzeSingleForm(forms.get(i), i);
            analyses.add(analysis);
        }

        return analyses;
    }

    private FormAnalysis analyzeSingleForm(WebElement form, int index) {
        FormAnalysis analysis = new FormAnalysis();
        analysis.index = index;
        analysis.formId = form.getAttribute("id");
        analysis.formAction = form.getAttribute("action");
        analysis.formMethod = form.getAttribute("method");

        try {
            // Находим все поля ввода
            analysis.inputFields = findInputFields(form);
            analysis.totalFields = analysis.inputFields.size();

            // Анализируем типы полей
            analysis.fieldTypes = analyzeFieldTypes(analysis.inputFields);

            // Находим кнопки отправки
            analysis.submitButtons = findSubmitButtons(form);

            // Проверяем наличие валидации
            analysis.hasValidation = checkForValidation(form);

            // Определяем тип формы
            analysis.formType = determineFormType(form, analysis);

        } catch (Exception e) {
            log.warn("Failed to analyze form {}: {}", index, e.getMessage());
        }

        return analysis;
    }

    private List<FormField> findInputFields(WebElement form) {
        List<FormField> fields = new ArrayList<>();

        String[] fieldSelectors = {
                "input:not([type='hidden']):not([type='submit']):not([type='button'])",
                "textarea",
                "select",
                "[role='textbox']",
                "[contenteditable='true']"
        };

        for (String selector : fieldSelectors) {
            try {
                List<WebElement> elements = form.findElements(By.cssSelector(selector));
                for (WebElement element : elements) {
                    if (element.isDisplayed()) {
                        FormField field = createFormField(element);
                        fields.add(field);
                    }
                }
            } catch (Exception e) {
                log.debug("No fields found with selector {} in form: {}", selector, e.getMessage());
            }
        }

        return fields;
    }

    private FormField createFormField(WebElement element) {
        FormField field = new FormField();
        field.element = element;
        field.tagName = element.getTagName();
        field.type = element.getAttribute("type");
        field.name = element.getAttribute("name");
        field.id = element.getAttribute("id");
        field.placeholder = element.getAttribute("placeholder");
        field.label = element.getAttribute("aria-label");
        field.isRequired = "true".equals(element.getAttribute("required")) || element.getAttribute("aria-required") != null;
        field.isEnabled = element.isEnabled();
        field.isReadonly = "true".equals(element.getAttribute("readonly"));

        // Генерируем селектор
        field.selector = generateFieldSelector(element);

        // Определяем тестовые данные
        field.testValue = generateTestValue(field);

        return field;
    }

    private Map<String, Integer> analyzeFieldTypes(List<FormField> fields) {
        Map<String, Integer> typeCount = new HashMap<>();

        for (FormField field : fields) {
            String type = field.type != null ? field.type : field.tagName;
            typeCount.put(type, typeCount.getOrDefault(type, 0) + 1);
        }

        return typeCount;
    }

    private List<WebElement> findSubmitButtons(WebElement form) {
        List<WebElement> buttons = new ArrayList<>();

        String[] buttonSelectors = {
                "button[type='submit']",
                "input[type='submit']",
                "button:not([type]):not([type='button'])",
                "[role='button']"
        };

        for (String selector : buttonSelectors) {
            try {
                List<WebElement> elements = form.findElements(By.cssSelector(selector));
                buttons.addAll(elements.stream()
                        .filter(WebElement::isDisplayed)
                        .toList());
            } catch (Exception e) {
                log.debug("No buttons found with selector: {}", selector);
            }
        }

        return buttons;
    }

    private boolean checkForValidation(WebElement form) {
        try {
            // Проверяем наличие атрибутов валидации
            boolean hasRequired = !form.findElements(By.cssSelector("[required]")).isEmpty();
            boolean hasPattern = !form.findElements(By.cssSelector("[pattern]")).isEmpty();
            boolean hasMinMax = !form.findElements(By.cssSelector("[min], [max], [minlength], [maxlength]")).isEmpty();

            return hasRequired || hasPattern || hasMinMax;
        } catch (Exception e) {
            return false;
        }
    }

    private String determineFormType(WebElement form, FormAnalysis analysis) {
        // Определяем тип формы на основе анализа
        String action = analysis.formAction != null ? analysis.formAction.toLowerCase() : "";
        String formId = analysis.formId != null ? analysis.formId.toLowerCase() : "";

        if (action.contains("login") || formId.contains("login")) {
            return "LOGIN_FORM";
        } else if (action.contains("register") || formId.contains("register")) {
            return "REGISTRATION_FORM";
        } else if (action.contains("search") || formId.contains("search")) {
            return "SEARCH_FORM";
        } else if (action.contains("contact") || formId.contains("contact")) {
            return "CONTACT_FORM";
        } else if (analysis.totalFields > 5) {
            return "COMPLEX_FORM";
        } else {
            return "SIMPLE_FORM";
        }
    }

    private FormTestResult testForms(WebDriver driver, List<FormAnalysis> analyses) {
        FormTestResult result = new FormTestResult();
        result.testedForms = new ArrayList<>();
        result.discoveredIssues = new ArrayList<>();

        // Тестируем каждую форму
        for (FormAnalysis analysis : analyses) {
            try {
                FormTestResult formResult = testSingleForm(driver, analysis);
                result.testedForms.add(formResult);

                // Собираем все обнаруженные проблемы
                result.discoveredIssues.addAll(formResult.discoveredIssues);

            } catch (Exception e) {
                log.error("Failed to test form {}: {}", analysis.index, e.getMessage());
                result.discoveredIssues.add("Ошибка тестирования формы #" + analysis.index + ": " + e.getMessage());
            }
        }

        result.success = result.testedForms.stream().anyMatch(r -> r.success);
        return result;
    }

    private FormTestResult testSingleForm(WebDriver driver, FormAnalysis analysis) {
        FormTestResult result = new FormTestResult();
        result.formIndex = analysis.index;
        result.formType = analysis.formType;
        result.testedFields = new ArrayList<>();
        result.discoveredIssues = new ArrayList<>();

        log.info("Testing form {} of type {}", analysis.index, analysis.formType);

        try {
            // 1. Заполняем поля тестовыми данными
            fillFormFields(driver, analysis, result);

            // 2. Проверяем валидацию (если есть)
            if (analysis.hasValidation) {
                testFormValidation(driver, analysis, result);
            }

            // 3. Пробуем отправить форму
            submitForm(driver, analysis, result);

            // 4. Анализируем результат отправки
            analyzeSubmissionResult(driver, analysis, result);

            result.success = result.testedFields.size() > 0;

        } catch (Exception e) {
            log.error("Error testing form: {}", e.getMessage());
            result.discoveredIssues.add("Критическая ошибка: " + e.getMessage());
        }

        return result;
    }

    private void fillFormFields(WebDriver driver, FormAnalysis analysis, FormTestResult result) {
        int filledFields = 0;

        for (FormField field : analysis.inputFields) {
            if (!field.isEnabled || field.isReadonly) {
                log.debug("Skipping disabled/readonly field: {}", field.name);
                continue;
            }

            try {
                log.info("Filling field {} with value: {}", field.name, field.testValue);

                // Заполняем поле
                fillSingleField(field.element, field.type, field.testValue);

                FieldTestResult fieldResult = new FieldTestResult();
                fieldResult.fieldName = field.name;
                fieldResult.fieldType = field.type;
                fieldResult.testValue = field.testValue;
                fieldResult.success = true;
                fieldResult.message = "Поле успешно заполнено";

                result.testedFields.add(fieldResult);
                filledFields++;

                // Небольшая пауза между заполнением полей
                Thread.sleep(100);

            } catch (Exception e) {
                log.error("Failed to fill field {}: {}", field.name, e.getMessage());

                FieldTestResult fieldResult = new FieldTestResult();
                fieldResult.fieldName = field.name;
                fieldResult.fieldType = field.type;
                fieldResult.testValue = field.testValue;
                fieldResult.success = false;
                fieldResult.message = "Ошибка заполнения: " + e.getMessage();

                result.testedFields.add(fieldResult);
                result.discoveredIssues.add("Не удалось заполнить поле '" + field.name + "': " + e.getMessage());
            }
        }

        log.info("Filled {} out of {} fields", filledFields, analysis.totalFields);
    }

    private void fillSingleField(WebElement element, String type, String value) {
        String fieldType = type != null ? type.toLowerCase() : "text";

        switch (fieldType) {
            case "text":
            case "email":
            case "password":
            case "tel":
            case "url":
            case "search":
                element.clear();
                element.sendKeys(value);
                break;

            case "textarea":
                element.clear();
                element.sendKeys(value);
                break;

            case "select":
                Select dropdown = new Select(element);
                if (!dropdown.getOptions().isEmpty()) {
                    dropdown.selectByIndex(0);
                }
                break;

            case "checkbox":
                if (!element.isSelected()) {
                    element.click();
                }
                break;

            case "radio":
                element.click();
                break;

            case "number":
                element.clear();
                element.sendKeys("123");
                break;

            case "date":
                element.sendKeys("2023-12-15");
                break;

            default:
                element.clear();
                element.sendKeys(value);
        }
    }

    private void testFormValidation(WebDriver driver, FormAnalysis analysis, FormTestResult result) {
        log.info("Testing form validation");

        try {
            // Находим обязательные поля
            List<FormField> requiredFields = analysis.inputFields.stream()
                    .filter(f -> f.isRequired)
                    .collect(Collectors.toList());

            if (!requiredFields.isEmpty()) {
                // Пробуем отправить форму с пустыми обязательными полями
                WebElement submitButton = analysis.submitButtons.isEmpty() ?
                        null : analysis.submitButtons.get(0);

                if (submitButton != null) {
                    // Очищаем все поля
                    clearFormFields(analysis);

                    // Пробуем отправить форму
                    submitButton.click();
                    Thread.sleep(1000);

                    // Проверяем, появились ли сообщения об ошибках
                    boolean validationErrors = checkValidationErrors(driver);

                    if (validationErrors) {
                        result.validationTested = true;
                        result.validationPassed = true;
                        result.discoveredIssues.add("Валидация работает: форма не отправляется с пустыми обязательными полями");
                    } else {
                        result.validationTested = true;
                        result.validationPassed = false;
                        result.discoveredIssues.add("ПРОБЛЕМА: Валидация не работает - форма отправилась с пустыми обязательными полями");
                    }

                    // Возвращаем заполненные значения
                    fillFormFields(driver, analysis, result);
                }
            }

        } catch (Exception e) {
            log.error("Validation test failed: {}", e.getMessage());
            result.discoveredIssues.add("Ошибка тестирования валидации: " + e.getMessage());
        }
    }

    private void clearFormFields(FormAnalysis analysis) {
        for (FormField field : analysis.inputFields) {
            try {
                if (field.tagName.equals("select")) {
                    // Для select выбираем пустое значение если возможно
                    Select dropdown = new Select(field.element);
                    try {
                        dropdown.selectByIndex(-1);
                    } catch (Exception e) {
                        // Игнорируем если нельзя выбрать пустое значение
                    }
                } else if (field.type.equals("checkbox") || field.type.equals("radio")) {
                    // Для чекбоксов и радио снимаем выбор
                    if (field.element.isSelected()) {
                        field.element.click();
                    }
                } else {
                    // Для текстовых полей очищаем
                    field.element.clear();
                }
            } catch (Exception e) {
                log.debug("Failed to clear field {}: {}", field.name, e.getMessage());
            }
        }
    }

    private boolean checkValidationErrors(WebDriver driver) {
        try {
            // Проверяем наличие сообщений об ошибках валидации
            List<WebElement> errorElements = driver.findElements(By.cssSelector(
                    ".error, .invalid-feedback, .validation-error, [aria-invalid='true'], " +
                            "[role='alert'], .has-error"));

            // Проверяем, появились ли подсвеченные поля с ошибками
            List<WebElement> invalidFields = driver.findElements(By.cssSelector(
                    "input:invalid, textarea:invalid, select:invalid"));

            return !errorElements.isEmpty() || !invalidFields.isEmpty();

        } catch (Exception e) {
            return false;
        }
    }

    private void submitForm(WebDriver driver, FormAnalysis analysis, FormTestResult result) {
        if (analysis.submitButtons.isEmpty()) {
            log.warn("No submit buttons found in form");
            result.discoveredIssues.add("Кнопка отправки не найдена в форме");
            return;
        }

        try {
            WebElement submitButton = analysis.submitButtons.get(0);
            String previousUrl = driver.getCurrentUrl();

            log.info("Submitting form with button: {}", submitButton.getText());

//            submitButton.click();
//            Thread.sleep(2000); // Ждем обработки формы

            String currentUrl = driver.getCurrentUrl();
            result.submissionAttempted = true;
            result.urlChanged = !previousUrl.equals(currentUrl);
            result.newUrl = currentUrl;

        } catch (Exception e) {
            log.error("Form submission failed: {}", e.getMessage());
            result.discoveredIssues.add("Ошибка отправки формы: " + e.getMessage());
        }
    }

    private void analyzeSubmissionResult(WebDriver driver, FormAnalysis analysis, FormTestResult result) {
        try {
            // Проверяем наличие сообщений об успешной отправке
            boolean successMessage = checkSuccessMessage(driver);

            // Проверяем наличие ошибок отправки
            boolean errorMessage = checkSubmissionErrors(driver);

            if (successMessage) {
                result.submissionSuccess = true;
                result.discoveredIssues.add("Форма успешно отправлена");
            } else if (errorMessage) {
                result.submissionSuccess = false;
                result.discoveredIssues.add("Обнаружены ошибки при отправке формы");
            } else if (result.urlChanged) {
                result.submissionSuccess = true;
                result.discoveredIssues.add("Форма отправлена, произошел переход на другую страницу");
            } else {
                result.submissionSuccess = false;
                result.discoveredIssues.add("Неясный результат отправки формы");
            }

        } catch (Exception e) {
            log.error("Failed to analyze submission result: {}", e.getMessage());
        }
    }

    private boolean checkSuccessMessage(WebDriver driver) {
        try {
            List<WebElement> successElements = driver.findElements(By.cssSelector(
                    ".success, .alert-success, .form-success, [role='status']"));

            // Также проверяем текст на странице
            String pageText = driver.findElement(By.tagName("body")).getText().toLowerCase();
            boolean hasSuccessKeywords = pageText.contains("success") ||
                    pageText.contains("thank you") ||
                    pageText.contains("отправлено") ||
                    pageText.contains("успешно");

            return !successElements.isEmpty() || hasSuccessKeywords;

        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkSubmissionErrors(WebDriver driver) {
        try {
            List<WebElement> errorElements = driver.findElements(By.cssSelector(
                    ".error, .alert-danger, .form-error, .server-error"));

            // Проверяем наличие HTTP ошибок в заголовке
            String pageTitle = driver.getTitle().toLowerCase();
            boolean hasErrorInTitle = pageTitle.contains("error") ||
                    pageTitle.contains("500") ||
                    pageTitle.contains("400");

            return !errorElements.isEmpty() || hasErrorInTitle;

        } catch (Exception e) {
            return false;
        }
    }

    private AgentAction createFormTestReport(FormTestResult result) {
        StringBuilder message = new StringBuilder();

        if (!result.success) {
            message.append("Исследование форм не удалось\n");
        } else {
            message.append("Исследование форм завершено\n");

            // Сводка по протестированным формам
            for (FormTestResult formResult : result.testedForms) {
                if (formResult.success) {
                    message.append("Форма #").append(formResult.formIndex)
                            .append(" (").append(formResult.formType).append("): ");

                    if (formResult.testedFields != null) {
                        message.append("заполнено ").append(formResult.testedFields.size())
                                .append(" полей, ");
                    }

                    if (formResult.submissionAttempted) {
                        message.append(formResult.submissionSuccess ? "отправлена успешно" : "ошибка отправки");
                    }

                    message.append("\n");
                }
            }
        }

        // Отчет об обнаруженных проблемах
        if (!result.discoveredIssues.isEmpty()) {
            message.append("\nОбнаруженные проблемы:\n");
            for (String issue : result.discoveredIssues) {
                message.append("• ").append(issue).append("\n");
            }
        }

        return createActionLog("EXPLORE_FORMS", result.success, message.toString());
    }

    private String generateFieldSelector(WebElement element) {
        try {
            String id = element.getAttribute("id");
            if (id != null && !id.isEmpty()) {
                return "#" + id;
            }

            String name = element.getAttribute("name");
            if (name != null && !name.isEmpty()) {
                return "[name='" + name + "']";
            }

            return element.getTagName() + "[type='" + element.getAttribute("type") + "']";
        } catch (Exception e) {
            return "input";
        }
    }

    private String generateTestValue(FormField field) {
        String type = field.type != null ? field.type.toLowerCase() : "text";

        switch (type) {
            case "email":
                return "test@example.com";
            case "password":
                return "TestPassword123";
            case "tel":
                return "+1234567890";
            case "url":
                return "https://example.com";
            case "number":
                return "123";
            case "date":
                return "2023-12-15";
            case "search":
                return "test search";
            case "text":
            default:
                if (field.name != null && field.name.toLowerCase().contains("name")) {
                    return "Тестовый Пользователь";
                } else if (field.placeholder != null && field.placeholder.toLowerCase().contains("message")) {
                    return "Тестовое сообщение для проверки формы.";
                } else {
                    return "Тестовое значение";
                }
        }
    }

    // Вспомогательные классы
    private static class FormAnalysis {
        int index;
        String formId;
        String formAction;
        String formMethod;
        String formType;
        List<FormField> inputFields = new ArrayList<>();
        int totalFields;
        Map<String, Integer> fieldTypes = new HashMap<>();
        List<WebElement> submitButtons = new ArrayList<>();
        boolean hasValidation;
    }

    private static class FormField {
        WebElement element;
        String tagName;
        String type;
        String name;
        String id;
        String placeholder;
        String label;
        String selector;
        String testValue;
        boolean isRequired;
        boolean isEnabled;
        boolean isReadonly;
    }

    private static class FormTestResult {
        boolean success;
        int formIndex;
        String formType;
        List<FieldTestResult> testedFields = new ArrayList<>();
        List<FormTestResult> testedForms = new ArrayList<>();
        boolean validationTested;
        boolean validationPassed;
        boolean submissionAttempted;
        boolean submissionSuccess;
        boolean urlChanged;
        String newUrl;
        List<String> discoveredIssues = new ArrayList<>();
    }

    private static class FieldTestResult {
        String fieldName;
        String fieldType;
        String testValue;
        boolean success;
        String message;
    }
}
