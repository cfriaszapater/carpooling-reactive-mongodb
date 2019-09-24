package com.cabify.pooling;

import com.mongodb.WriteConcern;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.WriteConcernResolver;

@SpringBootApplication
public class CarPoolingApplication {

	@Bean
	public WriteConcernResolver writeConcernResolver() {
		return action -> {
			System.out.println("Using Write Concern of MAJORITY");
			return WriteConcern.MAJORITY;
		};
	}

	public static void main(String[] args) {
		SpringApplication.run(CarPoolingApplication.class, args);
	}

}
