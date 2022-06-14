package todoapp

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.assertions.asClue
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.withCharset
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.testcontainers.containers.PostgreSQLContainer
import todoapp.jooq.tables.references.TODO
import java.net.ServerSocket
import java.nio.charset.StandardCharsets.UTF_8

class Spec : FunSpec({

    val testEnvironment = TestEnvironment()

    with(testEnvironment) {
        test("return empty todo list when no todo added") {
            val response = client.get("$applicationUrl/todo")
            response.asClue {
                it.status shouldBe HttpStatusCode.OK
                it.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
                it.bodyAsText() shouldBe """{"todo":[]}"""
            }
        }

        test("return todo resource when todo added") {
            val response = client.post("$applicationUrl/todo") {
                setBody("""{"todo":{"text":"Купить молока"}}""")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            response.asClue {
                it.status shouldBe HttpStatusCode.Created
                it.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
                it.bodyAsText() shouldBe """{"todo":{"id":1,"text":"Купить молока","done":false,"index":0}}"""
            }
        }

        test("return todo list with todo after todo added") {
            client.post("$applicationUrl/todo") {
                setBody("""{"todo":{"text":"Купить молока"}}""")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            val response = client.get("$applicationUrl/todo")
            response.asClue {
                it.status shouldBe HttpStatusCode.OK
                it.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
                it.bodyAsText() shouldBe """{"todo":[{"id":1,"text":"Купить молока","done":false,"index":0}]}"""
            }
        }

        test("return todo list with all todo after several todo added") {
            client.post("$applicationUrl/todo") {
                setBody("""{"todo":{"text":"Купить молока"}}""")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            client.post("$applicationUrl/todo") {
                setBody("""{"todo":{"text":"Купить хлеба"}}""")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            client.post("$applicationUrl/todo") {
                setBody("""{"todo":{"text":"Купить яйца"}}""")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            val response = client.get("$applicationUrl/todo")
            response.asClue {
                it.status shouldBe HttpStatusCode.OK
                it.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
                it.bodyAsText() shouldEqualJson """
                    |{
                    |   "todo":[
                    |       {"id":1,"text":"Купить молока","done":false,"index":0},
                    |       {"id":2,"text":"Купить хлеба","done":false,"index":1},
                    |       {"id":3,"text":"Купить яйца","done":false,"index":2}
                    |   ]
                    |}
                    |""".trimMargin()
            }
        }
    }

    beforeEach {
        testEnvironment.reset()
    }

    afterSpec {
        testEnvironment.close()
    }
})

class TestEnvironment {
    private val postgres = KPostgreSQLContainer("postgres:14.3")
        .withUsername("postgres")
        .withPassword("postgres")
        .also { it.start() }

    private val dataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = "postgres"
            password = "postgres"
            maximumPoolSize = 1
        }
    )

    private val env = Env(
        port = availablePort(),
        databaseUrl = postgres.jdbcUrl,
        databaseUsername = "postgres",
        databasePassword = "postgres",
    )

    val applicationUrl = "http://localhost:${env.port}"

    @OptIn(DelicateCoroutinesApi::class)
    private val applicationJob = GlobalScope.launch { applicationContext(env).use { it.run() } }

    val client = HttpClient(CIO) {
        expectSuccess = false
    }

    init {
        // await application is ready
        do {
            val result = kotlin.runCatching { runBlocking { client.get("$applicationUrl/todo") } }
        } while (result.isFailure)
    }

    fun reset() {
        with(DSL.using(dataSource, SQLDialect.POSTGRES)) {
            deleteFrom(TODO).execute()
            execute("SELECT setval('todo_id_seq', 1, false)")
        }
    }

    suspend fun close() {
        client.close()
        applicationJob.cancelAndJoin()
        dataSource.close()
        postgres.stop()
    }
}

class KPostgreSQLContainer(image: String) : PostgreSQLContainer<KPostgreSQLContainer>(image)

fun availablePort(): Int = ServerSocket(0).use { it.localPort }
