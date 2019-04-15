package io.edge.system.verticles;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;

public class LauncherVerticle extends AbstractVerticle {

	@Override
	public void start() {

		ConfigRetriever.create(vertx).getConfig(ar -> {

			if (ar.succeeded()) {

				JsonObject config = ar.result();

				DeploymentOptions options = new DeploymentOptions().setConfig(config);

				if (config.getBoolean("hystrix_http_stream", true)) {
					vertx.deployVerticle(HystrixVerticle.class.getName(), options);
				}

				if (config.getBoolean("cb_storage", true)) {
					vertx.deployVerticle(CircuitBreakerStorageVerticle.class.getName(), options);
				}

			}

		});

	}

}
