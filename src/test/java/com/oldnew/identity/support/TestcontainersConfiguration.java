package com.oldnew.identity.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A real MongoDB in Docker for integration and BDD tests. Spring wires the URI automatically.
 * <p>
 * Default image is mongo:7.0: MongoDB 8.x refuses to start on Linux kernels 6.19 to 7.0.13 (SERVER-121912),
 * which is what Docker Desktop's VM runs as of Oct 2026. Once `docker run --rm alpine uname -r` shows 7.0.14
 * or newer, run with -Dtest.mongo.image=mongo:8.0 (or change the default) to match production.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MongoDBContainer mongoDbContainer() {
        return new MongoDBContainer(DockerImageName.parse(System.getProperty("test.mongo.image", "mongo:7.0")));
    }
}
