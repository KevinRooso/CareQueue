package com.carequeue.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo-user passwords (ADR-004), bound from CAREQUEUE_NURSE_PASSWORD and
 * CAREQUEUE_MANAGER_PASSWORD via application.yml. No defaults — blank fails startup.
 */
@ConfigurationProperties(prefix = "carequeue.security")
public class SecurityProperties {

	private String nursePassword;
	private String managerPassword;

	public String getNursePassword() {
		return nursePassword;
	}

	public void setNursePassword(String nursePassword) {
		this.nursePassword = nursePassword;
	}

	public String getManagerPassword() {
		return managerPassword;
	}

	public void setManagerPassword(String managerPassword) {
		this.managerPassword = managerPassword;
	}
}
