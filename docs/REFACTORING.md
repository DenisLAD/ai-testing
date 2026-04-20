# Рефакторинг архитектуры по принципам SOLID и GRASP

## Новая архитектура

### Слои приложения

```
┌─────────────────────────────────────────────────────────────────┐
│                    Presentation Layer (UI)                       │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │  Controllers    │  │  Views (Vaadin) │  │   REST API      │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                  Application Layer (Services)                    │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │ TestAgentAppSvc │  │  SessionAppSvc  │  │  TestGenAppSvc  │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Domain Layer (Business Logic)                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Domain Services (Interfaces)                             │  │
│  │  - AIDecisionService                                      │  │
│  │  - ObservationCaptureService                              │  │
│  │  - WebDriverProvider                                      │  │
│  │  - ActionFactory                                          │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Domain Models                                            │  │
│  │  - Decision, Observation, InteractiveElement              │  │
│  │  - ActionHistory, Issue, ElementBounds                    │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Entities                                                 │  │
│  │  - TestSession, AgentAction, DiscoveredIssue              │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                 Infrastructure Layer (Implementations)           │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │  AI Services    │  │  Observation    │  │   WebDriver     │ │
│  │  - PromptBuilder│  │  - DOMExtractor │  │   - ChromeDrv   │ │
│  │  - DecisionParse│  │  - ElementScan  │  │   - Manager     │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
│  ┌─────────────────┐  ┌─────────────────┐                       │
│  │  Persistence    │  │   Actions       │                       │
│  │  - Repositories │  │   - Click       │                       │
│  │  - JPA Entities │  │   - Type        │                       │
│  └─────────────────┘  └─────────────────┘                       │
└─────────────────────────────────────────────────────────────────┘
```

## Структура пакетов

```
ru.sbrf.uddk.ai.testing/
├── presentation/                    # UI слой
│   ├── rest/                        # REST контроллеры
│   │   ├── TestSessionController
│   │   ├── TestGenerationController
│   │   └── ...
│   └── view/                        # Vaadin представления
│
├── application/                     # Application слой
│   ├── ai/                          # AI сервисы приложения
│   │   └── AIDecisionServiceImpl
│   ├── session/                     # Сервисы сессий
│   │   └── TestSessionAppService
│   └── testgen/                     # Сервисы генерации тестов
│       └── TestGenerationAppService
│
├── domain/                          # Domain слой (бизнес-логика)
│   ├── ai/                          # AI домен
│   │   ├── AIDecisionService        # Интерфейс
│   │   ├── AIPromptBuilder          # Интерфейс
│   │   └── AIDecisionParser         # Интерфейс
│   ├── observation/                 # Наблюдения
│   │   ├── ObservationCaptureService
│   │   ├── DOMExtractor
│   │   └── ElementScanner
│   ├── webdriver/                   # WebDriver
│   │   └── WebDriverProvider
│   ├── action/                      # Действия
│   │   ├── TestAgentAction
│   │   └── ActionFactory
│   ├── model/                       # Модели данных
│   │   ├── Decision
│   │   ├── Observation
│   │   ├── InteractiveElement
│   │   ├── ActionHistory
│   │   ├── Issue
│   │   └── ElementBounds
│   └── session/                     # Сессии
│       └── TestExecutionService
│
└── infrastructure/                  # Infrastructure слой
    ├── ai/                          # AI реализации
    │   ├── AIDecisionParserImpl
    │   ├── AIPromptBuilderImpl
    │   └── ChatClientAdapter
    ├── observation/                 # Наблюдения реализации
    │   ├── ObservationCaptureServiceImpl
    │   ├── DOMExtractorImpl
    │   ├── ElementScannerImpl
    │   └── ScreenshotService
    ├── webdriver/                   # WebDriver реализации
    │   ├── WebDriverProviderImpl
    │   └── SeleniumWebDriverManager
    ├── action/                      # Действия реализации
    │   ├── ActionFactoryImpl
    │   └── actions/                 # Конкретные действия
    │       ├── ClickAction
    │       ├── TypeAction
    │       └── ...
    └── persistence/                 # Персистентность
        ├── repositories/
        └── entities/
```

