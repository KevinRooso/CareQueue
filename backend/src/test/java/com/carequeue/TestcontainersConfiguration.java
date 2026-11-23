package com.carequeue;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/**
	 * Synthetic demo-user passwords for every @SpringBootTest context.
	 * A DynamicPropertyRegistrar bean (not @DynamicPropertySource, which is only
	 * scanned on the test class itself) so it applies to every context that
	 * imports this configuration.
	 */
	@Bean
	static DynamicPropertyRegistrar securityProperties() {
		return registry -> {
			registry.add("carequeue.security.nurse-password", () -> "test-nurse-password");
			registry.add("carequeue.security.manager-password", () -> "test-manager-password");
		};
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
	}

}
