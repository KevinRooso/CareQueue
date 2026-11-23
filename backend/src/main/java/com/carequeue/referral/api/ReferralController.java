package com.carequeue.referral.api;

import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.ReferralStatus;
import com.carequeue.referral.service.ReferralCommandService;
import com.carequeue.referral.service.ReferralQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/referrals")
@PreAuthorize("hasRole('CLINICIAN')")
@Tag(name = "referrals", description = "Referral search, detail, triage confirm and reject")
public class ReferralController {

	private final ReferralQueryService queryService;
	private final ReferralCommandService commandService;

	public ReferralController(ReferralQueryService queryService,
			ReferralCommandService commandService) {
		this.queryService = queryService;
		this.commandService = commandService;
	}

	@GetMapping
	@Operation(summary = "List referrals, optionally filtered by status, confirmed priority and clinic")
	public PagedModel<ReferralSummaryDto> list(
			@RequestParam(required = false) ReferralStatus status,
			@RequestParam(required = false) Priority priority,
			@RequestParam(required = false) Long clinicId,
			@PageableDefault(sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return new PagedModel<>(queryService.search(status, priority, clinicId, pageable)
				.map(ReferralSummaryDto::from));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Referral detail, including AI suggestion and confirmed triage fields")
	public ReferralDetailDto detail(@PathVariable Long id) {
		return ReferralDetailDto.from(queryService.getById(id));
	}

	@PostMapping("/{id}/triage")
	@Operation(summary = "Clinician confirms the priority (version-guarded)")
	public ReferralDetailDto confirm(@PathVariable Long id,
			@Valid @RequestBody ConfirmTriageRequest body, Authentication authentication) {
		return ReferralDetailDto.from(commandService.confirm(id, body.priority(), body.note(),
				body.version(), authentication.getName()));
	}

	@PostMapping("/{id}/reject")
	@Operation(summary = "Reject a referral (version-guarded)")
	public ReferralDetailDto reject(@PathVariable Long id,
			@Valid @RequestBody RejectReferralRequest body) {
		return ReferralDetailDto.from(commandService.reject(id, body.version()));
	}
}
