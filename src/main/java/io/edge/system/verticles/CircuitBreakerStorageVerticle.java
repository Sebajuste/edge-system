package io.edge.system.verticles;

import java.util.concurrent.TimeUnit;

import io.edge.utils.timeseries.BatchPoints;
import io.edge.utils.timeseries.Point;
import io.edge.utils.timeseries.influxdb.InfluxDB;
import io.edge.utils.timeseries.influxdb.InfluxDbOptions;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class CircuitBreakerStorageVerticle extends AbstractVerticle {

	private Logger LOGGER = LoggerFactory.getLogger(CircuitBreakerStorageVerticle.class);

	private String databaseName;

	private InfluxDB influxDB = null;

	private CircuitBreaker addMeasuresCB;

	private void onNotificationReceived(Message<JsonObject> message) {

		// Create Points from values

		// System.currentTimeMillis();

		JsonObject body = message.body();

		String state = body.getString("state");

		String name = body.getString("name");

		String node = body.getString("node");

		int requestCount = body.getInteger("rollingOperationCount", 0);
		int errorCount = body.getInteger("rollingErrorCount", 0);

		int rollingLatencyMean = body.getInteger("rollingLatencyMean", 0);
		// int rollingLatency = body.getInteger("rollingLatency", 0);

		int totalLatencyMean = body.getInteger("totalLatencyMean", 0);
		// int totalLatency = body.getInteger("totalLatency", 0);

		boolean open = state.equalsIgnoreCase(CircuitBreakerState.OPEN.toString());

		Point pRequestCount = Point.measurement("requestCount").addTag("name", name).addTag("node", node).setValue(requestCount).build();
		Point pErrorCount = Point.measurement("errorCount").addTag("name", name).addTag("node", node).setValue(errorCount).build();
		Point pOpen = Point.measurement("open").addTag("name", name).addTag("node", node).setValue(open).build();

		Point pRollingLatencyMean = Point.measurement("pRollingLatencyMean").addTag("name", name).addTag("node", node).setValue(rollingLatencyMean).build();
		// Point pRollingLatency =
		// Point.measurement("rollingLatency").addTag("name",
		// name).addTag("node", node).setValue(rollingLatency).build();
		Point pTotalLatencyMean = Point.measurement("totalLatencyMean").addTag("name", name).addTag("node", node).setValue(totalLatencyMean).build();
		// Point pTotalLatency =
		// Point.measurement("totalLatency").addTag("name", name).addTag("node",
		// node).setValue(totalLatency).build();

		BatchPoints batch = BatchPoints.database(this.databaseName).points(pRequestCount, pErrorCount, pOpen, pRollingLatencyMean, pTotalLatencyMean).build();

		// Save points into timeseries database

		this.addMeasuresCB.execute(future -> {

			this.influxDB.write(batch, ar -> {
				if (ar.succeeded()) {
					future.complete();
				} else {
					LOGGER.error(ar.cause().getMessage(), ar.cause());
					future.fail(ar.cause());
				}
			});

		}).setHandler(ar -> {

		});

	}

	@Override
	public void start() {

		this.addMeasuresCB = CircuitBreaker.create("edge.system.addMeasuresCB", vertx);

		HttpClientOptions influxDBOptions = new HttpClientOptions();
		influxDBOptions.setDefaultHost(config().getString("influxdb.host", "localhost"));
		influxDBOptions.setDefaultPort(config().getInteger("influxdb.port", 8086));

		this.databaseName = config().getString("influxdb.database", "edge_circuit_breaker");

		HttpClient client = vertx.createHttpClient(influxDBOptions);

		InfluxDbOptions options = new InfluxDbOptions().credentials(config().getString("influxdb.user"), config().getString("influxdb.password"));

		this.influxDB = InfluxDB.connect(client, options);

		Flowable.<JsonObject> create(emitter -> {

			MessageConsumer<JsonObject> consumer = this.vertx.eventBus().<JsonObject> consumer(CircuitBreakerOptions.DEFAULT_NOTIFICATION_ADDRESS, message -> {
				emitter.onNext(message.body());
			});

			emitter.setCancellable(consumer::unregister);

		}, BackpressureStrategy.BUFFER)//

				.flatMap(body -> {

					String state = body.getString("state");

					String name = body.getString("name");

					String node = body.getString("node");

					int requestCount = body.getInteger("rollingOperationCount", 0);
					int errorCount = body.getInteger("rollingErrorCount", 0);

					int rollingLatencyMean = body.getInteger("rollingLatencyMean", 0);

					int totalLatencyMean = body.getInteger("totalLatencyMean", 0);

					boolean open = state.equalsIgnoreCase(CircuitBreakerState.OPEN.toString());

					Point pRequestCount = Point.measurement("requestCount").addTag("name", name).addTag("node", node).setValue(requestCount).build();
					Point pErrorCount = Point.measurement("errorCount").addTag("name", name).addTag("node", node).setValue(errorCount).build();
					Point pOpen = Point.measurement("open").addTag("name", name).addTag("node", node).setValue(open).build();

					Point pRollingLatencyMean = Point.measurement("pRollingLatencyMean").addTag("name", name).addTag("node", node).setValue(rollingLatencyMean).build();

					Point pTotalLatencyMean = Point.measurement("totalLatencyMean").addTag("name", name).addTag("node", node).setValue(totalLatencyMean).build();


					return Observable.fromArray(pRequestCount, pErrorCount, pOpen, pRollingLatencyMean, pTotalLatencyMean).toFlowable(BackpressureStrategy.BUFFER);

				})//

				.buffer(5L, TimeUnit.SECONDS, 5000)//

				.map(points -> BatchPoints.database(databaseName).points(points).build() )//

				.retry()//
				
				.subscribe(batch -> {

					this.addMeasuresCB.execute(future -> {

						this.influxDB.write(batch, ar -> {
							if (ar.succeeded()) {
								future.complete();
							} else {
								LOGGER.error(ar.cause().getMessage(), ar.cause());
								future.fail(ar.cause());
							}
						});

					}).setHandler(ar -> {

					});

				});

	}

}
