package io.github.hchanjune.operationresult.core.providers

/**
 * Provides the issuer (actor) identity for the current operation execution.
 *
 * ## What is an issuer?
 * The issuer represents "who" initiated or is responsible for the operation.
 *
 * Typical examples include:
 * - An authenticated user ID or username
 * - A system account (e.g. scheduled job)
 * - An anonymous or unauthenticated client
 *
 * ## Purpose
 * Issuer information is commonly used for:
 * - Audit logging ("who performed this action")
 * - Operation result enrichment
 * - Security-sensitive traceability
 *
 * ## Implementations
 * Implementations depend on the application environment, such as:
 * - Spring Security authentication context
 * - Custom JWT parsing or API gateway headers
 * - Background/system task identifiers
 *
 * ## Customization
 * Applications may provide their own [IssuerProvider] bean to integrate with
 * their authentication or identity system.
 *
 * If no custom provider is configured, the library typically falls back to a
 * default issuer (e.g. `"anonymous"`).
 */
fun interface IssuerProvider {
    /**
     * Returns the current issuer identifier.
     *
     * This method should be lightweight and safe to call repeatedly.
     *
     * @return a string representing the current operation issuer.
     */
    fun currentIssuer(): String
}