## Примененные принципы SOLID

### 1. SRP (Single Responsibility Principle)

**Было:**
- `DecisionEngineService` - 1300+ строк, смешивает промпты, парсинг, валидацию, форматирование
- `ObservationService` - 1360+ строк, смешивает скриншоты, DOM, сканирование, оптимизацию

**Стало:**
```java
// Разделенные сервисы
interface AIDecisionService    // Принятие решений
interface AIPromptBuilder      // Построение промптов
interface AIDecisionParser     // Парсинг ответов
interface DecisionValidator    // Валидация решений

interface ObservationCaptureService  // Координация наблюдений
interface ScreenshotService          // Только скриншоты
interface DOMExtractor               // Только DOM
interface ElementScanner             // Только элементы
```

### 2. OCP (Open/Closed Principle)

**Было:**
```java
// Жесткая регистрация в конструкторе
public ActionRegistryService() {
    registerActions();  // Hardcoded
}
```

**Стало:**
```java
// Динамическая регистрация через Spring DI
@Component
public class ActionFactoryImpl implements ActionFactory {
    private final Map<String, TestAgentAction> actions = new HashMap<>();
    
    // Авто-регистрация всех бинков TestAgentAction
    public ActionFactoryImpl(List<TestAgentAction> actionBeans) {
        for (TestAgentAction action : actionBeans) {
            register(action.getType(), action);
        }
    }
}
```

### 3. LSP (Liskov Substitution Principle)

**Было:**
```java
public interface TestAgentAction {
    default void configure(Decision decision) { }  // Пустая реализация
}
```

**Стало:**
```java
public interface TestAgentAction {
    AgentAction execute(WebDriver driver);
    void configure(Decision decision);  // Обязательный метод
    String getDescription();
    String getType();
}
```

### 4. ISP (Interface Segregation Principle)

**Было:**
```java
// Один большой интерфейс
public interface ObservationService {
    String captureScreenshot();
    String extractDOM();
    List<Element> scanElements();
    void init();
    void destroy();
}
```

**Стало:**
```java
// Разделенные интерфейсы
public interface ScreenshotService {
    String captureScreenshot();
}

public interface DOMExtractor {
    String extractDOM();
}

public interface ElementScanner {
    List<Element> scanElements();
}
```

### 5. DIP (Dependency Inversion Principle)

**Было:**
```java
@Service
public class TestAgentService {
    @Autowired
    private ObservationService observationService;  // Конкретный класс
}
```

**Стало:**
```java
@Service
public class TestAgentAppService {
    private final ObservationCaptureService observationService;
    private final WebDriverProvider driverProvider;
    private final AIDecisionService decisionService;
    
    // Конструкторная инъекция вместо setter
    public TestAgentAppService(
        ObservationCaptureService observationService,
        WebDriverProvider driverProvider,
        AIDecisionService decisionService
    ) {
        this.observationService = observationService;
        this.driverProvider = driverProvider;
        this.decisionService = decisionService;
    }
}
```

## Примененные принципы GRASP

### 1. Information Expert

**Было:** `DecisionEngineService` принимает решения без полного контекста

**Стало:**
```java
public class AIDecisionServiceImpl implements AIDecisionService {
    private final AIPromptBuilder promptBuilder;  // Строит полный контекст
    private final AIDecisionParser parser;        // Парсит с контекстом
    
    public Decision decideNextAction(Observation observation) {
        // AI получает полный контекст через промпт
        String prompt = promptBuilder.buildPrompt(observation);
        return parser.parse(callAI(prompt));
    }
}
```

### 2. Creator

**Было:** Контроллер создает сущности
```java
@PostMapping("/startSession")
public ResponseEntity<String> startSession(@RequestBody StartSessionModel model) {
    TestSession session = new TestSession();  // Создание в контроллере
}
```

