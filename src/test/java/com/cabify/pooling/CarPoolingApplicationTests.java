package com.cabify.pooling;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import com.cabify.pooling.controller.CarPoolingController;
import com.cabify.util.FileUtil;

@RunWith(SpringRunner.class)
@SpringBootTest
public class CarPoolingApplicationTests {

	@Autowired
	private CarPoolingController carPoolingController;
	
	private WebTestClient webClient;

	@Before
	public void before() {
		webClient = WebTestClient.bindToController(carPoolingController).build();
	}
	
	@Test
	public void contextLoads() {
	}

	@Test
	public void WhenGetStatus_ThenOk() throws Exception {
		ResponseSpec result = webClient.get().uri("http://localhost/status").exchange();
		result.expectStatus().isOk();
	}

	@Test
	public void WhenPutCars_ThenOk() throws Exception {
		ResponseSpec result = putCars46();

		result.expectStatus().isOk();
	}

	private ResponseSpec putCars46() throws Exception {
		return webClient.put().uri("http://localhost/cars").contentType(MediaType.APPLICATION_JSON)
				.syncBody(FileUtil.loadFile("put-cars-46.ok.json")).exchange();
	}

}
