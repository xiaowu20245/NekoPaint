package io.github.xiaowu20245.nekopaint.feature.workflow

internal data class WorkflowDirtyFlags(
    val dirty: Boolean,
    val saved: Boolean,
)

/** 工作流编辑动作的 dirty/saved 语义（纯策略，便于测试矩阵）。 */
internal object WorkflowDirtyPolicy {

    fun editValue() = WorkflowDirtyFlags(dirty = true, saved = false)

    fun selectPreset() = WorkflowDirtyFlags(dirty = false, saved = true)

    fun saveNewPreset() = WorkflowDirtyFlags(dirty = false, saved = false)

    fun renameOrDeletePreset(currentDirty: Boolean) =
        WorkflowDirtyFlags(dirty = currentDirty, saved = false)

    fun patchTagGroups(currentDirty: Boolean) =
        WorkflowDirtyFlags(dirty = currentDirty, saved = false)

    fun explicitSaveSuccess() = WorkflowDirtyFlags(dirty = false, saved = true)
}
