package com.carequeue.imports.api;

import com.carequeue.imports.service.DeadLetterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "dead-letters", description = "Failed referral imports")
public class DeadLetterController {

	private final DeadLetterService deadLetterService;

	public DeadLetterController(DeadLetterService deadLetterService) {
		this.deadLetterService = deadLetterService;
	}

	@GetMapping("/api/dead-letters")
	@Operation(summary = "Dead-letter messages, newest first")
	public PagedModel<DeadLetterDto> list(@PageableDefault Pageable pageable) {
		return new PagedModel<>(deadLetterService.page(pageable).map(DeadLetterDto::from));
	}
}
