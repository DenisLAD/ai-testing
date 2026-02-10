package ru.sbrf.uddk.ai.testing.ui.mapper;

import org.mapstruct.Mapper;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import ru.sbrf.uddk.ai.testing.entity.DiscoveredIssue;
import ru.sbrf.uddk.ai.testing.entity.TestSession;
import ru.sbrf.uddk.ai.testing.ui.model.RestAgentAction;
import ru.sbrf.uddk.ai.testing.ui.model.RestDiscoveredIssue;
import ru.sbrf.uddk.ai.testing.ui.model.RestTestSession;

@Mapper(componentModel = "spring")
public interface SessionMapper {
    RestTestSession map(TestSession source);

    RestAgentAction map(AgentAction action);

    RestDiscoveredIssue map(DiscoveredIssue discoveredIssue);
}
