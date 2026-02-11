package io.github.hchanjune.operationresult.core.defaults

/**
 * Standard tag keys used by operation-result metrics.
 *
 * This object provides a shared vocabulary for metric tagging across
 * adapters (WebMVC, WebFlux, gRPC, batch jobs, etc.).
 *
 * Notes:
 * - Core only guarantees minimal tags such as [RESULT].
 * - Adapter modules may enrich additional tags depending on runtime context.
 * - Tags MUST remain low-cardinality.
 */
object MetricTagOption {

    /* ------------------------------------------------------------------------
     * Outcome / Classification
     * --------------------------------------------------------------------- */

    /** success | reject | failure */
    const val RESULT = "result"

    /** s2xx | s3xx | s4xx | s5xx */
    const val STATUS_GROUP = "status_group"

    /** simple exception name or category */
    const val EXCEPTION = "exception"

    /** error code (domain-specific, low-cardinality only) */
    const val ERROR_CODE = "error.code"

    /** error category (validation/auth/business/unexpected) */
    const val ERROR_CATEGORY = "error.category"


    /* ------------------------------------------------------------------------
     * Operation Identity (low-cardinality only)
     * --------------------------------------------------------------------- */

    /** operation name (stable logical identifier) */
    const val OPERATION = "operation"

    /** entrypoint name (controller/consumer/job) */
    const val ENTRYPOINT = "entrypoint"

    /** service name (application/service boundary) */
    const val SERVICE = "service"

    /** function name (method/usecase) */
    const val FUNCTION = "function"

    /** event name (message/event-driven entry) */
    const val EVENT = "event"


    /* ------------------------------------------------------------------------
     * HTTP / Web Tags (adapters only)
     * --------------------------------------------------------------------- */

    const val HTTP_METHOD = "http.method"

    /** normalized route template: /trees/{id} */
    const val HTTP_ROUTE = "http.route"

    /** raw uri (avoid high-cardinality!) */
    const val HTTP_URI = "http.uri"

    /** status code (avoid using raw code if possible, prefer STATUS_GROUP) */
    const val HTTP_STATUS = "http.status"

    /** client type: browser/mobile/api */
    const val HTTP_CLIENT = "http.client"


    /* ------------------------------------------------------------------------
     * Security / Actor (careful: must remain low-cardinality)
     * --------------------------------------------------------------------- */

    /** issuer type: anonymous/user/system */
    const val ISSUER_TYPE = "issuer.type"

    /** auth mechanism: jwt/session/api-key */
    const val AUTH_TYPE = "auth.type"

    /** tenant identifier (only if bounded!) */
    const val TENANT = "tenant"


    /* ------------------------------------------------------------------------
     * Runtime / Infrastructure
     * --------------------------------------------------------------------- */

    /** environment: local/dev/staging/prod */
    const val ENV = "env"

    /** instance id or pod name (bounded cardinality) */
    const val INSTANCE = "instance"

    /** deployment region */
    const val REGION = "region"

    /** availability zone */
    const val ZONE = "zone"


    /* ------------------------------------------------------------------------
     * Database / External Calls (optional)
     * --------------------------------------------------------------------- */

    /** db system: postgres/mongo/redis */
    const val DB_SYSTEM = "db.system"

    /** external dependency name */
    const val DEPENDENCY = "dependency"

    /** downstream service */
    const val DOWNSTREAM = "downstream"


    /* ------------------------------------------------------------------------
     * Performance Dimensions (bounded buckets only)
     * --------------------------------------------------------------------- */

    /** latency bucket: fast/normal/slow */
    const val LATENCY_BUCKET = "latency.bucket"

    /** payload size bucket: small/medium/large */
    const val PAYLOAD_BUCKET = "payload.bucket"
}
