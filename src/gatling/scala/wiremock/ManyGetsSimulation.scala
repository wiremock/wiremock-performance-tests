package wiremock

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._
import scala.language.postfixOps

class ManyGetsSimulation extends Simulation {

  val loadTestConfiguration = LoadTestConfiguration.fromEnvironment()

  val random = scala.util.Random

  before {
    loadTestConfiguration.before()
    loadTestConfiguration.onlyGet6000StubScenario()
  }

  after {
    loadTestConfiguration.after()
  }

  val httpConf = http
    .baseUrl(loadTestConfiguration.getBaseUrl)
    .shareConnections

  val onlyGet6000StubScenario = {
    scenario("6000 GETs")
      .repeat(1) {
        exec(http("GETs")
          .get(session => s"load-test/${random.nextInt(5999) + 1}")
          .header("Accept", "text/plain+stuff")
          .check(status.is(200)))
        .exec(http("Not founds")
          .get(session => s"load-test/${random.nextInt(5999) + 7000}")
          .header("Accept", "text/plain+stuff")
          .check(status.is(404)))
      }
  }

  setUp(
    onlyGet6000StubScenario.inject(
      rampUsers(loadTestConfiguration.getRate).during(loadTestConfiguration.getRampSeconds seconds),
      constantUsersPerSec(loadTestConfiguration.getRate).during(loadTestConfiguration.getDurationSeconds seconds)
    )
  ).protocols(httpConf)
}
