package io.github.xiaowu20245.nekopaint.data.repository

data class TaskRequestToken(
    val identity: ServerIdentity,
    val generation: Long,
)

object TaskRequestGuard {
    fun isCurrent(
        token: TaskRequestToken,
        currentIdentity: ServerIdentity?,
        currentGeneration: Long,
    ): Boolean = token.identity == currentIdentity && token.generation == currentGeneration
}
