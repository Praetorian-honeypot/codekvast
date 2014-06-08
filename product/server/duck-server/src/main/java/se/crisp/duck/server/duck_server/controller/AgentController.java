package se.crisp.duck.server.duck_server.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import se.crisp.duck.server.agent.AgentRestEndpoints;
import se.crisp.duck.server.agent.model.v1.SignatureData;
import se.crisp.duck.server.duck_server.db.AgentService;

import javax.inject.Inject;

/**
 * @author Olle Hallin
 */
@RestController
@Slf4j
public class AgentController {

    private final AgentService agentService;

    @Inject
    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @RequestMapping(value = AgentRestEndpoints.UPLOAD_SIGNATURES_V1,
                    method = RequestMethod.POST,
                    consumes = MediaType.APPLICATION_JSON_VALUE)
    public void receiveSignaturesV1(@RequestBody SignatureData data) {
        log.info("Received {}", data);
        agentService.storeSignatureData(data);
    }
}
