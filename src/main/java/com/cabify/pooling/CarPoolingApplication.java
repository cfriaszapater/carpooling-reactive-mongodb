package com.cabify.pooling;

import com.mongodb.WriteConcern;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.WriteConcernResolver;

@SpringBootApplication
public class CarPoolingApplication {

	// TODO possibly not needed
	@Bean
	public WriteConcernResolver writeConcernResolver() {
		return action -> WriteConcern.MAJORITY;
	}

	public static void main(String[] args) {
		SpringApplication.run(CarPoolingApplication.class, args);
	}

}
