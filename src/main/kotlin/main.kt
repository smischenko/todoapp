package todoapp

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import arrow.fx.coroutines.release
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
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
import javax.sql.DataSource
import kotlin.concurrent.thread

val logger: Logger = LoggerFactory.getLogger("todoapp")

fun main() = cancelOnShutdown {
    applicationContext(env()).use { it.run() }
}

data class Env(
    val port: Int,
    val databaseUrl: String,
    val databaseUsername: String,
    val databasePassword: String
)

fun applicationContext(env: Env): Resource<ApplicationContext> = resource {
    val dataSource = dataSource(env).bind()
    val routes = Routes(dataSource)
    val ktorServer = ktorServer(env, routes).bind()
    ApplicationContext(dataSource, ktorServer)
} release { logger.info("Application stopped") }

fun dataSource(env: Env): Resource<DataSource> = resource {
    HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = env.databaseUrl
            username = env.databaseUsername
            password = env.databasePassword
            isAutoCommit = false
        }
    )
} release { it.close() }

fun ktorServer(env: Env, routes: Routes): Resource<ApplicationEngine> = resource {
    embeddedServer(Netty, port = env.port) {
        installContentNegotiation()
        installStatusPages()
        installRouting(routes)
    }
} release { it.stop() }

fun Application.installContentNegotiation() = install(ContentNegotiation) { json() }

fun Application.installStatusPages() =
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Internal error", cause)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }

fun Application.installRouting(routes: Routes) = install(Routing) { routes() }

data class ApplicationContext(
    val dataSource: DataSource,
    val ktorServer: ApplicationEngine
)

suspend fun ApplicationContext.run() {
    migrate(dataSource)
    ktorServer.start(wait = false)
    logger.info("Application started")
    awaitCancellation()
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

fun env(): Env = Env(
    port = 80,
    databaseUrl = System.getenv("DATABASE_URL"),
    databaseUsername = System.getenv("DATABASE_USERNAME"),
    databasePassword = System.getenv("DATABASE_PASSWORD"),
)
