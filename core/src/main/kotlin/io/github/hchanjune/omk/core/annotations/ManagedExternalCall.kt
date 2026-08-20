package io.github.hchanjune.omk.core.annotations

/**
 * Span layer: `[EXT]` EXTERNAL — opens a child span for a method that calls another system
 * (`SpanKind.CLIENT` when bridged to OpenTelemetry).
 *
 * Annotate the method that actually issues the request, not the class that holds it: an EXTERNAL
 * span is only meaningful when it maps one-to-one onto a network round trip, and client classes
 * routinely contain methods that short-circuit (a cached token, a batched no-op) or never leave
 * the process at all. That is also why this differs from [ManagedRepository] and
 * [ManagedCacheRepository], which are class-level — every method of a repository does reach its
 * store, so the class-wide invariant holds there and does not here.
 *
 * The span measures the annotated method's execution. For a blocking client that reads the
 * response body inside the method, that is the round trip. Beware of calls whose execution is
 * deferred past the method return — a `RestClient` chain ending at `retrieve()` without a
 * terminal operator sends nothing, and the span will report a healthy near-zero call.
 *
 * @param destination the system being called, recorded as the `destination` tag.
 *                    Defaults to the declaring class's simple name.
 * @param route the endpoint being called, recorded as the `route` tag; omitted when blank.
 *              Use it to tell one endpoint of a destination from another
 *              (`/internal-service/consume` vs `/internal-service/refund`).
 *
 *              This MUST be a route **template**, never a resolved path — `/scenario/{id}/episodes/{seq}`,
 *              not `/scenario/01H8XK7.../episodes/3`. Tags become metric dimensions, so a
 *              resolved path creates one time series per id and blows up cardinality. Being an
 *              annotation attribute, the value is a compile-time constant and stays bounded by
 *              construction — keep it that way. See the cardinality rules on `MetricTags`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ManagedExternalCall(
    val destination: String = "", // fallback: declaring class simple name
    val route: String = ""        // route template only, e.g. "/scenario/{id}/episodes/{seq}"
)
