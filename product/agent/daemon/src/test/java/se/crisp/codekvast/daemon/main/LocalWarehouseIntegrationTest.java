package se.crisp.codekvast.daemon.main;

import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import se.crisp.codekvast.daemon.CodekvastDaemon;

import java.lang.annotation.*;

/**
 * Meta annotation for integration tests against an embedded CodekvastDaemon application that uses a local warehouse as data processing
 * strategy.
 *
 * @author olle.hallin@crisp.se
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringApplicationConfiguration(classes = CodekvastDaemon.class)
@IntegrationTest({
        "spring.profiles.active=localWarehouse",
        "spring.datasource.url=jdbc:h2:mem:integrationTest",
        "codekvast.apiAccessID=apiAccessID",
        "codekvast.apiAccessSecret=apiAccessSecret",
        "codekvast.serverUri=serverUri",
        "codekvast.dataPath=dataPath",
        "codekvast.dataProcessingIntervalSeconds=600",
        "codekvast.exportFile=/tmp/codekvast-export.zip"
})
public @interface LocalWarehouseIntegrationTest {
}
