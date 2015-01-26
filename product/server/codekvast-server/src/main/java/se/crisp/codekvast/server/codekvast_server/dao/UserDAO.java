package se.crisp.codekvast.server.codekvast_server.dao;

import org.springframework.dao.DataAccessException;
import se.crisp.codekvast.server.agent_api.model.v1.InvocationEntry;
import se.crisp.codekvast.server.codekvast_server.exception.UndefinedApplicationException;
import se.crisp.codekvast.server.codekvast_server.exception.UndefinedCustomerException;
import se.crisp.codekvast.server.codekvast_server.model.AppId;
import se.crisp.codekvast.server.codekvast_server.model.Application;
import se.crisp.codekvast.server.codekvast_server.model.Role;

import java.util.Collection;

/**
 * @author Olle Hallin
 */
public interface UserDAO {

    /**
     * Translates a username to a customer ID
     *
     * @param username A real user's login name or an agent's agentAccessID
     * @return The username for that user or agent.
     * @throws UndefinedCustomerException
     */
    long usernameToCustomerId(String username) throws UndefinedCustomerException;

    /**
     * Retrieve an application ID. If not found, a new row is inserted into APPLICATIONS and an ApplicationCreatedEvent is posted on the
     * event bus.
     */
    long getAppId(long customerId, String appName) throws UndefinedApplicationException;

    AppId getAppIdByJvmFingerprint(String jvmFingerprint);

    int countUsersByUsername(String username);

    int countUsersByEmailAddress(String emailAddress);

    int countCustomersByNameLc(String customerName);

    long createUser(String fullName, String username, String emailAddress, String plaintextPassword, Role... roles)
            throws DataAccessException;

    void createCustomerWithPrimaryContact(String customerName, long userId) throws DataAccessException;

    Collection<InvocationEntry> getSignatures(String username);

    /**
     * Retrieve all applications for a certain username
     *
     * @param username The username
     * @return All applications that a certain user has rights to view. Does never return null.
     */
    Collection<Application> getApplications(String username);
}
