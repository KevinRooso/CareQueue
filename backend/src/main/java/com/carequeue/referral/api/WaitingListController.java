package com.carequeue.referral.api;

import com.carequeue.referral.service.ReferralQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/waiting-list")
@PreAuthorize("hasRole('CLINICIAN')")
@Tag(name = "waiting-list", description = "Clinic waiting lists ordered by confirmed priority")
public class WaitingListController {

	private final ReferralQueryService queryService;

	public WaitingListController(ReferralQueryService queryService) {
		this.queryService = queryService;
	}

	@GetMapping
	@Operation(summary = "Waiting list for a clinic (ordering is fixed server-side; sort is ignored)")
	public PagedModel<WaitingListEntryDto> list(@RequestParam Long clinicId,
			@PageableDefault Pageable pageable) {
		return new PagedModel<>(queryService.waitingList(clinicId,
				PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
				.map(WaitingListEntryDto::from));
	}
}
