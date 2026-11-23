package com.carequeue.common.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

/**
 * Serialises Page results through PagedModel's stable DTO shape:
 * {content: [...], page: {size, number, totalElements, totalPages}}.
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class WebConfig {
}