**Стало:**
```java
@Component
public class TestSessionFactory {
    public TestSession create(CreateSessionCommand command) {
        return TestSession.builder()
            .id(UUID.randomUUID())
            .targetUrl(command.getUrl())
            .goal(command.getGoal())
            .status(SessionStatus.CREATED)
            .build();
    }
}
```

### 3. Controller

**Было:** `TestSessionService` смешивает координацию и бизнес-логику

**Стало:**
```java
// Application Service - только координация
@Service
public class TestSessionAppService {
    private final TestSessionRepository repository;
    private final TestExecutionService domainService;
    private final TestSessionFactory factory;
    
    public UUID startSession(StartSessionCommand command) {
        TestSession session = factory.create(command);
        repository.save(session);
        domainService.executeAsync(session);  // Делегирование домену
        return session.getId();
    }
}

// Domain Service - бизнес-логика
@Service
public class TestExecutionService {
    public void executeAsync(TestSession session) {
        // Бизнес-логика выполнения теста
    }
}
```

### 4. Low Coupling

**Было:** Высокое зацепление через конкретные классы
```
TestAgentService → ObservationService (конкретный)
                 → DecisionEngineService (конкретный)
                 → SeleniumSupplierService (конкретный)
```

**Стало:** Низкое зацепление через интерфейсы
```
TestAgentAppService → ObservationCaptureService (интерфейс)
                    → AIDecisionService (интерфейс)
                    → WebDriverProvider (интерфейс)
```

### 5. High Cohesion

**Было:** `ObservationService` с низким сцеплением (скриншоты + DOM + элементы + кэш)

**Стало:** Высокое сцепление
```
ObservationCaptureService  ← Координирует
    ├── ScreenshotService  ← Только скриншоты
    ├── DOMExtractor       ← Только DOM
    └── ElementScanner     ← Только элементы
```

### 6. Polymorphism

**Было:** Switch/if-else для действий
```java
private String getDefaultReason(Decision decision) {
    return switch (decision.getAction()) {
        case "CLICK" -> "Кликаю...";
        case "TYPE" -> "Ввожу...";
        // ...
    };
}
```

**Стало:** Полиморфные действия
```java
public abstract class BaseAgentAction implements TestAgentAction {
    @Override
    public String getDescription() {
        return generateDescription();  // Переопределяется в подклассах
    }
    
    protected abstract String generateDescription();
}

public class ClickAction extends BaseAgentAction {
    @Override
    protected String generateDescription() {
        return "Клик на элемент: " + target;
    }
}
```

## План миграции

### Этап 1: Создание инфраструктуры (выполнено)
- [x] Создать интерфейсы доменного слоя
- [x] Создать модели данных
- [x] Создать реализации парсера и промпт билдера

### Этап 2: Рефакторинг сервисов
- [ ] Рефакторинг `ObservationService` → разделение на компоненты
- [ ] Рефакторинг `ActionRegistryService` → `ActionFactoryImpl`
- [ ] Рефакторинг `BaseAgentAction` → вынос утилит

### Этап 3: Устранение дублирования
- [ ] Объединить `TestAgentService` и `TestSessionService.test()`
- [ ] Создать `TestExecutionService` в domain слое

### Этап 4: Внедрение конструкторной инъекции
- [ ] Заменить `@Setter(onMethod_ = @Autowired)` на конструкторы
- [ ] Использовать `@RequiredArgsConstructor`

### Этап 5: Рефакторинг Explore действий
- [ ] Вынести логику `ExploreFormsAction` в `FormTestingService`
- [ ] Вынести логику `ExploreMenuAction` в `MenuExplorationService`

### Этап 6: Тестирование и документация
- [ ] Написать unit тесты для новых сервисов
- [ ] Обновить документацию архитектуры

## Преимущества новой архитектуры

1. **Тестируемость** - каждый сервис можно тестировать изолированно
2. **Расширяемость** - новые действия регистрируются автоматически
3. **Поддерживаемость** - каждый класс имеет одну ответственность
4. **Понятность** - явное разделение слоев и зависимостей
5. **Гибкость** - легко заменить реализацию (например, AI провайдер)
