package se.crisp.codekvast.server.codekvast_server.config;

import org.springframework.boot.autoconfigure.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * @author Olle Hallin
 */
@Import(RedisAutoConfiguration.class)
@EnableRedisHttpSession
public class RedisHttpSessionConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public RedisServer redisServer() throws IOException, URISyntaxException {
        return new RedisServer();
    }

    @Bean
    public JedisConnectionFactory redisConnectionFactory(RedisServer redisServer) {
        JedisConnectionFactory connectionFactory = new JedisConnectionFactory();
        connectionFactory.setPort(redisServer.getPort());
        return connectionFactory;
    }

}

