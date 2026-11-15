package io.github.xiaowu20245.nekopaint.core.model.ai

/**
 * AI data disclosure acknowledgements are scoped to an exact endpoint origin
 * and a disclosure version. A changed origin or a future disclosure revision
 * therefore requires a fresh acknowledgement.
 */
object AiDisclosurePolicy {
    const val VERSION = 1

    fun key(origin: String): String = "v$VERSION|$origin"

    fun isAcknowledged(origin: String, acknowledgements: Set<String>): Boolean =
        key(origin) in acknowledgements
}
