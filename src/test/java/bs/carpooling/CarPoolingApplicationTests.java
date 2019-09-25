package bs.carpooling;

import bs.carpooling.repository.CarsRepository;
import bs.util.FileUtil;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureWebTestClient
public class CarPoolingApplicationTests {

  @Autowired
  private WebTestClient webClient;

  @Autowired
  private CarsRepository carsRepository;

  @Before
  public void before() {
    Hooks.onOperatorDebug();
    carsRepository.initWith(Flux.empty()).blockLast();
  }

  @Test
  public void WhenGetStatus_ThenOk() {
    ResponseSpec result = webClient.get().uri("http://localhost/status").exchange();
    result.expectStatus().isOk();
  }

  @Test
  public void WhenPutCars_ThenOk() {
    ResponseSpec result = putCars46();

    result.expectStatus().isOk();
  }

  private ResponseSpec putCars46() {
    return webClient.put().uri("http://localhost/cars").contentType(MediaType.APPLICATION_JSON)
      .syncBody(FileUtil.loadFile("put-cars-46.ok.json")).exchange();
  }

  @Test
  public void WhenPutCarsBadFormat_Then400BadRequest() {
    webClient.put().uri("http://localhost/cars").contentType(MediaType.APPLICATION_JSON)
      .syncBody(FileUtil.loadFile("put-cars.bad-request.nojson")).exchange().expectStatus().isBadRequest();
  }

  @Test
  public void WhenPostJourney_ThenOk() {
    ResponseSpec result = postJourney4();

    result.expectStatus().isOk();
  }

  private ResponseSpec postJourney4() {
    return webClient.post().uri("http://localhost/journey").contentType(MediaType.APPLICATION_JSON)
      .syncBody(FileUtil.loadFile("post-journey-4.ok.json")).exchange();
  }

  @Test
  public void WhenPostJourneyBadFormat_Then400BadRequest() {
    webClient.post().uri("http://localhost/journey").contentType(MediaType.APPLICATION_JSON)
      .syncBody(FileUtil.loadFile("post-journey.bad-request.nojson")).exchange().expectStatus().isBadRequest();
  }

  @Test
  public void WhenPostDropoff_Then404() {
    int groupId = 13;
    ResponseSpec result = postDropoff(groupId);

    result.expectStatus().isNotFound();
  }

  private ResponseSpec postDropoff(int groupId) {
    return webClient.post().uri("http://localhost/dropoff").contentType(MediaType.APPLICATION_FORM_URLENCODED)
      .syncBody("ID=" + groupId).exchange();
  }

  @Test
  public void GivenGroupWaiting_WhenPostDropoff_ThenRemovedFromWaitingGroups() {
    postJourney4().expectStatus().isOk();

    int groupId = 1;
    ResponseSpec result = postDropoff(groupId);

    result.expectStatus().isNoContent();
  }

  @Test
  public void GivenGroupAssigned_WhenPostDropoff_ThenOk() {
    putCars46().expectStatus().isOk();
    postJourney4().expectStatus().isOk();

    int groupId = 1;
    ResponseSpec result = postDropoff(groupId);

    result.expectStatus().isOk();
  }

  @Test
  public void WhenPostDropoffBadFormat_Then400() {
    webClient.post().uri("http://localhost/dropoff").contentType(MediaType.APPLICATION_FORM_URLENCODED)
      .syncBody("wrong-body").exchange().expectStatus().isBadRequest();
  }

  @Test
  public void WhenPostLocate_Then404() {
    ResponseSpec result = postLocate(1);

    result.expectStatus().isNotFound();
  }

  private ResponseSpec postLocate(Integer groupId) {
    return webClient.post().uri("http://localhost/locate").contentType(MediaType.APPLICATION_FORM_URLENCODED)
      .syncBody("ID=" + groupId).exchange();
  }

  @Test
  public void GivenGroupAssignedToCar_WhenPostLocate_ThenCar_AndAvailableSeatsReduced() {
    putCars46();
    postJourney4();

    ResponseSpec result = postLocate(1);

    result.expectStatus().isOk()
      .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
      .expectBody()
      .jsonPath("$.id").isEqualTo(1)
      .jsonPath("$.seats").isEqualTo(0);
  }

  @Test
  public void GivenGroupWaiting_WhenPostLocate_Then204() {
    postJourney4();

    ResponseSpec result = postLocate(1);

    result.expectStatus().isNoContent();
  }

  @Test
  public void GivenGroupNotExists_WhenPostLocate_Then404() {
    ResponseSpec result = postLocate(1);

    result.expectStatus().isNotFound();
  }

  @SuppressWarnings("SameReturnValue")
  @Test
  public void GivenGroupWaiting_AndDroppedoff_WhenPostLocateAfterSomeTime_Then404() {
    postJourney4();
    postDropoff(1);

    await().atMost(1, SECONDS).ignoreExceptions().until(() -> {
      postLocate(1).expectStatus().isNotFound();
      return true;
    });
    ResponseSpec result = postLocate(1);

    result.expectStatus().isNotFound();
  }

  @Test
  public void GivenGroupAssigned_AndDroppedoff_WhenLocate_Then404() {
    putCars46();
    postJourney4();
    int groupId = 1;
    postDropoff(groupId);

    ResponseSpec result = postLocate(groupId);

    result.expectStatus().isNotFound();
    StepVerifier.create(carsRepository.findAllGroupsWaiting()).verifyComplete();
  }

}
