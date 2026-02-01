package ru.sbrf.uddk.ai.testing.ui.view;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.Setter;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import ru.sbrf.uddk.ai.testing.entity.TestSession;
import ru.sbrf.uddk.ai.testing.entity.consts.TestGoal;
import ru.sbrf.uddk.ai.testing.interfaces.TestAgentAction;
import ru.sbrf.uddk.ai.testing.model.AgentObservation;
import ru.sbrf.uddk.ai.testing.service.DecisionEngineService;
import ru.sbrf.uddk.ai.testing.service.ObservationService;
import ru.sbrf.uddk.ai.testing.service.SeleniumSupplierService;
import ru.sbrf.uddk.ai.testing.service.actions.BaseAgentAction;
import ru.sbrf.uddk.ai.testing.service.actions.CompleteAction;
import ru.sbrf.uddk.ai.testing.service.actions.ReportIssueAction;
import ru.sbrf.uddk.ai.testing.ui.components.ScrollView;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@Route("agent-testing")
@PageTitle("Autonomous Testing Agent")
public class MainView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(MainView.class);
    @Setter(onMethod_ = @Autowired)
    private ObservationService observationService;

    @Setter(onMethod_ = @Autowired)
    private SeleniumSupplierService seleniumSupplierService;

    @Setter(onMethod_ = @Autowired)
    private DecisionEngineService decisionEngineService;

    private TextArea promptField;
    private TextField urlField;
    private VerticalLayout logContainer;
    private Image screenshotImage;
    private Button startButton;
    private Button stopButton;
    private Grid<AgentAction> actionGrid;
    private boolean started;

    public MainView() {

        initUI();
        setupEventListeners();
    }

    private void initUI() {
        setSizeFull();
        setPadding(false);
        setSpacing(true);

        H1 title = new H1("🤖 Autonomous Testing Agent");

        VerticalLayout promptSection = createPromptSection();
        HorizontalLayout controls = createControls();
        HorizontalLayout contentArea = createContentArea();

        add(title, promptSection, controls, contentArea);
        expand(contentArea);
    }

    private VerticalLayout createPromptSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(true);
        section.getStyle().set("background", "var(--lumo-contrast-5pct)");
        section.setWidth("100%");

        H3 sectionTitle = new H3("Testing Instructions");
        urlField = new TextField();
        urlField.setWidth("100%");
        urlField.setPlaceholder("https://the-internet.herokuapp.com/login");
        urlField.setValue("https://the-internet.herokuapp.com/login");
        urlField.setMaxLength(1000);
        urlField.setLabel("URL");

        promptField = new TextArea();
        promptField.setWidth("100%");
        promptField.setPlaceholder("Example: Test login functionality with invalid credentials");
        promptField.setMaxLength(1000);
        promptField.setHeight("120px");
        promptField.setLabel("Инструкции");

        section.add(sectionTitle, urlField, promptField);
        return section;
    }

    private HorizontalLayout createControls() {
        startButton = new Button("Start Testing", VaadinIcon.PLAY.create());
        startButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        stopButton = new Button("Stop", VaadinIcon.STOP.create());
        stopButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        stopButton.setEnabled(false);

        Button clearButton = new Button("Clear Log", VaadinIcon.TRASH.create());
        clearButton.addClickListener(e -> clearLog());

        HorizontalLayout controls = new HorizontalLayout(startButton, stopButton, clearButton);
        controls.setSpacing(true);
        return controls;
    }


    private HorizontalLayout createContentArea() {
        HorizontalLayout content = new HorizontalLayout();
        content.setSizeFull();
        content.setSpacing(true);

        // Left side: Screenshot view
        VerticalLayout screenshotSection = new VerticalLayout();
        screenshotSection.setWidth("50%");
        screenshotSection.setPadding(false);

        H3 screenshotTitle = new H3("Browser Screenshot");
        screenshotImage = new Image();
        screenshotImage.setWidth("100%");
        screenshotImage.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
        screenshotImage.setAlt("Current browser state");

        screenshotSection.add(screenshotTitle, screenshotImage);
        screenshotSection.expand(screenshotImage);

        // Right side: Action log
        VerticalLayout logSection = new VerticalLayout();
        logSection.setWidth("40%");
        logSection.setPadding(false);

        H3 logTitle = new H3("Agent Actions & Results");

        logContainer = new VerticalLayout();
        logContainer.setPadding(true);
        logContainer.setSpacing(true);
        logContainer.setHeight("600px");

        ScrollView scrollView = new ScrollView(logContainer);
        scrollView.setSizeFull();

        // Action grid for detailed view
        actionGrid = new Grid<>(AgentAction.class, false);
        actionGrid.addColumn(AgentAction::getTimestamp).setHeader("Time").setWidth("100px");
        actionGrid.addColumn(AgentAction::getActionType).setHeader("Action").setWidth("200px");
        actionGrid.addColumn(AgentAction::getResult).setHeader("Result").setWidth("300px");
        actionGrid.addColumn(AgentAction::getResult).setHeader("Status").setWidth("100px");
        actionGrid.setHeight("300px");


        logSection.add(logTitle, scrollView, new H4("Detailed Actions"), actionGrid);

        content.add(screenshotSection, logSection);
        return content;
    }

    private void setupEventListeners() {
        startButton.addClickListener(e -> startTesting());
        stopButton.addClickListener(e -> stopTesting());

        // Real-time prompt validation
        promptField.addValueChangeListener(e -> {
            String prompt = e.getValue();
//            if (filterService.containsRestrictedContent(prompt)) {
//                promptField.setInvalid(true);
//                promptField.setErrorMessage("This prompt contains restricted requests. Please focus on testing instructions only.");
//            } else {
//                promptField.setInvalid(false);
//                promptField.setErrorMessage(null);
//            }
        });
    }

    private void clearLog() {

    }

    private void startTesting() {
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        started = true;

        UI ui = UI.getCurrent();

        CompletableFuture.runAsync(() -> {
            TestSession testSession = new TestSession();
            testSession.setGoal(TestGoal.EXPLORATORY);
//        testSession.setTargetUrl("https://rzd.ru");
            testSession.setTargetUrl(urlField.getValue());
            testSession.setId(UUID.randomUUID());
            testSession.setDescription(promptField.getValue());

            test(ui, testSession);
        });
    }

    private void stopTesting() {
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        started = false;
    }

    private void test(UI ui, TestSession testSession) {
        WebDriver webDriver = seleniumSupplierService.get();
        webDriver.get(testSession.getTargetUrl());

        ListDataProvider<AgentAction> actions = new ListDataProvider<>(testSession.getActions());

        ui.access(() -> {
            actionGrid.setDataProvider(actions);
        });

        while (started) {
            try {
                LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(2));
                AgentObservation agentObservation = observationService.captureObservation(webDriver, testSession);
                ui.access(() -> {
                    screenshotImage.setSrc(agentObservation.getScreenshot());
                });
                TestAgentAction agentAction = decisionEngineService.decideNextAction(agentObservation);
                ui.access(() -> {
                    NativeLabel label = new NativeLabel();
                    label.setText(((BaseAgentAction) agentAction).getReason());
                    logContainer.add(label);
                });
                AgentAction ac = agentAction.execute(webDriver);
                ui.access(() -> {
                    actions.getItems().add(ac);
                    actions.refreshItem(ac);
                });
                testSession.addAction(ac);
                if (agentAction instanceof CompleteAction) {
                    started = false;
                }
                if (agentAction instanceof ReportIssueAction) {
                    started = false;
                }
            } catch (Exception e) {
                log.error("error", e);
                started = false;
            }
        }

        ui.access(() -> {
            stopTesting();
        });

        webDriver.close();
    }

}
