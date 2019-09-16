package com.cabify.pooling;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import com.cabify.pooling.repository.CarsRepository;
import com.cabify.pooling.repository.GroupsRepository;
import com.cabify.util.FileUtil;

import reactor.core.publisher.Hooks;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureWebTestClient
public class CarPoolingApplicationTests {

	@Autowired
	private WebTestClient webClient;
	
	@Autowired
	private GroupsRepository groupsRepository;

	@Autowired
	private CarsRepository carsRepository;

	@Before
	public void before() {
		Hooks.onOperatorDebug();
		groupsRepository.deleteAll().block();
		carsRepository.deleteAll().block();
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

	@Test
	public void WhenPutCarsBadFormat_Then400BadRequest() throws Exception {
		webClient.put().uri("http://localhost/cars").contentType(MediaType.APPLICATION_JSON)
				.syncBody(FileUtil.loadFile("put-cars.bad-request.json")).exchange().expectStatus().isBadRequest();
	}

	@Test
	public void WhenPostJourney_ThenOk() throws Exception {
		ResponseSpec result = postJourney4();

		result.expectStatus().isOk();
	}

	private ResponseSpec postJourney4() throws Exception {
		return webClient.post().uri("http://localhost/journey").contentType(MediaType.APPLICATION_JSON)
				.syncBody(FileUtil.loadFile("post-journey-4.ok.json")).exchange();
	}

	@Test
	public void WhenPostJourneyBadFormat_Then400BadRequest() throws Exception {
		webClient.post().uri("http://localhost/journey").contentType(MediaType.APPLICATION_JSON)
				.syncBody(FileUtil.loadFile("post-journey.bad-request.json")).exchange().expectStatus().isBadRequest();
	}

	@Test
	public void WhenPostDropoff_Then404() throws Exception {
		int groupId = 13;
		ResponseSpec result = postDropoff(groupId);

		result.expectStatus().isNotFound();
	}

	private ResponseSpec postDropoff(int groupId) throws Exception {
		return webClient.post().uri("http://localhost/dropoff").contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.syncBody("ID=" + groupId).exchange();
	}

	@Test
	public void GivenGroupWaiting_WhenPostDropoff_ThenRemovedFromWaitingGroups() throws Exception {
		postJourney4().expectStatus().isOk();

		int groupId = 1;
		ResponseSpec result = postDropoff(groupId);

		result.expectStatus().isNoContent();
	}

	@Test
	public void GivenGroupAssigned_WhenPostDropoff_ThenOk() throws Exception {
		putCars46().expectStatus().isOk();
		postJourney4().expectStatus().isOk();

		int groupId = 1;
		ResponseSpec result = postDropoff(groupId);

		result.expectStatus().isOk();
	}
}
