package todoapp

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import arrow.fx.coroutines.release
import brave.Tracing
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.flywaydb.core.Flyway
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import todoapp.infrastructure.*
import javax.sql.DataSource
import kotlin.concurrent.thread

fun main() = cancelOnShutdown {
    application(properties()).run()
}

val logger: Logger = LoggerFactory.getLogger("todoapp")

fun application(properties: ApplicationProperties): Resource<Application> = resource {
    logger.info("Application creating...")
    val dataSource = dataSource(properties.dataSourceProperties).bind()
    val transaction = Transaction(dataSource)
    val repository = Repository()
    val service = Service(transaction, repository)
    val routing = routing(
        todoCreateHandler(service),
        todoReadHandler(service),
        todoUpdateHandler(service),
        todoDeleteHandler(service)
    )
    val tracing = tracing(properties.tracingProperties).bind()
    val ktorServer = ktorServer(properties.ktorProperties, routing, tracing).bind()
    suspend {
        logger.info("Application start...")
        migrate(dataSource)
        ktorServer.start(wait = false)
        logger.info("Application started")
        awaitCancellation()
    }
} afterRelease { logger.info("Application stopped") }

fun dataSource(properties: DataSourceProperties): Resource<DataSource> = resource {
    HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = properties.url
            username = properties.username
            password = properties.password
            isAutoCommit = false
        }
    )
} release { it.close() }

fun ktorServer(
    properties: KtorProperties,
    routing: Routing.() -> Unit,
    tracing: Tracing
): Resource<ApplicationEngine> = resource {
    embeddedServer(Netty, port = properties.port) {
        installContentNegotiation()
        installStatusPages()
        installRouting(routing)
        installCallLogging()
        installTracing(tracing)
        installMetrics()
    }
} release { it.stop() }

fun KtorApplication.installContentNegotiation() = install(ContentNegotiation) { json() }

fun KtorApplication.installStatusPages() =
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Internal error", cause)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }

fun KtorApplication.installRouting(routing: Routing.() -> Unit) = install(Routing) {
    routing()
}

fun KtorApplication.installCallLogging() =
    install(CallLogging) {
        filter { call -> call.request.path() != "/metrics" }
    }

fun KtorApplication.installTracing(tracing: Tracing) =
    install(ZipkinServerTracing) {
        this.tracing = tracing
    }

fun KtorApplication.installMetrics() {
    val prometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = prometheusMeterRegistry
    }
    routing {
        get("/metrics") {
            call.respondText(prometheusMeterRegistry.scrape())
        }
    }
}

fun migrate(dataSource: DataSource) {
    Flyway.configure().dataSource(dataSource).load().migrate()
}

fun cancelOnShutdown(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking {
    val job = launch(Dispatchers.Default) {
        block()
    }
    Runtime.getRuntime().addShutdownHook(thread(name = "shutdown", start = false) {
        runBlocking {
            withTimeoutOrNull(10_000) {
                job.cancelAndJoin()
            } ?: logger.warn("Application stop timeout")
        }
    })
}

suspend fun Resource<Application>.run() = use { it() }

fun properties(): ApplicationProperties = ApplicationProperties(
    KtorProperties(port = 80),
    DataSourceProperties(
        url = System.getenv("DATABASE_URL"),
        username = System.getenv("DATABASE_USERNAME"),
        password = System.getenv("DATABASE_PASSWORD")
    ),
    TracingProperties(zipkinServerUrl = System.getenv("ZIPKIN_SERVER_URL"))
)

data class ApplicationProperties(
    val ktorProperties: KtorProperties,
    val dataSourceProperties: DataSourceProperties,
    val tracingProperties: TracingProperties
)

data class KtorProperties(val port: Int)

data class DataSourceProperties(
    val url: String,
    val username: String,
    val password: String
)

data class TracingProperties(
    val zipkinServerUrl: String
)

typealias Application = suspend () -> Unit

typealias KtorApplication = io.ktor.server.application.Application

infix fun <A> Resource<A>.afterRelease(block: () -> Unit): Resource<A> = resource {
    Resource({ }, { _, _ -> block() }).bind()
    bind()
}