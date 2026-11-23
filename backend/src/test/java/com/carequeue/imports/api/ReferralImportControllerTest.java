package com.carequeue.imports.api;

import com.carequeue.common.error.InvalidMessageException;
import com.carequeue.common.security.SecurityConfig;
import com.carequeue.imports.domain.ImportFormat;
import com.carequeue.imports.service.ReferralImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReferralImportController.class, properties = {
		"carequeue.security.nurse-password=test-nurse-password",
		"carequeue.security.manager-password=test-manager-password"})
@Import(SecurityConfig.class)
class ReferralImportControllerTest {

	private static final String NURSE = "nurse";
	private static final String NURSE_PW = "test-nurse-password";
	private static final String MANAGER = "manager";
	private static final String MANAGER_PW = "test-manager-password";
	private static final String JSON_BODY = "{\"externalRef\":\"GP-1\"}";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ReferralImportService importService;

	@Test
	void importJson_whenCreated_returns201WithLocation() throws Exception {
		when(importService.importReferral(anyString(), any()))
				.thenReturn(new ImportResultDto(60L, true));

		mockMvc.perform(post("/api/referrals/import")
				.with(httpBasic(MANAGER, MANAGER_PW))
				.contentType("application/json")
				.content(JSON_BODY))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/referrals/60"))
				.andExpect(jsonPath("$.id").value(60))
				.andExpect(jsonPath("$.created").value(true));
	}

	@Test
	void importJson_whenDuplicate_returns200WithExistingId() throws Exception {
		when(importService.importReferral(anyString(), any()))
				.thenReturn(new ImportResultDto(55L, false));

		mockMvc.perform(post("/api/referrals/import")
				.with(httpBasic(MANAGER, MANAGER_PW))
				.contentType("application/json")
				.content(JSON_BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(55))
				.andExpect(jsonPath("$.created").value(false));
	}

	@Test
	void importXml_whenCreated_passesXmlFormat() throws Exception {
		when(importService.importReferral(anyString(), org.mockito.ArgumentMatchers.eq(ImportFormat.XML)))
				.thenReturn(new ImportResultDto(61L, true));

		mockMvc.perform(post("/api/referrals/import")
				.with(httpBasic(MANAGER, MANAGER_PW))
				.contentType("application/xml")
				.content("<referral/>"))
				.andExpect(status().isCreated());
	}

	@Test
	void importJson_whenInvalid_returns422WithErrorsAndDeadLetterId() throws Exception {
		when(importService.importReferral(anyString(), any()))
				.thenThrow(new InvalidMessageException("The message failed validation.",
						List.of(new InvalidMessageException.Violation("externalRef", "must not be blank")))
						.withDeadLetterId(77L));

		mockMvc.perform(post("/api/referrals/import")
				.with(httpBasic(MANAGER, MANAGER_PW))
				.contentType("application/json")
				.content(JSON_BODY))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(content().contentType("application/problem+json"))
				.andExpect(jsonPath("$.title").value("Invalid referral message"))
				.andExpect(jsonPath("$.errors[0].field").value("externalRef"))
				.andExpect(jsonPath("$.deadLetterId").value(77));
	}

	@Test
	void import_whenWrongContentType_returns415() throws Exception {
		mockMvc.perform(post("/api/referrals/import")
				.with(httpBasic(MANAGER, MANAGER_PW))
				.contentType("text/plain")
				.content("hello"))
				.andExpect(status().isUnsupportedMediaType());
	}

	@Test
	void import_whenNurse_returns403() throws Exception {
		mockMvc.perform(post("/api/referrals/import")
				.with(httpBasic(NURSE, NURSE_PW))
				.contentType("application/json")
				.content(JSON_BODY))
				.andExpect(status().isForbidden());
	}

	@Test
	void import_whenUnauthenticated_returns401WithoutChallenge() throws Exception {
		mockMvc.perform(post("/api/referrals/import")
				.contentType("application/json")
				.content(JSON_BODY))
				.andExpect(status().isUnauthorized())
				.andExpect(header().doesNotExist("WWW-Authenticate"));
	}
}
