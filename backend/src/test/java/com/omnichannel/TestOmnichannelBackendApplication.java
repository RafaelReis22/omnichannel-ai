package com.omnichannel;

import org.springframework.boot.SpringApplication;

public class TestOmnichannelBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(OmnichannelBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
