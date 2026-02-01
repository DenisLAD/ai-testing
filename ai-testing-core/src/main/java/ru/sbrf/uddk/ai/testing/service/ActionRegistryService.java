package ru.sbrf.uddk.ai.testing.service;

import ru.sbrf.uddk.ai.testing.interfaces.TestAgentAction;
import ru.sbrf.uddk.ai.testing.service.actions.AssertPresenceAction;
import ru.sbrf.uddk.ai.testing.service.actions.AssertTextAction;
import ru.sbrf.uddk.ai.testing.service.actions.BaseAgentAction;
import ru.sbrf.uddk.ai.testing.service.actions.ClickAction;
import ru.sbrf.uddk.ai.testing.service.actions.CompleteAction;
import ru.sbrf.uddk.ai.testing.service.actions.ExploreFormsAction;
import ru.sbrf.uddk.ai.testing.service.actions.ExploreMenuAction;
import ru.sbrf.uddk.ai.testing.service.actions.NavigateBackAction;
import ru.sbrf.uddk.ai.testing.service.actions.NavigateForwardAction;
import ru.sbrf.uddk.ai.testing.service.actions.NavigateToAction;
import ru.sbrf.uddk.ai.testing.service.actions.RefreshAction;
import ru.sbrf.uddk.ai.testing.service.actions.ReportIssueAction;
import ru.sbrf.uddk.ai.testing.service.actions.ScrollDownAction;
import ru.sbrf.uddk.ai.testing.service.actions.ScrollUpAction;
import ru.sbrf.uddk.ai.testing.service.actions.TestValidationAction;
import ru.sbrf.uddk.ai.testing.service.actions.TypeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Service
@Slf4j
public class ActionRegistryService {
    private final Map<String, Supplier<BaseAgentAction>> actionCreators = new HashMap<>();

    public ActionRegistryService() {
        registerActions();
    }

    private void registerActions() {
        actionCreators.put("CLICK", ClickAction::new);
        actionCreators.put("TYPE", TypeAction::new);
        actionCreators.put("NAVIGATE_BACK", NavigateBackAction::new);
        actionCreators.put("NAVIGATE_FORWARD", NavigateForwardAction::new);
        actionCreators.put("NAVIGATE_TO", NavigateToAction::new);
        actionCreators.put("ASSERT_PRESENCE", AssertPresenceAction::new);
        actionCreators.put("ASSERT_TEXT", AssertTextAction::new);
        actionCreators.put("SCROLL_UP", ScrollUpAction::new);
        actionCreators.put("SCROLL_DOWN", ScrollDownAction::new);
        actionCreators.put("REFRESH", RefreshAction::new);
        actionCreators.put("EXPLORE_MENU", ExploreMenuAction::new);
        actionCreators.put("EXPLORE_FORMS", ExploreFormsAction::new);
        actionCreators.put("TEST_VALIDATION", TestValidationAction::new);
        actionCreators.put("REPORT_ISSUE", ReportIssueAction::new);
        actionCreators.put("COMPLETE", CompleteAction::new);
    }

    public TestAgentAction createAction(DecisionEngineService.Decision decision) {
        Supplier<BaseAgentAction> creator = actionCreators.get(decision.getAction());
        if (creator == null) {
            log.warn("Unknown action type: {}, using RefreshAction", decision.getAction());
            creator = RefreshAction::new;
        }

        BaseAgentAction action = creator.get();
        action.configure(decision);
        return action;
    }

}
