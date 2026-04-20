package ru.sbrf.uddk.ai.testing.infrastructure.action;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.sbrf.uddk.ai.testing.domain.action.ActionFactory;
import ru.sbrf.uddk.ai.testing.domain.action.TestAgentAction;
import ru.sbrf.uddk.ai.testing.domain.model.Decision;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Фабрика действий
 * Реализация с автоматической регистрацией через Spring
 */
@Slf4j
@Component
public class ActionFactoryImpl implements ActionFactory {

    private final Map<String, TestAgentAction> actionCreators = new HashMap<>();

    /**
     * Автоматическая регистрация всех бинков TestAgentAction
     */
    public ActionFactoryImpl(List<TestAgentAction> actions) {
        for (TestAgentAction action : actions) {
            register(action.getType(), action);
        }
        log.info("Registered {} actions: {}", actionCreators.size(), actionCreators.keySet());
    }

    @Override
    public void register(String type, TestAgentAction action) {
        if (type == null || action == null) {
            log.warn("Cannot register null action");
            return;
        }
        actionCreators.put(type.toUpperCase(), action);
        log.debug("Registered action: {}", type);
    }

    @Override
    public TestAgentAction create(Decision decision) {
        if (decision == null || decision.getAction() == null) {
            throw new IllegalArgumentException("Decision cannot be null");
        }

        String actionType = decision.getAction().toUpperCase();
        TestAgentAction action = actionCreators.get(actionType);

        if (action == null) {
            log.warn("Unknown action type: {}. Available: {}", actionType, actionCreators.keySet());
            throw new IllegalArgumentException("Unknown action type: " + actionType);
        }

        action.configure(decision);
        return action;
    }

    @Override
    public boolean hasAction(String type) {
        return actionCreators.containsKey(type.toUpperCase());
    }

    @Override
    public Set<String> getRegisteredTypes() {
        return Set.copyOf(actionCreators.keySet());
    }

    public int getRegisteredCount() {
        return actionCreators.size();
    }
}
