package io.edge.system.verticles;

import io.vertx.circuitbreaker.HystrixMetricHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;

public class HystrixVerticle extends AbstractVerticle {

	private static final Logger LOGGER = LoggerFactory.getLogger(HystrixVerticle.class);

	@Override
	public void start(Future<Void> startFuture) {

		int port = this.config().getInteger("hystrix.port", 8081);

		Router router = Router.router(vertx);

		router.get("/hystrix-metrics").handler(HystrixMetricHandler.create(vertx));

		vertx.createHttpServer().requestHandler(router).listen(port, ar -> {

			if (ar.succeeded()) {
				LOGGER.info("SSE Hystrix metrics stream start on port " + ar.result().actualPort() + " /hystrix-metrics");
				startFuture.complete();
			} else {
				startFuture.fail(ar.cause());
			}

		});

	}

}
