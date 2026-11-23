package com.carequeue.common.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Serves the React app from the same origin as the API (architecture.md, "Deployment").
 * <p>
 * The Docker build copies {@code frontend/dist} into {@code classpath:/static}, so Spring's
 * static resource handler serves {@code /index.html}, {@code /favicon.svg} and {@code /assets/**}.
 * React Router owns the browser routes below; they exist only in the browser, so a page refresh
 * or a bookmarked link would otherwise hit the server and fail. Each one is forwarded
 * (server-side, no redirect) to {@code /index.html}, and React Router renders the right page.
 */
@Configuration
public class SpaForwardingConfig implements WebMvcConfigurer {

	/**
	 * The browser routes of {@code frontend/src/app/routes.tsx}; keep the two in sync.
	 * {@code SecurityConfig} permits GET on exactly these paths (the page shell is public,
	 * the data behind {@code /api} is not).
	 */
	public static final List<String> ROUTES = List.of(
			"/", "/login", "/waiting-list", "/referrals", "/referrals/{id}", "/import", "/dead-letters");

	/** Files at the root of {@code frontend/dist} (plus the hashed bundles under /assets). */
	public static final List<String> STATIC_FILES = List.of("/index.html", "/favicon.svg", "/assets/**");

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		ROUTES.forEach(route -> registry.addViewController(route).setViewName("forward:/index.html"));
	}
}
