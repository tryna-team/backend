package com.tryna;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TrynaApplication {

	public static void main(String[] args) {
		SpringApplication.run(TrynaApplication.class, args);
	}

}
