package todoapp

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import arrow.fx.coroutines.release
import brave.Span
import brave.Tracing
import brave.handler.SpanHandler
import brave.propagation.TraceContext
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpSendPipeline
import io.ktor.client.request.header
import io.ktor.http.Headers
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.Hook
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.util.AttributeKey
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import org.slf4j.MDC
import zipkin2.reporter.Sender
import zipkin2.reporter.brave.AsyncZipkinSpanHandler
import zipkin2.reporter.urlconnection.URLConnectionSender
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

fun tracing(properties: TracingProperties): Resource<Tracing> = resource {
    val spanSender = spanSender(properties.zipkinServerUrl).bind()
    val spanHandler = spanHandler(spanSender).bind()
    Tracing.newBuilder()
        .localServiceName("todoapp")
        .addSpanHandler(spanHandler)
        .build()
} release { it.close() }

fun spanSender(zipkinServerUrl: String): Resource<Sender> = resource {
    URLConnectionSender.create("${zipkinServerUrl}/api/v2/spans")
} release { it.close() }

fun spanHandler(spanSender: Sender): Resource<SpanHandler> = resource {
    AsyncZipkinSpanHandler.newBuilder(spanSender).build()
} release { it.close() }

val ZipkinServerTracing = createApplicationPlugin("ZipkinServerTracing", ::ZipkinServerTracingConfiguration) {
    val tracing = pluginConfig.tracing
    on(TracingHook) { call, block ->
        withSpan(tracing, call, block)
    }
}

class ZipkinServerTracingConfiguration {
    lateinit var tracing: Tracing
}

private object TracingHook: Hook<suspend (ApplicationCall, suspend () -> Unit) -> Unit> {
    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend (ApplicationCall, suspend () -> Unit) -> Unit
    ) {
        pipeline.intercept(ApplicationCallPipeline.Monitoring) {
            handler(call, ::proceed)
        }
    }
}

private suspend inline fun withSpan(tracing: Tracing, call: ApplicationCall, crossinline block: suspend () -> Unit) {
    val spanContext = tracing.propagation().extractor(Headers::get).extract(call.request.headers)
    val span: Span = tracing.tracer().nextSpan(spanContext)
    span.name(call.request.httpMethod.value + " " + call.request.path())
    span.kind(Span.Kind.SERVER)
    span.tag("http.method", call.request.httpMethod.value)
    span.tag("http.path", call.request.path())
    span.start()
    try {
        withContext(TraceContextElement(span.context())) {
            block()
        }
    } finally {
        val statusCode = call.response.status()?.value
        if (statusCode != null) {
            span.tag("http.status_code", "$statusCode")
        }
        if (statusCode == null || statusCode >= 400) {
            span.tag("error", "true")
        }
        span.finish()
    }
}

data class TraceContextElement(val traceContext: TraceContext?) : ThreadContextElement<Map<String, String?>>,
    AbstractCoroutineContextElement(TraceContextElement) {

    private val mdcEntries = mapOf(
        "traceId" to traceContext?.traceIdString(),
        "spanId" to traceContext?.spanIdString()
    )

    override fun restoreThreadContext(context: CoroutineContext, oldState: Map<String, String?>) {
        setCurrent(oldState)
    }

    override fun updateThreadContext(context: CoroutineContext): Map<String, String?> {
        val state = getCurrent()
        setCurrent(mdcEntries)
        return state
    }

    private fun getCurrent(): Map<String, String?> {
        val contextMap = MDC.getCopyOfContextMap()
        return mdcEntries.mapValues { (key, _) -> contextMap?.get(key) }
    }

    private fun setCurrent(state: Map<String, String?>) {
        state.forEach(::putToMDC)
    }

    private fun putToMDC(key: String, value: String?) {
        if (value != null) {
            MDC.put(key, value)
        } else {
            MDC.remove(key)
        }
    }

    companion object Key : CoroutineContext.Key<TraceContextElement>
}

suspend inline fun <T> Tracing.span(name: String, crossinline block: suspend () -> T): T {
    val traceContext = coroutineContext[TraceContextElement]?.traceContext
    val span: Span = traceContext?.let { tracer().newChild(it) } ?: tracer().newTrace()
    span.name(name)
    span.start()
    return try {
        withContext(TraceContextElement(span.context())) {
            block()
        }
    } finally {
        span.finish()
    }
}

class ZipkinClientTracing private constructor(private val tracing: Tracing){

    class Configuration {
        lateinit var tracing: Tracing
    }

    companion object Feature : HttpClientPlugin<Configuration, ZipkinClientTracing> {
        override val key: AttributeKey<ZipkinClientTracing> = AttributeKey("ZipkinClientTracing")

        override fun install(plugin: ZipkinClientTracing, scope: HttpClient) {
            val tracing = plugin.tracing
            scope.sendPipeline.intercept(HttpSendPipeline.State) {
                coroutineContext[TraceContextElement]?.traceContext?.let { traceContext ->
                    tracing.propagation().injector(HttpRequestBuilder::header).inject(traceContext, context)
                }
            }
        }

        override fun prepare(block: Configuration.() -> Unit): ZipkinClientTracing {
            val configuration = Configuration().also(block)
            return ZipkinClientTracing(configuration.tracing)
        }
    }
}