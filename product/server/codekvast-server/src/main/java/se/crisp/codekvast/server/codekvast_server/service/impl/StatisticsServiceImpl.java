package se.crisp.codekvast.server.codekvast_server.service.impl;

import com.google.common.eventbus.EventBus;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import se.crisp.codekvast.server.codekvast_server.config.CodekvastSettings;
import se.crisp.codekvast.server.codekvast_server.dao.AgentDAO;
import se.crisp.codekvast.server.codekvast_server.model.AppId;
import se.crisp.codekvast.server.codekvast_server.service.StatisticsService;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.util.concurrent.*;

import static com.google.common.base.Throwables.getRootCause;

/**
 * Asynchronous service for calculating statistics. It serves two purposes:
 * <ol>
 *     <li>Avoid unnecessary recalculations for the same app if there already is one in progress</li>
 *     <li>Eliminate the risk for database lock conflicts</li>
 * </ol>
 *
 * setting codekvast.statisticsDelayMillis to <= 0 turns off the async behaviour.
 *
 * @author Olle Hallin (qolha), olle.hallin@crisp.se
 */
@Service
@Slf4j
public class StatisticsServiceImpl implements StatisticsService {

    private final AgentDAO agentDAO;
    private final EventBus eventBus;
    private final CodekvastSettings codekvastSettings;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final BlockingQueue<DelayedAppId> queue = new DelayQueue<>();

    @Inject
    public StatisticsServiceImpl(AgentDAO agentDAO, EventBus eventBus, CodekvastSettings codekvastSettings) {
        this.agentDAO = agentDAO;
        this.eventBus = eventBus;
        this.codekvastSettings = codekvastSettings;
    }

    @PostConstruct
    void start() {
        log.debug("Starting");
        executor.execute(new Worker());
    }

    @PreDestroy
    void shutdown() {
        log.debug("Shutting down");
        executor.shutdownNow();
    }

    @Override
    @Synchronized
    public void recalculateApplicationStatistics(AppId appId) {

        if (codekvastSettings.getStatisticsDelayMillis() <= 0L) {
            doTheWork(appId);
            return;
        }

        if (executor.isShutdown()) {
            log.debug("Ignoring statistics request during shutdown");
            return;
        }

        DelayedAppId delayedAppId = new DelayedAppId(appId, codekvastSettings.getStatisticsDelayMillis());

        if (queue.contains(delayedAppId)) {
            log.debug("{} is already queued for statistics", appId);
        } else {
            log.debug("Queueing statistics for {}", appId);
            queue.add(delayedAppId);
        }
    }

    private void doTheWork(AppId appId) {
        log.debug("Recalculating statistics for {}", appId);

        long startedAt = System.currentTimeMillis();
        agentDAO.recalculateApplicationStatistics(appId);
        log.info("Calculated statistics for {} in {} ms", appId, System.currentTimeMillis() - startedAt);

        eventBus.post(agentDAO.createWebSocketMessage(appId.getOrganisationId()));
    }

    @RequiredArgsConstructor
    private class Worker implements Runnable {

        @Override
        public void run() {
            for (; ; ) {
                AppId appId = null;
                try {
                    appId = queue.take().getAppId();

                    doTheWork(appId);
                } catch (InterruptedException ignore) {
                    log.debug("Interrupted");
                    return;
                } catch (Exception e) {
                    log.warn("Cannot calculate statistics for {}: {}", appId, getRootCause(e).toString());
                }
            }
        }
    }

    @EqualsAndHashCode(of = "appId")
    private class DelayedAppId implements Delayed {

        @Getter
        private final AppId appId;
        private final long expiresAtMillis;

        private DelayedAppId(AppId appId, long delayMillis) {
            this.appId = appId;
            expiresAtMillis = System.currentTimeMillis() + delayMillis;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long diff = expiresAtMillis - System.currentTimeMillis();
            return unit.convert(diff, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            if (this.expiresAtMillis < ((DelayedAppId) o).expiresAtMillis) {
                return -1;
            }
            if (this.expiresAtMillis > ((DelayedAppId) o).expiresAtMillis) {
                return 1;
            }
            return 0;
        }
    }
}
