package com.carequeue.common.web;

import com.carequeue.common.error.BadSortException;
import com.carequeue.common.error.InvalidMessageException;
import com.carequeue.common.error.NotFoundException;
import com.carequeue.common.error.StaleVersionException;
import com.carequeue.referral.domain.InvalidStatusTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Turns exceptions into RFC 9457 problem+json. Domain rules map to 404/409,
 * validation and bad sorts to 400, everything else to a generic 500 — internals
 * and SQL never leave the log.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
		pd.setTitle(title);
		pd.setType(URI.create("about:blank"));
		withRequestId(pd);
		return pd;
	}

	private void withRequestId(ProblemDetail pd) {
		String requestId = MDC.get(RequestIdFilter.MDC_KEY);
		if (requestId != null) {
			pd.setProperty(RequestIdFilter.MDC_KEY, requestId);
		}
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
	}

	@ExceptionHandler(InvalidStatusTransitionException.class)
	ProblemDetail handleInvalidTransition(InvalidStatusTransitionException ex) {
		return problem(HttpStatus.CONFLICT, "Invalid status transition", ex.getMessage());
	}

	@ExceptionHandler(StaleVersionException.class)
	ProblemDetail handleStaleVersion(StaleVersionException ex) {
		return problem(HttpStatus.CONFLICT, "Stale version",
				"The referral was changed since you loaded it; reload and retry.");
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
		return problem(HttpStatus.CONFLICT, "Concurrent modification",
				"The resource was changed by another request; reload and retry.");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		// Log the constraint name only: the driver message can echo row values (MRNs, refs).
		String constraint = ex.getCause() instanceof org.hibernate.exception.ConstraintViolationException cve
				? cve.getConstraintName() : ex.getMostSpecificCause().getClass().getSimpleName();
		log.warn("Data integrity violation (constraint={})", constraint);
		return problem(HttpStatus.CONFLICT, "Data conflict",
				"The request conflicts with existing data.");
	}

	@ExceptionHandler(InvalidMessageException.class)
	ProblemDetail handleInvalidMessage(InvalidMessageException ex) {
		ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid referral message",
				ex.getMessage());
		if (!ex.getViolations().isEmpty()) {
			pd.setProperty("errors", ex.getViolations().stream()
					.map(v -> Map.of("field", v.field(), "message", v.message()))
					.toList());
		}
		if (ex.getDeadLetterId() != null) {
			pd.setProperty("deadLetterId", ex.getDeadLetterId());
		}
		return pd;
	}

	@ExceptionHandler({PropertyReferenceException.class, BadSortException.class})
	ProblemDetail handleBadSort(RuntimeException ex) {
		String detail = ex instanceof PropertyReferenceException pre
				? "Cannot sort by '" + pre.getPropertyName() + "'."
				: ex.getMessage() + ".";
		return problem(HttpStatus.BAD_REQUEST, "Invalid sort", detail);
	}

	@Override
	protected org.springframework.http.ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex, org.springframework.http.HttpHeaders headers,
			org.springframework.http.HttpStatusCode status, WebRequest request) {
		ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed",
				"One or more fields are invalid.");
		List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> Map.of("field", fe.getField(),
						"message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
				.toList();
		pd.setProperty("errors", errors);
		return org.springframework.http.ResponseEntity.badRequest().body(pd);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail handleAccessDenied(AccessDeniedException ex) {
		// @PreAuthorize denials surface inside the dispatcher, before the security
		// AccessDeniedHandler can see them — map them to the same 403 shape here.
		return problem(HttpStatus.FORBIDDEN, "Forbidden",
				"You do not have permission to perform this action.");
	}

	@Override
	protected org.springframework.http.ResponseEntity<Object> handleExceptionInternal(
			Exception ex, Object body, org.springframework.http.HttpHeaders headers,
			org.springframework.http.HttpStatusCode statusCode, WebRequest request) {
		org.springframework.http.ResponseEntity<Object> response =
				super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail pd) {
			withRequestId(pd);
		}
		return response;
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unhandled exception: {}: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
				"Something went wrong. Please try again.");
	}
}
