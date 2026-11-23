package com.carequeue.common.security;

import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.util.StringUtils;

/**
 * HTTP Basic with two demo users (ADR-004), stateless, problem+json 401/403 (ADR-009).
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationEntryPoint entryPoint,
			AccessDeniedHandler accessDeniedHandler) throws Exception {
		return http
				.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(entryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health/**", "/error",
								"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
						.permitAll()
						.requestMatchers("/actuator/**").hasRole("ADMIN")
						.requestMatchers("/api/**").authenticated()
						.anyRequest().denyAll())
				.build();
	}

	@Bean
	AuthenticationEntryPoint problemJsonEntryPoint(ObjectMapper objectMapper) {
		return new ProblemJsonAuthenticationEntryPoint(objectMapper);
	}

	@Bean
	AccessDeniedHandler problemJsonAccessDeniedHandler(ObjectMapper objectMapper) {
		return new ProblemJsonAccessDeniedHandler(objectMapper);
	}

	@Bean
	RoleHierarchy roleHierarchy() {
		return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_CLINICIAN");
	}

	@Bean
	static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
		DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
		handler.setRoleHierarchy(roleHierarchy);
		return handler;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	UserDetailsService userDetailsService(SecurityProperties properties, PasswordEncoder passwordEncoder) {
		String nursePassword = requirePassword(properties.getNursePassword(), "CAREQUEUE_NURSE_PASSWORD");
		String managerPassword = requirePassword(properties.getManagerPassword(), "CAREQUEUE_MANAGER_PASSWORD");
		return new InMemoryUserDetailsManager(
				User.withUsername("nurse")
						.password(passwordEncoder.encode(nursePassword))
						.roles("CLINICIAN")
						.build(),
				User.withUsername("manager")
						.password(passwordEncoder.encode(managerPassword))
						.roles("ADMIN")
						.build());
	}

	private static String requirePassword(String password, String envVar) {
		if (!StringUtils.hasText(password)) {
			throw new IllegalStateException(
					"Demo user password is not set; export " + envVar + " before starting the app.");
		}
		return password;
	}
}
