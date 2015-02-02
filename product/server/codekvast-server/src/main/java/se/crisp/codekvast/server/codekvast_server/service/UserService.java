package se.crisp.codekvast.server.codekvast_server.service;

import se.crisp.codekvast.server.agent_api.model.v1.SignatureEntry;
import se.crisp.codekvast.server.codekvast_server.exception.CodekvastException;
import se.crisp.codekvast.server.codekvast_server.model.Application;

import java.util.Collection;

/**
 * @author Olle Hallin
 */
public interface UserService {
    /**
     * Retrieve all signatures that a certain user has access to.
     *
     * @param username The logged in user's username
     * @return A list of invocation entries. Does never return null.
     */
    Collection<SignatureEntry> getSignatures(String username) throws CodekvastException;

    /**
     * Retrieve all applications that a certain user has access to.
     *
     * @param username The logged in user's username
     * @return A collection of applications. Does never return null.
     */
    Collection<Application> getApplications(String username) throws CodekvastException;

}
