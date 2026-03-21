package gatling;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.doIf;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.pause;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.repeat;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class AccountSimulation extends Simulation {

  private static final AtomicReference<String> heavyAccountId = new AtomicReference<>();
  private final String baseUrl = System.getProperty("baseUrl", "http://localhost:8080");
  private final HttpProtocolBuilder httpProtocol =
      http.baseUrl(baseUrl).acceptHeader("application/json").contentTypeHeader("application/json");
  // Scenario 1: Full account lifecycle (open → deposit → read → withdraw)
  private final ScenarioBuilder accountLifecycle =
      scenario("Account Lifecycle")
          .exec(
              http("Open Account")
                  .post("/accounts")
                  .body(
                      StringBody(
                          "{\"accountCurrency\":\"EUR\",\"initialAmount\":1000,\"initialAmountCurrency\":\"EUR\"}"))
                  .check(status().is(201))
                  .check(jsonPath("$.accountId").saveAs("accountId")))
          .exitHereIfFailed()
          .exec(
              http("Deposit")
                  .post("/accounts/#{accountId}/deposits")
                  .header("Transaction-Id", _ -> UUID.randomUUID().toString())
                  .body(StringBody("{\"currency\":\"EUR\",\"amount\":500}"))
                  .check(status().is(200)))
          .exitHereIfFailed()
          .exec(session -> session.set("depositVisible", false))
          .exec(
              repeat(19, "projectionRetryAttempt")
                  .on(
                      doIf(session -> !session.getBoolean("depositVisible"))
                          .then(
                              pause(Duration.ofMillis(500))
                                  .exec(
                                      http("Get Account Retry")
                                          .get("/accounts/#{accountId}")
                                          .silent()
                                          .check(status().saveAs("getAccountStatus"))
                                          .check(
                                              jsonPath("$.balanceAmount")
                                                  .ofDouble()
                                                  .optional()
                                                  .saveAs("balanceAmount")))
                                  .exec(
                                      session -> {
                                        boolean projectionCaughtUp =
                                            session.getInt("getAccountStatus") == 200
                                                && session.contains("balanceAmount")
                                                && session.getDouble("balanceAmount") == 1500.0;
                                        return session.set("depositVisible", projectionCaughtUp);
                                      }))))
          .exec(
              doIf(session -> !session.getBoolean("depositVisible"))
                  .then(
                      pause(Duration.ofMillis(500))
                          .exec(
                              http("Get Account")
                                  .get("/accounts/#{accountId}")
                                  .check(status().is(200))
                                  .check(jsonPath("$.balanceAmount").ofDouble().is(1500.0)))))
          .exitHereIfFailed()
          .exec(
              http("Withdraw")
                  .post("/accounts/#{accountId}/withdrawals")
                  .header("Transaction-Id", _ -> UUID.randomUUID().toString())
                  .body(StringBody("{\"currency\":\"EUR\",\"amount\":200}"))
                  .check(status().is(200)));
  // Scenario 2: Reservation flow (open → reserve → capture)
  private final ScenarioBuilder reservationFlow =
      scenario("Reservation Flow")
          .exec(
              http("Open Account for Reservation")
                  .post("/accounts")
                  .body(
                      StringBody(
                          "{\"accountCurrency\":\"USD\",\"initialAmount\":5000,\"initialAmountCurrency\":\"USD\"}"))
                  .check(status().is(201))
                  .check(jsonPath("$.accountId").saveAs("accountId")))
          .exitHereIfFailed()
          .exec(
              http("Reserve Funds")
                  .post("/accounts/#{accountId}/reservations")
                  .header("Transaction-Id", _ -> UUID.randomUUID().toString())
                  .body(StringBody("{\"currency\":\"USD\",\"amount\":1000}"))
                  .check(status().is(200))
                  .check(jsonPath("$.reservationId").saveAs("reservationId")))
          .exitHereIfFailed()
          .exec(
              http("Capture Reservation")
                  .post("/accounts/#{accountId}/reservations/#{reservationId}/capture")
                  .check(status().is(200)));
  private final ScenarioBuilder heavyAccountSetup =
      scenario("Heavy Account Setup")
          .exec(
              http("Create Heavy Account")
                  .post("/accounts")
                  .body(
                      StringBody(
                          "{\"accountCurrency\":\"EUR\",\"initialAmount\":10000,\"initialAmountCurrency\":\"EUR\"}"))
                  .check(status().is(201))
                  .check(jsonPath("$.accountId").saveAs("accountId")))
          .exitHereIfFailed()
          .exec(
              session -> {
                heavyAccountId.set(session.getString("accountId"));
                return session;
              });

  // Scenario 3: Single account under heavy sequential write load (tests event store growth +
  // snapshots)
  private final ScenarioBuilder heavyAccountLoad =
      scenario("Heavy Account Deposits")
          .exec(
              session -> {
                String accountId = heavyAccountId.get();
                if (accountId == null) {
                  return session.markAsFailed();
                }
                return session.set("accountId", accountId);
              })
          .exitHereIfFailed()
          .repeat(1000)
          .on(
              exec(
                  http("Heavy Deposit")
                      .post("/accounts/#{accountId}/deposits")
                      .header("Transaction-Id", _ -> UUID.randomUUID().toString())
                      .body(StringBody("{\"currency\":\"EUR\",\"amount\":1}"))
                      .check(status().is(200))));

  // Load profile
  {
    setUp(
            accountLifecycle.injectOpen(
                rampUsersPerSec(1).to(100).during(30),
                constantUsersPerSec(100).during(60),
                rampUsersPerSec(100).to(1).during(30)),
            reservationFlow.injectOpen(
                rampUsersPerSec(1).to(50).during(30),
                constantUsersPerSec(50).during(60),
                rampUsersPerSec(50).to(1).during(30)),
            heavyAccountSetup
                .injectOpen(atOnceUsers(1))
                .andThen(heavyAccountLoad.injectOpen(atOnceUsers(1))))
        .protocols(httpProtocol)
        .assertions(
            global().successfulRequests().percent().is(100.0),
            global().responseTime().percentile1().lt(10),
            global().responseTime().percentile3().lt(50),
            global().responseTime().percentile4().lt(100));
  }
}
