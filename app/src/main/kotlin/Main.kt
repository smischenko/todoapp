package todoapp

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import arrow.fx.coroutines.release
import brave.Tracing
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.*
import org.flywaydb.core.Flyway
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import todoapp.domain.todoCreateUseCase
import todoapp.domain.todoDeleteUseCase
import todoapp.domain.todoReadUseCase
import todoapp.domain.todoUpdateUseCase
import todoapp.infrastructure.*
import javax.sql.DataSource
import kotlin.concurrent.thread

fun main() = cancelOnShutdown {
    application(properties()).use { awaitCancellation() }
}

val logger: Logger = LoggerFactory.getLogger("todoapp")

fun application(properties: ApplicationProperties): Resource<Unit> = resource {
    logger.info("Application starting with properties $properties")
    val dataSource = dataSource(properties.dataSourceProperties).bind()
        .also { migrate(it) }
    val transactionManager = transactionManager(dataSource)
    val insertTodo = insertTodo()
    val selectTodoCount = selectTodoCount()
    val selectTodo = selectTodo()
    val selectAllTodo = selectAllTodo()
    val updateTodo = updateTodo()
    val updateTodoList = updateTodoList()
    val deleteTodo = deleteTodo()
    val todoCreateUseCase = todoCreateUseCase(
        transactionManager,
        selectTodoCount,
        insertTodo
    )
    val todoReadUseCase = todoReadUseCase(transactionManager, selectAllTodo)
    val todoUpdateUseCase = todoUpdateUseCase(transactionManager, selectTodo, updateTodo)
    val todoDeleteUseCase = todoDeleteUseCase(transactionManager, selectTodo, deleteTodo, selectAllTodo, updateTodoList)
    val routing = routing(
        todoCreateHandler(todoCreateUseCase),
        todoReadHandler(todoReadUseCase),
        todoUpdateHandler(todoUpdateUseCase),
        todoDeleteHandler(todoDeleteUseCase)
    )
    val tracing = tracing(properties.tracingProperties).bind()
    ktorServer(properties.ktorProperties, routing, tracing).bind()
    logger.info("Application started")
}
    .release { logger.info("Application stopping") }
    .afterRelease { logger.info("Application stopped") }

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
    }.start()
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

typealias KtorApplication = Application

infix fun <A> Resource<A>.afterRelease(block: () -> Unit): Resource<A> = resource {
    Resource({ }, { _, _ -> block() }).bind()
    bind()
}