package sttp.tapir.serverless.aws.lambda.tests

import cats.effect.IO
import org.scalatest.Assertions
import org.scalatest.compatible.Assertion
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.{basicRequest, _}
import sttp.model.{Header, Uri}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.backendResource

/** Requires running sam-local process with template generated by `LambdaSamTemplate`,
  * it's automated in sbt test task but requires sam cli installed.
  */
class AwsLambdaSamLocalHttpTest extends AnyFunSuite {

  private val baseUri: Uri = uri"http://localhost:3000"

  testServer(in_path_path_out_string_endpoint) { backend =>
    basicRequest.get(uri"$baseUri/fruit/orange/amount/20").send(backend).map { req =>
      req.body
        .map(_ shouldBe "orange 20")
        .getOrElse(Assertions.fail())
    }
  }

  testServer(in_string_out_string_endpoint) { backend =>
    basicRequest.post(uri"$baseUri/api/echo/string").body("Sweet").send(backend).map { req =>
      req.body.map(_ shouldBe "Sweet").getOrElse(Assertions.fail())
    }
  }

  testServer(in_json_out_json_endpoint) { backend =>
    basicRequest
      .post(uri"$baseUri/api/echo/json")
      .body("""{"fruit":"orange","amount":11}""")
      .send(backend)
      .map { req =>
        req.body
          .map(_ shouldBe """{"fruit":"orange","amount":11}""")
          .getOrElse(Assertions.fail())
      }
  }

  testServer(in_headers_out_headers_endpoint) { backend =>
    basicRequest
      .get(uri"$baseUri/api/echo/headers")
      .headers(Header.unsafeApply("X-Fruit", "apple"), Header.unsafeApply("Y-Fruit", "Orange"))
      .send(backend)
      .map(_.headers should contain allOf (Header.unsafeApply("X-Fruit", "apple"), Header.unsafeApply("Y-Fruit", "Orange")))
  }

  testServer(in_input_stream_out_input_stream_endpoint) { backend =>
    basicRequest.post(uri"$baseUri/api/echo/is").body("mango").send(backend).map(_.body shouldBe Right("mango"))
  }

  testServer(in_4query_out_4header_extended_endpoint) { backend =>
    basicRequest
      .get(uri"$baseUri/echo/query?a=1&b=2&x=3&y=4")
      .send(backend)
      .map(_.headers.map(h => h.name -> h.value).toSet should contain allOf ("A" -> "1", "B" -> "2", "X" -> "3", "Y" -> "4"))
  }

  private def testServer(t: ServerEndpoint[_, _, _, Any, IO], suffix: String = "")(
      f: SttpBackend[IO, Fs2Streams[IO] with WebSockets] => IO[Assertion]
  ): Unit = test(s"${t.endpoint.showDetail} $suffix")(backendResource.use(f(_)).unsafeRunSync())
}
