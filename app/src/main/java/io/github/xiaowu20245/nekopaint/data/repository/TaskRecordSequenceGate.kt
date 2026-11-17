package io.github.xiaowu20245.nekopaint.data.repository

/** 只接受递增 sequence，防止迟到的持久化事件覆盖/复活较新状态。 */
class TaskRecordSequenceGate {
    private var last = 0L

    fun accept(sequence: Long): Boolean {
        if (sequence <= last) return false
        last = sequence
        return true
    }
}
