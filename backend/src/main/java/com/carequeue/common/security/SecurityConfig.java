package com.carequeue.common.security;

import com.carequeue.common.web.SpaForwardingConfig;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.util.StringUtils;

/**
 * HTTP Basic with two demo users (ADR-004), stateless, problem+json 401/403 (ADR-009).
 * The React UI's files and browser routes are public; the data behind {@code /api} is not (ADR-009).
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

	/**
	 * Same-origin only: the UI's scripts, styles, fonts and API calls all come from this server.
	 * {@code style-src 'unsafe-inline'} because React and Swagger UI set inline style attributes;
	 * scripts stay strict (no inline script, no eval). Swagger UI works under this policy as is
	 * (its index.html has no inline script), so there is one policy for every path (ADR-009).
	 */
	static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; "
			+ "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; "
			+ "connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; "
			+ "frame-ancestors 'none'";

	/** The app needs no powerful browser features; deny the common ones outright. */
	static final String PERMISSIONS_POLICY =
			"camera=(), microphone=(), geolocation=(), payment=(), usb=()";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationEntryPoint entryPoint,
			AccessDeniedHandler accessDeniedHandler) throws Exception {
		return http
				// CSRF is off for /api only: credentials travel in an explicit Authorization header,
				// never in a cookie or a browser-cached Basic login (ADR-009).
				.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(entryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				// Spring Security's defaults stay on (X-Content-Type-Options, X-Frame-Options DENY,
				// HSTS on https, no-cache); these add CSP, Referrer-Policy and Permissions-Policy.
				.headers(headers -> headers
						.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
						.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
						.permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY)))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health/**", "/error",
								"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
						.permitAll()
						// The React UI: its browser routes (forwarded to index.html) and its static files.
						.requestMatchers(HttpMethod.GET, SpaForwardingConfig.ROUTES.toArray(String[]::new))
						.permitAll()
						.requestMatchers(HttpMethod.GET, SpaForwardingConfig.STATIC_FILES.toArray(String[]::new))
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
