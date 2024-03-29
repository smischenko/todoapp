package todoapp

import com.github.tomakehurst.wiremock.client.WireMock
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.assertions.asClue
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.contentType
import io.ktor.http.withCharset
import kotlinx.coroutines.*
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import todoapp.infrastructure.TracingProperties
import java.net.ServerSocket
import java.nio.charset.StandardCharsets.UTF_8

class Spec : FunSpec({

    val testEnvironment = TestEnvironment()

    with(testEnvironment) {
        test("return empty todo list when no todo added") {
            val response = client.get("$applicationUrl/todo")
            response.asClue {
                it.status shouldBe OK
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
                it.status shouldBe Created
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
                it.status shouldBe OK
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
                it.status shouldBe OK
                it.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
                it.bodyAsText() shouldEqualJson """
                    {
                       "todo":[
                           {"id":1,"text":"Купить молока","done":false,"index":0},
                           {"id":2,"text":"Купить хлеба","done":false,"index":1},
                           {"id":3,"text":"Купить яйца","done":false,"index":2}
                       ]
                    }
                    """.trimIndent()
            }
        }

        test("return todo resource when todo updated") {
            client.post("$applicationUrl/todo") {
                setBody("""{"todo":{"text":"Купить молока"}}""")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            val response = client.put("$applicationUrl/todo/1") {
                setBody("""{"todo":{"done":true}}""")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            response.asClue {
                it.status shouldBe OK
                it.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
                it.bodyAsText() shouldBe """{"todo":{"id":1,"text":"Купить молока","done":true,"index":0}}"""
            }
        }

        test("return todo list with todo after todo updated") {
            client.post("$applicationUrl/todo") {
                setBody("""{"todo":{"text":"Купить молока"}}""")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            client.put("$applicationUrl/todo/1") {
                setBody("""{"todo":{"done":true}}""")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            val response = client.get("$applicationUrl/todo")
            response.asClue {
                it.status shouldBe OK
                it.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
                it.bodyAsText() shouldBe """{"todo":[{"id":1,"text":"Купить молока","done":true,"index":0}]}"""
            }
        }

        test("return Ok when todo deleted") {
            client.post("$applicationUrl/todo") {
                setBody("""{"todo":{"text":"Купить молока"}}""")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            val response = client.delete("$applicationUrl/todo/1")
            response.asClue {
                it.status shouldBe OK
            }
        }

        test("return empty list after todo deleted") {
            client.post("$applicationUrl/todo") {
                setBody("""{"todo":{"text":"Купить молока"}}""")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            client.delete("$applicationUrl/todo/1")
            val response = client.get("$applicationUrl/todo")
            response.asClue {
                it.status shouldBe OK
                it.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
                it.bodyAsText() shouldBe """{"todo":[]}"""
            }
        }

        test("return updated list after todo deleted") {
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
            client.delete("$applicationUrl/todo/2")
            val response = client.get("$applicationUrl/todo")
            response.asClue {
                it.status shouldBe OK
                it.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
                it.bodyAsText() shouldEqualJson """
                    {
                       "todo":[
                           {"id":1,"text":"Купить молока","done":false,"index":0},
                           {"id":3,"text":"Купить яйца","done":false,"index":1}
                       ]
                    }
                    """.trimIndent()
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

    private val wireMockContainer = WireMockContainer("wiremock/wiremock:2.32.0-alpine")
        .withClasspathResourceMapping("wiremock", "/home/wiremock", BindMode.READ_ONLY)
        .also { it.start() }

    val wireMock = WireMock(wireMockContainer.host, wireMockContainer.port)

    private val applicationProperties = ApplicationProperties(
        KtorProperties(port = availablePort()),
        DataSourceProperties(
            url = postgres.jdbcUrl,
            username = "postgres",
            password = "postgres",
        ),
        TracingProperties(zipkinServerUrl = "${wireMockContainer.url}/zipkin")
    )

    val applicationUrl = "http://localhost:${applicationProperties.ktorProperties.port}"

    @OptIn(DelicateCoroutinesApi::class)
    private val applicationJob = GlobalScope.launch { application(applicationProperties).use { awaitCancellation() } }

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
            execute("DELETE FROM todo")
            execute("SELECT setval('todo_id_seq', 1, false)")
        }
        wireMock.resetToDefaultMappings()
    }

    suspend fun close() {
        client.close()
        applicationJob.cancelAndJoin()
        dataSource.close()
        postgres.stop()
        wireMockContainer.stop()
    }
}

class KPostgreSQLContainer(image: String) : PostgreSQLContainer<KPostgreSQLContainer>(image)

class WireMockContainer(image: String) : GenericContainer<WireMockContainer>(image) {
    init {
        addExposedPort(8080)
    }

    val port: Int
        get() = getMappedPort(8080)

    val url: String
        get() = "http://$host:$port"
}

fun availablePort(): Int = ServerSocket(0).use { it.localPort }
