package se.crisp.codekvast.server.codekvast_server.model.event.rest;

/**
 * Different method usage scopes.
 *
 * @author olle.hallin@crisp.se
 */
public enum MethodUsageScope {
    DEAD, POSSIBLY_DEAD, LIVE;

    public String toDisplayString() {
        return name().toLowerCase().replace("_", " ");
    }
}
