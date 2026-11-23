package com.carequeue.imports.api;

import com.carequeue.imports.domain.ImportFormat;
import com.carequeue.imports.service.ReferralImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/referrals")
@Tag(name = "imports", description = "Referral import (JSON/XML) with dead letters")
public class ReferralImportController {

	private final ReferralImportService importService;

	public ReferralImportController(ReferralImportService importService) {
		this.importService = importService;
	}

	@PostMapping(value = "/import",
			consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE })
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Import one referral (JSON or XML); idempotent on externalRef")
	public ResponseEntity<ImportResultDto> importReferral(
			@RequestBody String body,
			@org.springframework.web.bind.annotation.RequestHeader(
					name = "Content-Type") String contentType) {
		ImportFormat format = contentType != null
				&& contentType.toLowerCase().startsWith(MediaType.APPLICATION_XML_VALUE)
				? ImportFormat.XML
				: ImportFormat.JSON;
		ImportResultDto result = importService.importReferral(body, format);
		if (result.created()) {
			return ResponseEntity.created(URI.create("/api/referrals/" + result.id())).body(result);
		}
		return ResponseEntity.ok(result);
	}
}
