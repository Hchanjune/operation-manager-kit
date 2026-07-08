package io.github.hchanjune.omk.reactive.filter

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.contants.OperationOutcome
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.IssuerProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TelemetryPropagationProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.reactive.ReactiveOperations
import org.springframework.http.HttpMethod
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class ManagedContextWebFilter(
    private val contextProvider: ManagedContextProvider,
    private val propagationProvider: TelemetryPropagationProvider,
    private val traceIdProvider: TraceIdProvider,
    private val causationIdProvider: CausationIdProvider,
    private val compositeHook: OperationHook,
    private val issuerProvider: IssuerProvider = IssuerProvider { "anonymous" },
    private val generateWhenMissing: Boolean = true,
    private val excludeOptions: Boolean = false
) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val request = exchange.request

        if (excludeOptions && request.method == HttpMethod.OPTIONS) {
            return chain.filter(exchange)
        }

        val context = contextProvider.provide().apply {
            val extractedTraceId = propagationProvider.extractTraceId { request.headers.getFirst(it) }
            val extractedCausationId = propagationProvider.extractParentId { request.headers.getFirst(it) }
            injectTraceId(extractedTraceId ?: if (generateWhenMissing) traceIdProvider.provideTraceId() else "")
            injectCausationId(extractedCausationId ?: if (generateWhenMissing) causationIdProvider.provideCausationId() else "")
            injectIssuer(issuerProvider.currentIssuer())
            injectIp(resolveClientIp(request))
            injectProtocol("HTTP")
            injectType("API")
            injectHttpInfo(uri = request.uri.path, method = request.method.name())
        }

        context.start()

        exchange.response.beforeCommit {
            propagationProvider.inject(
                traceId = context.traceId,
                spanId = context.rootSpan?.spanId ?: context.causationId
            ) { name, value -> exchange.response.headers.set(name, value) }
            val statusCode = exchange.response.statusCode?.value() ?: 200
            context.injectStatusCode(statusCode)
            context.end()
            if (context.outcome == OperationOutcome.SERVER_ERROR) {
                compositeHook.onFailure(context, context.capturedException ?: RuntimeException("HTTP $statusCode"))
            } else {
                compositeHook.onSuccess(context)
            }
            Mono.empty()
        }

        return chain.filter(exchange)
            .contextWrite(reactor.util.context.Context.of(ReactiveOperations.CONTEXT_KEY, context))
    }

    /** 프록시/로드밸런서 뒤에 있을 수 있으니 X-Forwarded-For를 우선 확인하고, 없으면 소켓 주소를 쓴다. */
    private fun resolveClientIp(request: ServerHttpRequest): String {
        val forwardedFor = request.headers.getFirst("X-Forwarded-For")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        return forwardedFor ?: request.remoteAddress?.address?.hostAddress ?: ""
    }
}
