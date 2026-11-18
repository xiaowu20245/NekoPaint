package io.github.xiaowu20245.nekopaint.feature.workflow

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowDirtyPolicyTest {

    @Test
    fun editingValueMarksDirty() {
        assertEquals(
            WorkflowDirtyFlags(dirty = true, saved = false),
            WorkflowDirtyPolicy.editValue(),
        )
    }

    @Test
    fun selectingPresetPersistsImmediately() {
        assertEquals(
            WorkflowDirtyFlags(dirty = false, saved = true),
            WorkflowDirtyPolicy.selectPreset(),
        )
    }

    @Test
    fun savingNewPresetClearsDirtyWithoutGenericSavedNotice() {
        assertEquals(
            WorkflowDirtyFlags(dirty = false, saved = false),
            WorkflowDirtyPolicy.saveNewPreset(),
        )
    }

    @Test
    fun metadataPatchesPreserveCurrentDirtyState() {
        assertEquals(
            WorkflowDirtyFlags(dirty = true, saved = false),
            WorkflowDirtyPolicy.renameOrDeletePreset(currentDirty = true),
        )
        assertEquals(
            WorkflowDirtyFlags(dirty = true, saved = false),
            WorkflowDirtyPolicy.patchTagGroups(currentDirty = true),
        )
        assertEquals(
            WorkflowDirtyFlags(dirty = false, saved = false),
            WorkflowDirtyPolicy.renameOrDeletePreset(currentDirty = false),
        )
    }

    @Test
    fun explicitSaveClearsDirtyAndShowsSaved() {
        assertEquals(
            WorkflowDirtyFlags(dirty = false, saved = true),
            WorkflowDirtyPolicy.explicitSaveSuccess(),
        )
    }
}
