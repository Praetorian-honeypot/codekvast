package se.crisp.codekvast.server.codekvast_server.controller;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import lombok.Value;
import lombok.experimental.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;
import se.crisp.codekvast.server.codekvast_server.event.internal.ApplicationCreatedEvent;

import javax.inject.Inject;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Responsible for maintaining one {@link FilterValues} object for each active web socket user.
 * <p/>
 * Upon connection of a new web socket user, an initial FilterValues object is built by querying other services.
 * After that, the object is maintained by subscribing to internal messages.
 * <p/>
 * Each time a FilterValues object is updated, the relevant user is notified via a STOMP message to that user..
 *
 * @author Olle Hallin
 */
@Controller
@Slf4j
public class FilterHandler extends AbstractMessageHandler {

    private final Map<String, FilterValues> usernameToFilterValues = new ConcurrentHashMap<>();

    @Inject
    public FilterHandler(EventBus eventBus, SimpMessagingTemplate messagingTemplate) {
        super(eventBus, messagingTemplate);
    }

    @Subscribe
    @AllowConcurrentEvents
    public void onUserConnectedEvent(UserHandler.UserConnectedEvent event) {
        usernameToFilterValues.put(event.getUsername(), createInitialFilterValues(event.getUsername()));
    }

    @Subscribe
    @AllowConcurrentEvents
    public void onUserDisconnectedEvent(UserHandler.UserDisconnectedEvent event) {
        usernameToFilterValues.remove(event.getUsername());
    }

    /**
     * The JavaScript layer starts a STOMP subscription for filter values.
     *
     * @param principal The identity of the authenticated user.
     * @return The current FilterValues that the user shall use.
     */
    @SubscribeMapping("/filterValues")
    public FilterValues subscribeFilterValues(Principal principal) {
        String username = principal.getName();
        log.debug("'{}' is subscribing to filterValues", username);

        return usernameToFilterValues.get(username);
    }

    /**
     * A new application has been created. Identify the relevant active users and update their filter values.
     */
    @Subscribe
    public void onApplicationCreated(ApplicationCreatedEvent event) {
        log.debug("Handling {}", event);
        for (String username : event.getUsernames()) {
            FilterValues fv = usernameToFilterValues.get(username);
            if (fv != null) {
                String applicationName = event.getApplication().getName();
                log.debug("Adding application '{}' to filter values for {}", applicationName, username);

                fv.getApplications().add(applicationName);
                messagingTemplate.convertAndSendToUser(username, "/queue/filterValues", fv);
            }
        }
    }

    /**
     * A value object containing everything that can be specified as filters in the web layer. It is sent as a STOMP message from the server
     * to the web layer as soon as there is a change in filter values.
     *
     * @author Olle Hallin
     */
    @Value
    @Builder
    public static class FilterValues {
        private Collection<String> applications;
        private Collection<String> versions;
        private Collection<String> tags;
    }

    //---- Fake stuff below ----------------------------------------

    private FilterValues createInitialFilterValues(String username) {
        // TODO: implement
        Collection<String> applications = randomStrings(username + "-app", 3);
        Collection<String> versions = randomStrings(username + "-v", 10);
        Collection<String> tags = randomStrings(username + "-tag", 10);

        return FilterValues.builder()
                           .applications(applications)
                           .versions(versions)
                           .tags(tags)
                           .build();
    }

    /**
     * Fake way to see that STOMP updates work.
     */
    // @Scheduled(fixedRate = 5000L)
    public void sendFilterValuesToActiveUsers() {
        for (String username : usernameToFilterValues.keySet()) {
            log.debug("Sending filter values to '{}'", username);

            messagingTemplate.convertAndSendToUser(username, "/queue/filterValues", createInitialFilterValues(username));
        }
    }

    private Random random = new Random();

    private Collection<String> randomStrings(String prefix, int count) {
        Collection<String> result = new ArrayList<>();
        int c = (int) (count + random.nextGaussian() * count * 0.2d);

        for (int i = 0; i < c; i++) {
            String value = prefix + i;
            if (i >= 2) {
                // Don't append random values to the first couple of values, so that we can see that it is possible to select something
                // in the web page and that it remains selected when new filter values arrive.
                value += "-" + random.nextInt(100);
            }
            result.add(value);
        }
        return result;
    }

}