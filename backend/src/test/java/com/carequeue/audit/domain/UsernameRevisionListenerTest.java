package com.carequeue.audit.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class UsernameRevisionListenerTest {

	private final UsernameRevisionListener listener = new UsernameRevisionListener();

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void newRevision_whenAuthenticatedUser_recordsUsername() {
		SecurityContextHolder.getContext()
				.setAuthentication(new TestingAuthenticationToken("nurse.test", "pw", "ROLE_CLINICIAN"));
		AuditRevision rev = new AuditRevision();
		listener.newRevision(rev);
		assertThat(rev.getUsername()).isEqualTo("nurse.test");
	}

	@Test
	void newRevision_whenAnonymous_recordsSystem() {
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
				"key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
		AuditRevision rev = new AuditRevision();
		listener.newRevision(rev);
		assertThat(rev.getUsername()).isEqualTo("system");
	}

	@Test
	void newRevision_whenNoAuthentication_recordsSystem() {
		SecurityContextHolder.clearContext();
		AuditRevision rev = new AuditRevision();
		listener.newRevision(rev);
		assertThat(rev.getUsername()).isEqualTo("system");
	}
}
