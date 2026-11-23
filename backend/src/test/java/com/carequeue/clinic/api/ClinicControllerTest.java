package com.carequeue.clinic.api;

import com.carequeue.clinic.domain.Clinic;
import com.carequeue.clinic.service.ClinicService;
import com.carequeue.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClinicController.class, properties = {
		"carequeue.security.nurse-password=test-nurse-password",
		"carequeue.security.manager-password=test-manager-password"})
@Import(SecurityConfig.class)
class ClinicControllerTest {

	private static final String NURSE = "nurse";
	private static final String NURSE_PW = "test-nurse-password";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ClinicService clinicService;

	@Test
	void list_whenCalled_returnsClinics() throws Exception {
		Clinic c = new Clinic("GASTRO", "Gastroenterology Clinic", "GASTRO");
		ReflectionTestUtils.setField(c, "id", 1L);
		when(clinicService.findAll()).thenReturn(List.of(c));

		mockMvc.perform(get("/api/clinics").with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].code").value("GASTRO"))
				.andExpect(jsonPath("$[0].name").value("Gastroenterology Clinic"));
	}

	@Test
	void list_whenUnauthenticated_returns401WithoutChallenge() throws Exception {
		mockMvc.perform(get("/api/clinics"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType("application/problem+json"))
				.andExpect(header().doesNotExist("WWW-Authenticate"));
	}
}
