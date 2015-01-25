package se.crisp.codekvast.server.codekvast_server.service;

import se.crisp.codekvast.server.agent_api.model.v1.InvocationEntry;
import se.crisp.codekvast.server.codekvast_server.controller.RegistrationController;
import se.crisp.codekvast.server.codekvast_server.exception.CodekvastException;
import se.crisp.codekvast.server.codekvast_server.model.Application;

import java.util.Collection;

/**
 * @author Olle Hallin
 */
public interface UserService {
    enum UniqueKind {USERNAME, EMAIL_ADDRESS, CUSTOMER_NAME}

    /**
     * Tests whether a name is unique.
     *
     * @param kind What kind of name?
     * @param name The name to test.
     * @return true iff that kind of name is unique.
     */
    boolean isUnique(UniqueKind kind, String name);

    /**
     * Creates a user and customer. Assigns roles ADMIN and USER to the new USER. Inserts the new user as primary contact and ADMIN for the
     * customer.
     *
     * @param data The registration data.
     * @return The id of the created user.
     * @throws CodekvastException If anything fails.
     */
    long registerUserAndCustomer(RegistrationController.RegistrationRequest data) throws CodekvastException;

    /**
     * Retrieve all signatures for a certain customer.
     *
     * @param customerName The name of the customer
     * @return A list of invocation entries. Does never return null.
     */
    Collection<InvocationEntry> getSignatures(String customerName) throws CodekvastException;

    /**
     * Retrieve all applications this user has access to.
     *
     * @param username The logged in user's name
     * @return A collection of applications. Does never return null.
     */
    Collection<Application> getApplications(String username);

    /**
     * Retrieve all usernames that has rights to view data for this customer.
     *
     * @param customerId The primary key for the customer.
     * @return A collection of usernames.
     */
    Collection<String> getUsernamesWithRightsToViewCustomer(String customerName);

}
