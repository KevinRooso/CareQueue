package com.carequeue.triage.api;

import com.carequeue.referral.api.ReferralDetailDto;
import com.carequeue.triage.service.TriageSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/referrals")
@PreAuthorize("hasRole('CLINICIAN')")
@Tag(name = "triage", description = "AI triage suggestions (a clinician still confirms)")
public class TriageSuggestionController {

	private final TriageSuggestionService triageSuggestionService;

	public TriageSuggestionController(TriageSuggestionService triageSuggestionService) {
		this.triageSuggestionService = triageSuggestionService;
	}

	@PostMapping("/{id}/triage-suggestion")
	@Operation(summary = "Run the AI (or rule-based) triage suggestion; moves RECEIVED → AI_SUGGESTED")
	public ReferralDetailDto suggest(@PathVariable Long id) {
		return ReferralDetailDto.from(triageSuggestionService.suggest(id));
	}
}
