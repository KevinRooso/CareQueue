package com.carequeue.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

	private final RequestIdFilter filter = new RequestIdFilter();

	private static final FilterChain NOOP_CHAIN = (req, res) -> {
	};

	private String run(MockHttpServletRequest request) throws ServletException, IOException {
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, NOOP_CHAIN);
		return response.getHeader(RequestIdFilter.HEADER);
	}

	@Test
	void doFilter_whenValidHeader_echoesIt() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/referrals");
		request.addHeader(RequestIdFilter.HEADER, "smoke-123_ABC.def");

		String echoed = run(request);

		assertThat(echoed).isEqualTo("smoke-123_ABC.def");
	}

	@Test
	void doFilter_whenNoHeader_generatesUuid() throws Exception {
		String generated = run(new MockHttpServletRequest("GET", "/api/referrals"));

		assertThat(UUID.fromString(generated)).isNotNull();
	}

	@Test
	void doFilter_whenHeaderTooLong_replacesIt() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/referrals");
		request.addHeader(RequestIdFilter.HEADER, "x".repeat(65));

		String id = run(request);

		assertThat(UUID.fromString(id)).isNotNull();
	}

	@Test
	void doFilter_whenHeaderHasBadCharacters_replacesIt() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/referrals");
		request.addHeader(RequestIdFilter.HEADER, "bad id with spaces");

		String id = run(request);

		assertThat(UUID.fromString(id)).isNotNull();
	}

	@Test
	void doFilter_whenHeaderHasCrLf_replacesIt() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/referrals");
		request.addHeader(RequestIdFilter.HEADER, "smoke\r\nInjected: yes");

		String id = run(request);

		assertThat(UUID.fromString(id)).isNotNull();
	}

	@Test
	void doFilter_afterRequest_mdcIsCleared() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/referrals");
		FilterChain capturingChain = (req, res) -> assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNotBlank();

		filter.doFilter(request, new MockHttpServletResponse(), capturingChain);

		assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
	}
}
