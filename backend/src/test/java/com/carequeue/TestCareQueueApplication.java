package com.carequeue;

import org.springframework.boot.SpringApplication;

public class TestCareQueueApplication {

	public static void main(String[] args) {
		SpringApplication.from(CareQueueApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
