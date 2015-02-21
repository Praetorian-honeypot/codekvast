package se.crisp.codekvast.server.codekvast_server.model.event.display;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.Collection;

/**
 * An event published every time collector data is received from any agent.
 *
 * @author olle.hallin@crisp.se
 */
@Value
@Builder
public class CollectorStatusMessage {
    /**
     * Which usernames should be broadcast this message (if logged in)?
     */
    @NonNull
    Collection<String> usernames;

    /**
     * The collectors to display.
     */
    @NonNull
    Collection<CollectorDisplay> collectors;
}
