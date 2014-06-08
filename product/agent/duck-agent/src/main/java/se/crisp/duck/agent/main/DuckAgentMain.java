package se.crisp.duck.agent.main;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.scheduling.annotation.EnableScheduling;
import se.crisp.duck.agent.main.spring.AgentConfigPropertySource;
import se.crisp.duck.agent.util.AgentConfig;
import se.crisp.duck.server.agent.ServerDelegate;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * @author Olle Hallin
 */
@Configuration
@EnableAutoConfiguration
@EnableScheduling
@ComponentScan("se.crisp.duck")
public class DuckAgentMain {

    private static URI agentConfigLocation;

    public static void main(String[] args) throws IOException, URISyntaxException {
        // Reuse the same duck.properties as is used by the sensor...
        DuckAgentMain.agentConfigLocation = getAgentConfigLocation(args);

        SpringApplication application = new SpringApplication(DuckAgentMain.class);
        application.setDefaultProperties(getDefaultProperties());
        application.run(args);
    }

    private static URI getAgentConfigLocation(String[] args) throws URISyntaxException {
        return args == null || args.length < 1 ? new URI("classpath:/duck.properties") : new File(args[0]).toURI();
    }

    private static Properties getDefaultProperties() {
        Properties result = new Properties();
        result.setProperty("tmpDir", System.getProperty("java.io.tmpdir"));
        return result;
    }

    @Bean
    public static AgentConfig agentConfig(ConfigurableEnvironment environment) {
        AgentConfig agentConfig = AgentConfig.parseConfigFile(agentConfigLocation);

        // Make the AgentConfig object usable in SpringEL expressions with duck. as prefix...
        environment.getPropertySources().addLast(new AgentConfigPropertySource(agentConfig, "duck."));
        return agentConfig;
    }

    @Bean
    public static ServerDelegate.Config serverDelegateConfig(AgentConfig agentConfig) {
        return ServerDelegate.Config.builder()
                                    .customerName(agentConfig.getCustomerName())
                                    .appName(agentConfig.getAppName())
                                    .environment(agentConfig.getEnvironment())
                                    .serverUri(agentConfig.getServerUri())
                                    .build();
    }

}
