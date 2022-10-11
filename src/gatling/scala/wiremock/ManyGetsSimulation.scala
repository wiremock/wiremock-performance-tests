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
    loadTestConfiguration.manyStubGetScenario()
  }

  after {
    loadTestConfiguration.after()
  }

  val httpConf = http
    .baseUrl(loadTestConfiguration.getBaseUrl)
    .shareConnections

  val manyGetsScenario = {
    scenario(s"${loadTestConfiguration.getStubCount} GETs")
      .repeat(1) {
        exec(http("GETs")
          .get(session => s"load-test/${random.nextInt(loadTestConfiguration.getStubCount - 1) + 1}")
          .header("Accept", "text/plain+stuff")
          .check(status.is(200)))
        .exec(http("Not founds")
          .get(session => s"load-test/${random.nextInt(loadTestConfiguration.getStubCount - 1) + 7000}")
          .header("Accept", "text/plain+stuff")
          .check(status.is(404)))
      }
  }

  setUp(
    manyGetsScenario.inject(
      constantUsersPerSec(loadTestConfiguration.getRate).during(loadTestConfiguration.getDurationSeconds seconds)
    )
  )
  .throttle(
    reachRps(loadTestConfiguration.getRate).in(loadTestConfiguration.getRampSeconds seconds),
    holdFor(loadTestConfiguration.getDurationSeconds seconds)
  )
  .protocols(httpConf)
}
