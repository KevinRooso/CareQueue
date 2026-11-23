package com.carequeue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The React UI served by Spring (single origin, M7). Runs against a real server on a random port
 * instead of MockMvc, because MockMvc does not execute forwards: only a real request proves that
 * a browser route is forwarded to index.html <em>and</em> that Spring Security lets the forward through.
 * <p>
 * {@code src/test/resources/static/} holds a tiny index.html and assets/x.js, so no frontend build is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class StaticUiIntegrationTest {

	private static final String INDEX_MARKER = "carequeue-test-index";
	private static final String NURSE_BASIC = "Basic " + Base64.getEncoder()
			.encodeToString("nurse:test-nurse-password".getBytes(StandardCharsets.UTF_8));

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	@LocalServerPort
	private int port;

	@ParameterizedTest
	@ValueSource(strings = {"/", "/login", "/waiting-list", "/referrals", "/referrals/42", "/import",
			"/dead-letters"})
	void spaRoute_withoutAuth_servesIndexHtml(String path) throws Exception {
		HttpResponse<String> response = get(path);

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
				type -> assertThat(type).startsWith("text/html"));
		assertThat(response.body()).contains(INDEX_MARKER).contains("<title>CareQueue</title>");
	}

	@Test
	void staticFiles_withoutAuth_areServed() throws Exception {
		HttpResponse<String> asset = get("/assets/x.js");
		assertThat(asset.statusCode()).isEqualTo(200);
		assertThat(asset.body()).contains("carequeue-test-asset");

		HttpResponse<String> index = get("/index.html");
		assertThat(index.statusCode()).isEqualTo(200);
		assertThat(index.body()).contains(INDEX_MARKER);
	}

	@Test
	void api_staysProtected() throws Exception {
		HttpResponse<String> anonymous = get("/api/me");
		assertThat(anonymous.statusCode()).isEqualTo(401);
		assertThat(anonymous.headers().firstValue("WWW-Authenticate")).isEmpty();

		HttpResponse<String> nurse = send(request("/api/me").header("Authorization", NURSE_BASIC));
		assertThat(nurse.statusCode()).isEqualTo(200);
		assertThat(nurse.body()).contains("\"username\":\"nurse\"");
	}

	@Test
	void unknownPaths_areDenied() throws Exception {
		// Anything that is not a UI route, a UI file, the docs, health or /api stays denyAll.
		assertThat(get("/secret").statusCode()).isEqualTo(401);
		assertThat(get("/referrals/42/edit").statusCode()).isEqualTo(401);
		assertThat(send(request("/secret").header("Authorization", NURSE_BASIC)).statusCode())
				.isEqualTo(403);
		// Only GET is public for UI routes.
		assertThat(send(request("/login").POST(HttpRequest.BodyPublishers.noBody())).statusCode())
				.isIn(401, 403);
	}

	@Test
	void root_sendsSecurityHeaders() throws Exception {
		HttpResponse<String> response = get("/");

		assertThat(response.headers().firstValue("Content-Security-Policy")).hasValue(
				"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
						+ "img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; "
						+ "base-uri 'self'; form-action 'self'; frame-ancestors 'none'");
		assertThat(response.headers().firstValue("Referrer-Policy")).hasValue("no-referrer");
		assertThat(response.headers().firstValue("Permissions-Policy")).hasValueSatisfying(
				policy -> assertThat(policy).contains("camera=()").contains("geolocation=()"));
		// Spring Security defaults are still there.
		assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
		assertThat(response.headers().firstValue("X-Frame-Options")).hasValue("DENY");
		// Plain http (no proxy): no HSTS.
		assertThat(response.headers().firstValue("Strict-Transport-Security")).isEmpty();
	}

	@Test
	void behindTlsProxy_sendsHsts() throws Exception {
		// Render terminates TLS and forwards plain http with X-Forwarded-Proto: https.
		// server.forward-headers-strategy=framework makes the request count as secure.
		HttpResponse<String> response = send(request("/").header("X-Forwarded-Proto", "https"));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Strict-Transport-Security")).isPresent();
	}

	@Test
	void swaggerUi_isServed_withoutInlineScripts() throws Exception {
		HttpResponse<String> index = get("/swagger-ui/index.html");
		assertThat(index.statusCode()).isEqualTo(200);
		assertThat(index.headers().firstValue("Content-Security-Policy")).isPresent();
		// The strict script-src 'self' only works while Swagger UI loads every script from a file.
		// If a springdoc upgrade adds an inline <script>, this fails before the browser does.
		assertThat(Pattern.compile("<script(?![^>]*\\bsrc=)", Pattern.CASE_INSENSITIVE)
				.matcher(index.body()).find()).as("inline <script> in Swagger UI").isFalse();

		assertThat(get("/swagger-ui/swagger-initializer.js").statusCode()).isEqualTo(200);
		assertThat(get("/v3/api-docs/swagger-config").statusCode()).isEqualTo(200);
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
	}

	private HttpResponse<String> get(String path) throws IOException, InterruptedException {
		return send(request(path));
	}

	private HttpResponse<String> send(HttpRequest.Builder builder) throws IOException, InterruptedException {
		return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}
}
