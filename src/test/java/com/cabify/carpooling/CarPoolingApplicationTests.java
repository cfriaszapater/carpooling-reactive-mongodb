package com.cabify.carpooling;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import com.cabify.carpooling.controller.CarPoolingController;

@RunWith(SpringRunner.class)
@SpringBootTest
public class CarPoolingApplicationTests {

	private WebTestClient webClient = WebTestClient.bindToController(new CarPoolingController()).build();

	@Test
	public void contextLoads() {
	}

	@Test
	public void WhenGetStatus_ThenOk() throws Exception {
		ResponseSpec result = webClient.get().uri("http://localhost/status").exchange();
		result.expectStatus().isOk();
	}
}
