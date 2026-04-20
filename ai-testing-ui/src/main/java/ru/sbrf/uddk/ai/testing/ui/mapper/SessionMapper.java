package ru.sbrf.uddk.ai.testing.ui.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import ru.sbrf.uddk.ai.testing.entity.DiscoveredIssue;
import ru.sbrf.uddk.ai.testing.entity.TestSession;
import ru.sbrf.uddk.ai.testing.ui.model.dto.ActionDto;
import ru.sbrf.uddk.ai.testing.ui.model.dto.IssueDto;
import ru.sbrf.uddk.ai.testing.ui.model.dto.TestSessionDto;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MapStruct маппер для преобразования Entity -> DTO
 */
@Mapper(componentModel = "spring")
public interface SessionMapper {

    @Mapping(target = "goal", source = "goal", qualifiedByName = "enumToString")
    @Mapping(target = "status", source = "status", qualifiedByName = "enumToString")
    @Mapping(target = "actions", source = "actions")
    @Mapping(target = "issues", source = "discoveredIssues")
    TestSessionDto toDto(TestSession session);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "actionType", source = "actionType")
    @Mapping(target = "targetSelector", source = "targetSelector")
    @Mapping(target = "targetXpath", source = "targetXpath")
    @Mapping(target = "targetCss", source = "targetCss")
    @Mapping(target = "inputValue", source = "inputValue")
    @Mapping(target = "reason", source = "reason")
    @Mapping(target = "expectedOutcome", source = "expectedOutcome")
    @Mapping(target = "stepDescription", source = "stepDescription")
    @Mapping(target = "isAssertion", source = "isAssertion")
    @Mapping(target = "assertionType", source = "assertionType")
    @Mapping(target = "assertionExpected", source = "assertionExpected")
    @Mapping(target = "success", source = "result.success")
    @Mapping(target = "resultMessage", source = "result.message")
    @Mapping(target = "timestamp", source = "timestamp")
    @Mapping(target = "executionTimeMs", source = "executionTimeMs")
    @Mapping(target = "screenshotBefore", source = "screenshotBefore")
    @Mapping(target = "screenshotAfter", source = "screenshotAfter")
    ActionDto toDto(AgentAction action);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "type", source = "type", qualifiedByName = "enumToString")
    @Mapping(target = "severity", source = "severity", qualifiedByName = "enumToString")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "stepsToReproduce", ignore = true)
    IssueDto toDto(DiscoveredIssue issue);

    default List<TestSessionDto> toDtoList(List<TestSession> sessions) {
        if (sessions == null) {
            return Collections.emptyList();
        }
        return sessions.stream().map(this::toDto).collect(Collectors.toList());
    }

    default List<ActionDto> actionListToDtoList(List<AgentAction> actions) {
        if (actions == null) {
            return Collections.emptyList();
        }
        return actions.stream().map(this::toDto).collect(Collectors.toList());
    }

    default List<IssueDto> issueListToDtoList(List<DiscoveredIssue> issues) {
        if (issues == null) {
            return Collections.emptyList();
        }
        return issues.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Named("enumToString")
    default String enumToString(Enum<?> enumValue) {
        return enumValue != null ? enumValue.name() : null;
    }
}
