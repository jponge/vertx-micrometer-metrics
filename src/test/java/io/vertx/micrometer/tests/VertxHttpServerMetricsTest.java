package io.vertx.micrometer.tests;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.micrometer.Label;
import io.vertx.micrometer.MicrometerMetricsOptions;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(VertxUnitRunner.class)
public class VertxHttpServerMetricsTest extends MicrometerMetricsTestBase {

  private HttpServer httpServer;

  @Override
  protected MicrometerMetricsOptions metricOptions() {
    return super.metricOptions()
      .addLabels(Label.HTTP_PATH);
  }

  @Test
  public void shouldDecrementActiveRequestsWhenRequestEndedAfterResponseEnded(TestContext ctx) {
    vertx = vertx(ctx);
    int numRequests = 10;
    Async doneLatch = ctx.async(numRequests * 2);
    httpServer = vertx.createHttpServer()
      .requestHandler(req -> {
        req.response().end();
        req.end().onComplete(ctx.asyncAssertSuccess(v -> doneLatch.countDown()));
      });
    Async listenLatch = ctx.async();
    httpServer
      .listen(9195, "127.0.0.1")
      .onComplete(ctx.asyncAssertSuccess(s -> listenLatch.complete()));
    listenLatch.awaitSuccess(20_000);
    HttpClient client = vertx.createHttpClient();
    for (int i = 0;i < numRequests;i++) {
      client.request(HttpMethod.POST, 9195, "127.0.0.1", "/resource?foo=bar")
        .onComplete(ctx.asyncAssertSuccess(req -> {
          req
            .response()
            .compose(HttpClientResponse::body)
            .onComplete(ctx.asyncAssertSuccess(b -> {
              doneLatch.countDown();
              req.end();
            }));
          req.setChunked(true);
          req.write("chunk");
        }));
    }
    doneLatch.awaitSuccess(20_000);
    List<Datapoint> datapoints = listDatapoints(startsWith("vertx.http.server.active.requests"));
    assertThat(datapoints).hasSize(1).contains(
      dp("vertx.http.server.active.requests[method=POST,path=/resource]$VALUE", 0.0));
  }
}
