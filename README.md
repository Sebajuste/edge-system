Edge System
------

Provide optional services for Edge platform.

### Circuit Breaker - Hystrix

Provide an hystrix stream on subpath `hystrix-metrics`

Configuration:

- **hystrix_http_stream** Enable the hystrix stream. *Default true*
- **hystrix.port** Define the HTTP port *Default 8081*

### Circuit Breaker - Metrics

Save metrics in a InfluxDB database of all Ciruit Breaker deployed on the cluster.

Configuration:

- **cb_storage** Enable circuit breaker storage *Default true*
- **influxdb.host** Host of influxDB database *Default localhost*
- **influxdb.database** Name of influxDB database *Default edge_circuit_breaker*
- **influxdb.user** Username of influxDB database *Require*
- **influxdb.password** Password of influxDB database *Require*
