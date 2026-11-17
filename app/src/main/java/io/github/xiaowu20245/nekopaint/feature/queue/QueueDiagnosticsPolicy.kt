package io.github.xiaowu20245.nekopaint.feature.queue

import io.github.xiaowu20245.nekopaint.data.repository.TaskRecordWriteState

/** Queue 只展示持久化失败；后续 Saved/Deleted 会自然清除横幅。 */
internal fun queueRecordWriteError(state: TaskRecordWriteState): String? =
    (state as? TaskRecordWriteState.Failed)?.message
