package ru.sbrf.uddk.ai.testing.infrastructure.action;

import ru.sbrf.uddk.ai.testing.domain.action.ActionFactory;
import ru.sbrf.uddk.ai.testing.domain.action.TestAgentAction;
import ru.sbrf.uddk.ai.testing.domain.model.Decision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Фабрика действий на основе Spring
 * Автоматически регистрирует все доступные действия
 */
@Slf4j
@Component
public class ActionFactoryImpl implements ActionFactory {

    private final Map<String, TestAgentAction> actions;

    public ActionFactoryImpl(List<TestAgentAction> actionList) {
        this.actions = actionList.stream()
            .collect(Collectors.toMap(
                TestAgentAction::getType,
                action -> action,
                (existing, replacement) -> {
                    log.warn("Duplicate action type: {}, keeping existing", existing.getType());
                    return existing;
                }
            ));
        
        log.info("Зарегистрировано действий: {}", actions.size());
        actions.keySet().forEach(key -> log.debug("  - {}", key));
    }

    @Override
    public void register(String type, TestAgentAction action) {
        // Не используется при автоматической регистрации
        log.debug("Register action: {} -> {}", type, action.getClass().getSimpleName());
    }

    @Override
    public TestAgentAction create(Decision decision) {
        String actionType = decision.getAction();
        
        TestAgentAction action = Optional.ofNullable(actions.get(actionType))
            .orElseGet(() -> {
                log.warn("Неизвестное действие: {}, используем SCROLL_DOWN", actionType);
                return actions.get("SCROLL_DOWN");
            });

        // Клонируем действие и настраиваем
        action.configure(decision);
        return action;
    }

    @Override
    public boolean hasAction(String type) {
        return actions.containsKey(type);
    }

    @Override
    public Set<String> getRegisteredTypes() {
        return Set.copyOf(actions.keySet());
    }
    
    public List<String> getAvailableActions() {
        return List.copyOf(actions.keySet());
    }
}
