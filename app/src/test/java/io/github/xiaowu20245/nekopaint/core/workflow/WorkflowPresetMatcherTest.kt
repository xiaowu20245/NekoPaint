package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.PresetValueEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.SeedStrategy
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowPreset
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowPresetMatcherTest {

    private fun sampleCandidate(
        nodeId: String,
        inputName: String,
        label: String,
        classType: String,
        type: ParameterType = ParameterType.INT,
        group: ParameterGroup = ParameterGroup.SAMPLING,
        defaultValue: String = "20",
        value: String = defaultValue,
        selected: Boolean = true,
        readOnly: Boolean = false,
    ) = ParameterCandidate(
        id = "$nodeId:$inputName",
        nodeId = nodeId,
        inputName = inputName,
        label = label,
        type = type,
        group = group,
        defaultValue = defaultValue,
        value = value,
        selected = selected,
        classType = classType,
        readOnly = readOnly,
    )

    @Test
    fun `applyPreset matches exact candidateId and semantic fallback when nodeId changes`() {
        val originalCandidates = listOf(
            sampleCandidate("6", "text", "正向提示词", "CLIPTextEncode", ParameterType.MULTILINE_TEXT, ParameterGroup.PROMPT, "old", "1girl, silver hair"),
            sampleCandidate("3", "steps", "采样步数", "KSampler", ParameterType.INT, ParameterGroup.SAMPLING, "20", "32"),
            sampleCandidate("99", "old_param", "已删除参数", "OldNode", ParameterType.INT, ParameterGroup.OTHER, "1", "5"),
        )
        val preset = WorkflowPreset(
            id = "preset-1",
            name = "测试预设",
            entries = WorkflowPresetMatcher.buildEntries(originalCandidates),
        )

        // 模拟工作流修改：KSampler 节点从 #3 变成了 #15，删掉了 #99，新增了 #20 width
        val modifiedCandidates = listOf(
            sampleCandidate("6", "text", "正向提示词", "CLIPTextEncode", ParameterType.MULTILINE_TEXT, ParameterGroup.PROMPT, "default prompt", "default prompt"),
            sampleCandidate("15", "steps", "采样步数", "KSampler", ParameterType.INT, ParameterGroup.SAMPLING, "20", "20"),
            sampleCandidate("20", "width", "宽度", "EmptyLatentImage", ParameterType.INT, ParameterGroup.IMAGE, "1024", "1024"),
        )

        val applied = WorkflowPresetMatcher.applyPreset(modifiedCandidates, preset)
        assertEquals("1girl, silver hair", applied.first { it.id == "6:text" }.value)
        // #3:steps 不存在，但通过 KSampler + steps + 采样步数 语义回退匹配到了 #15:steps
        assertEquals("32", applied.first { it.id == "15:steps" }.value)
        // 新增节点保留默认值
        assertEquals("1024", applied.first { it.id == "20:width" }.value)
    }

    @Test
    fun `resetToDefault restores defaultValue for editable candidates`() {
        val candidates = listOf(
            sampleCandidate("3", "steps", "采样步数", "KSampler", defaultValue = "20", value = "35"),
        )
        val reset = WorkflowPresetMatcher.resetToDefault(candidates)
        assertEquals("20", reset.single().value)
    }

    @Test
    fun `applySeedForRun handles RANDOM INCREMENT and FIXED strategies`() {
        val template = WorkflowTemplate(
            id = "t1",
            name = "Test",
            sourceFileName = "test.json",
            sourceHash = "abc",
            importedAt = 1L,
            nodeCount = 2,
            reachableNodeCount = 2,
            candidates = listOf(
                sampleCandidate("3", "seed", "随机种子", "KSampler", defaultValue = "100", value = "100"),
                sampleCandidate("3", "steps", "采样步数", "KSampler", defaultValue = "20", value = "20"),
            ),
        )
        assertTrue(template.hasSeedCandidate)

        val inc0 = WorkflowPresetMatcher.applySeedForRun(template, SeedStrategy.INCREMENT, runIndex = 0)
        val inc2 = WorkflowPresetMatcher.applySeedForRun(template, SeedStrategy.INCREMENT, runIndex = 2)
        assertEquals("100", inc0.candidates.first { it.inputName == "seed" }.value)
        assertEquals("102", inc2.candidates.first { it.inputName == "seed" }.value)

        val fixed = WorkflowPresetMatcher.applySeedForRun(template, SeedStrategy.FIXED, runIndex = 3)
        assertEquals("100", fixed.candidates.first { it.inputName == "seed" }.value)

        val rnd = WorkflowPresetMatcher.applySeedForRun(
            template = template,
            strategy = SeedStrategy.RANDOM,
            runIndex = 0,
            randomSeedGenerator = { 777888999L },
        )
        assertEquals("777888999", rnd.candidates.first { it.inputName == "seed" }.value)

        val seedless = template.copy(candidates = template.candidates.filter { it.inputName != "seed" })
        assertFalse(seedless.hasSeedCandidate)
        assertSame(
            seedless,
            WorkflowPresetMatcher.applySeedForRun(seedless, SeedStrategy.RANDOM, runIndex = 0),
        )
    }

    @Test
    fun `buildEntries only snapshots editable selected candidates`() {
        val candidates = listOf(
            sampleCandidate("3", "steps", "采样步数", "KSampler", selected = true),
            sampleCandidate("4", "cfg", "引导系数", "KSampler", selected = false),
            sampleCandidate("5", "seed", "随机种子", "KSampler", selected = true, readOnly = true),
        )

        val entries = WorkflowPresetMatcher.buildEntries(candidates)

        assertEquals(listOf("3:steps"), entries.map { it.candidateId })
    }

    @Test
    fun `applyPreset restores selection for matched candidates`() {
        val candidates = listOf(
            sampleCandidate("3", "steps", "采样步数", "KSampler", selected = false),
            sampleCandidate("4", "cfg", "引导系数", "KSampler", selected = false),
        )
        val preset = WorkflowPreset(
            id = "p",
            name = "P",
            entries = listOf(
                PresetValueEntry(
                    candidateId = "3:steps",
                    classType = "KSampler",
                    inputName = "steps",
                    label = "采样步数",
                    value = "32",
                ),
            ),
        )

        val applied = WorkflowPresetMatcher.applyPreset(candidates, preset)

        assertTrue(applied.first { it.id == "3:steps" }.selected)
        assertEquals("32", applied.first { it.id == "3:steps" }.value)
        // 未命中的参数保持原选择状态
        assertFalse(applied.first { it.id == "4:cfg" }.selected)
    }

    @Test
    fun `applyPreset skips ambiguous semantic signatures`() {
        val candidates = listOf(
            sampleCandidate(
                "10", "text", "正向提示词", "CLIPTextEncode",
                ParameterType.MULTILINE_TEXT, ParameterGroup.PROMPT,
                defaultValue = "base", value = "keep-a", selected = false,
            ),
            sampleCandidate(
                "11", "text", "负向提示词", "CLIPTextEncode",
                ParameterType.MULTILINE_TEXT, ParameterGroup.PROMPT,
                defaultValue = "base", value = "keep-b", selected = false,
            ),
        )
        val preset = WorkflowPreset(
            id = "p",
            name = "P",
            entries = listOf(
                PresetValueEntry(
                    candidateId = "99:text",
                    classType = "CLIPTextEncode",
                    inputName = "text",
                    label = "旧标签",
                    value = "overwrite",
                ),
            ),
        )

        val applied = WorkflowPresetMatcher.applyPreset(candidates, preset)

        assertEquals("keep-a", applied.first { it.id == "10:text" }.value)
        assertEquals("keep-b", applied.first { it.id == "11:text" }.value)
        assertFalse(applied.any { it.selected })
    }
}
