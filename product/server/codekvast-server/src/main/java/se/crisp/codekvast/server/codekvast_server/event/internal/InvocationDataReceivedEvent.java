package se.crisp.codekvast.server.codekvast_server.event.internal;

import lombok.Value;
import se.crisp.codekvast.server.agent_api.model.v1.InvocationEntry;
import se.crisp.codekvast.server.codekvast_server.model.AppId;

import java.util.Collection;

/**
 * @author Olle Hallin
 */
@Value
public class InvocationDataReceivedEvent {
    private final AppId appId;
    private final Collection<InvocationEntry> invocationEntries;

    @Override
    public String toString() {
        return "InvocationDataReceivedEvent(appId=" + appId + ", invocationEntries.size=" + invocationEntries
                .size() + ")";
    }
}
