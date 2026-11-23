package com.carequeue.audit.domain;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class UsernameRevisionListener implements RevisionListener {

	@Override
	public void newRevision(Object revisionEntity) {
		((AuditRevision) revisionEntity).setUsername(currentUsername());
	}

	private String currentUsername() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
			return "system";
		}
		return auth.getName();
	}
}
