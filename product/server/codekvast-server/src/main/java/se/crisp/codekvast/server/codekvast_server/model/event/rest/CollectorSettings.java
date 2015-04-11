package se.crisp.codekvast.server.codekvast_server.model.event.rest;

import lombok.Data;

import java.util.List;

/**
 * A REST message sent from the web layer when saving new collector settings.
 *
 * @author olle.hallin@crisp.se
 */
@Data
public class CollectorSettings {
    private List<CollectorSettingsEntry> collectorSettings;
}
