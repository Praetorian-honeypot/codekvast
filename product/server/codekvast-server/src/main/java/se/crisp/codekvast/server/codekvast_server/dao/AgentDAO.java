package se.crisp.codekvast.server.codekvast_server.dao;

import se.crisp.codekvast.server.agent_api.model.v1.InvocationData;
import se.crisp.codekvast.server.agent_api.model.v1.InvocationEntry;
import se.crisp.codekvast.server.agent_api.model.v1.JvmData;
import se.crisp.codekvast.server.codekvast_server.exception.CodekvastException;

import java.util.Collection;

/**
 * A data access object for things related to the agent API.
 *
 * @author Olle Hallin
 */
public interface AgentDAO {

    /**
     * @param apiAccessID The identity of the agent (in reality a Spring Security username)
     * @param jvmData
     * @throws CodekvastException
     */
    void storeJvmData(String apiAccessID, JvmData jvmData) throws CodekvastException;

    /**
     * Stores invocation data in the database.
     *
     * @param invocationData The invocation data to store.
     * @return The actually stored or updated invocation entries.
     */
    Collection<InvocationEntry> storeInvocationData(InvocationData invocationData);
}
