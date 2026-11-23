package com.carequeue.audit.api;

import com.carequeue.audit.service.ReferralHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/referrals")
@Tag(name = "Referral history", description = "Audit trail of a referral's changes")
public class ReferralHistoryController {

	private final ReferralHistoryService historyService;

	public ReferralHistoryController(ReferralHistoryService historyService) {
		this.historyService = historyService;
	}

	@GetMapping("/{id}/history")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Change history of a referral (US-9)",
			description = "Every Envers revision of the referral with the fields that changed, "
					+ "who made the change and when. ADMIN only.")
	public List<ReferralRevisionDto> history(@PathVariable Long id) {
		return historyService.history(id);
	}
}
