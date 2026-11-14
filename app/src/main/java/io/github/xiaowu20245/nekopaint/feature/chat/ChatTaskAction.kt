package io.github.xiaowu20245.nekopaint.feature.chat

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.NodeInstallPackage
import io.github.xiaowu20245.nekopaint.core.model.workflow.NodeInstallPlan
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowMode
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowPreset
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowPresetMatcher
import io.github.xiaowu20245.nekopaint.data.repository.NodeInstaller
import io.github.xiaowu20245.nekopaint.data.repository.QueueRepository
import io.github.xiaowu20245.nekopaint.data.repository.TaskManager
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowRepository
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.util.UUID

@Serializable
enum class ChatTaskAction(val apiValue: String) {
    RETRY("retry"),
    INTERRUPT("interrupt"),
    DELETE("delete"),
    SUBMIT("submit"),
    BATCH_RETRY("batch_retry"),
    BATCH_DELETE("batch_delete"),
    INSTALL_NODES("install_nodes"),
    ;

    companion object {
        fun fromApiValue(value: String?): ChatTaskAction? =
            entries.firstOrNull { it.apiValue == value }
    }
}

@Serializable
enum class ChatActionState {
    PENDING,
    EXECUTING,
    SUCCESS,
    FAILED,
    CANCELLED,
}

@Serializable
data class ChatPromptOption(
    val id: String,
    val label: String,
    val nodeId: String,
)

@Serializable
data class ChatSubmissionParameter(
    val candidateId: String,
    val value: String,
)

@Serializable
data class ChatParameterOverride(
    val candidateId: String,
    val label: String,
    val oldValue: String,
    val newValue: String,
)

@Serializable
data class ChatBatchTarget(
    val promptId: String,
    val title: String,
    val status: String,
)

@Serializable
data class ChatActionProposal(
    val id: String,
    val action: ChatTaskAction,
    val promptId: String,
    val title: String,
    val status: String,
    val state: ChatActionState = ChatActionState.PENDING,
    val resultMessage: String? = null,
    val workflowId: String? = null,
    val promptText: String? = null,
    val promptCandidateId: String? = null,
    val promptCandidateLabel: String? = null,
    val promptOptions: List<ChatPromptOption> = emptyList(),
    val parameterOverrides: List<ChatParameterOverride> = emptyList(),
    val presetSaved: Boolean = false,
    val presetName: String? = null,
    val presetSaving: Boolean = false,
    val presetError: String? = null,
    val batchTargets: List<ChatBatchTarget> = emptyList(),
    val installPackages: List<NodeInstallPackage> = emptyList(),
    val unresolvedNodeTypes: List<String> = emptyList(),
    val missingNodeTypeCount: Int = 0,
    val restartAfterInstall: Boolean = true,
) {
    val shortId: String
        get() = if (promptId.length <= 8) promptId else promptId.take(8)
}

/**
 * 校验模型提交的参数覆盖，只允许非提示词、非只读、非图片字段，并检查类型与范围。
 */
internal fun validateParameterOverrides(
    template: WorkflowTemplate,
    requests: List<ChatSubmissionParameter>,
): Result<List<ChatParameterOverride>> {
    if (requests.size > MAX_PARAMETER_OVERRIDES) {
        return Result.failure(
            IllegalStateException("一次最多覆盖 $MAX_PARAMETER_OVERRIDES 个参数"),
        )
    }
    val seen = mutableSetOf<String>()
    val overrides = mutableListOf<ChatParameterOverride>()
    requests.forEach { request ->
        val candidateId = request.candidateId.trim()
        if (candidateId.isEmpty()) {
            return Result.failure(IllegalStateException("参数 ID 不能为空"))
        }
        if (!seen.add(candidateId)) {
            return Result.failure(IllegalStateException("参数重复：$candidateId"))
        }
        val candidate = template.candidates.firstOrNull { it.id == candidateId }
            ?: return Result.failure(IllegalStateException("未知参数：$candidateId"))
        if (candidate.readOnly) {
            return Result.failure(
                IllegalStateException("参数「${candidate.label}」是只读的"),
            )
        }
        if (candidate.group == ParameterGroup.PROMPT) {
            return Result.failure(
                IllegalStateException("提示词请使用 prompt 字段，不要放进 parameters"),
            )
        }
        if (candidate.type == ParameterType.IMAGE) {
            return Result.failure(IllegalStateException("暂不支持覆盖图片参数"))
        }
        val normalized = normalizeParameterValue(candidate, request.value)
            ?: return Result.failure(
                IllegalStateException(
                    "参数「${candidate.label}」的值「${request.value.take(40)}」不符合要求",
                ),
            )
        overrides += ChatParameterOverride(
            candidateId = candidate.id,
            label = candidate.label,
            oldValue = candidate.value,
            newValue = normalized,
        )
    }
    return Result.success(overrides)
}

internal fun applyParameterOverrides(
    original: WorkflowTemplate,
    target: WorkflowTemplate,
    overrides: List<ChatParameterOverride>,
): Result<WorkflowTemplate> {
    val validated = validateParameterOverrides(
        template = original,
        requests = overrides.map { override ->
            ChatSubmissionParameter(
                candidateId = override.candidateId,
                value = override.newValue,
            )
        },
    ).getOrElse { return Result.failure(it) }
    val byId = validated.associateBy { it.candidateId }
    return Result.success(
        target.copy(
            candidates = target.candidates.map { candidate ->
                byId[candidate.id]?.let { override ->
                    candidate.copy(value = override.newValue, selected = true)
                } ?: candidate
            },
        ),
    )
}

internal fun renderParameterSchema(
    template: WorkflowTemplate,
    includeAdvanced: Boolean,
): String = buildString {
    appendLine("本地状态：工作流参数")
    appendLine("模板：${template.name}（${template.compatibility?.mode ?: WorkflowMode.UNKNOWN}）")
    appendLine("格式：candidateId | 标签 | 类型 | 当前值 | 约束")
    val parameters = template.candidates
        .asSequence()
        .filter { candidate ->
            !candidate.readOnly &&
                candidate.group != ParameterGroup.PROMPT &&
                candidate.type != ParameterType.IMAGE &&
                (
                    includeAdvanced ||
                        (candidate.group != ParameterGroup.ADVANCED &&
                            candidate.group != ParameterGroup.OTHER)
                    )
        }
        .take(MAX_PARAMETER_SCHEMA_ENTRIES)
        .toList()
    if (parameters.isEmpty()) {
        appendLine("没有可覆盖的参数。")
    } else {
        parameters.forEach { candidate ->
            append("- ${candidate.id} | ${candidate.label} | ${candidate.type.name} | ")
            append(candidate.value.replace(Regex("\\s+"), " ").take(80))
            parameterConstraintText(candidate)?.let { append(" | $it") }
            appendLine()
        }
    }
    append("覆盖参数时使用 candidateId 和字符串值，不要编造未列出的参数。")
}

private fun normalizeParameterValue(
    candidate: ParameterCandidate,
    rawValue: String,
): String? {
    val value = rawValue.trim()
    if (value.length > MAX_PARAMETER_VALUE_LENGTH) return null
    return when (candidate.type) {
        ParameterType.INT -> {
            if (!INTEGER_PATTERN.matches(value)) return null
            val number = value.toBigDecimalOrNull() ?: return null
            value.takeIf { withinConstraints(number, candidate) }
        }

        ParameterType.FLOAT -> {
            val number = value.toBigDecimalOrNull() ?: return null
            value.takeIf { withinConstraints(number, candidate) }
        }

        ParameterType.BOOLEAN -> value.lowercase().takeIf { it == "true" || it == "false" }

        ParameterType.ENUM -> candidate.constraints.options
            .firstOrNull { option -> option.equals(value, ignoreCase = true) }

        ParameterType.TEXT,
        ParameterType.MULTILINE_TEXT -> value

        ParameterType.IMAGE,
        ParameterType.UNKNOWN -> null
    }
}

private fun withinConstraints(
    value: BigDecimal,
    candidate: ParameterCandidate,
): Boolean {
    candidate.constraints.min?.let { min ->
        if (value < BigDecimal.valueOf(min)) return false
    }
    candidate.constraints.max?.let { max ->
        if (value > BigDecimal.valueOf(max)) return false
    }
    return true
}

private fun parameterConstraintText(candidate: ParameterCandidate): String? = buildList {
    val constraints = candidate.constraints
    if (constraints.min != null || constraints.max != null) {
        add("范围=${constraints.min ?: "-"}..${constraints.max ?: "-"}")
    }
    if (constraints.options.isNotEmpty()) {
        val options = constraints.options.take(8).joinToString(", ")
        val suffix = if (constraints.options.size > 8) ", ..." else ""
        add("选项=$options$suffix")
    }
}.takeIf { it.isNotEmpty() }?.joinToString("; ")

private val INTEGER_PATTERN = Regex("^-?\\d+$")
private const val MAX_PARAMETER_OVERRIDES = 20
private const val MAX_PARAMETER_VALUE_LENGTH = 2000
private const val MAX_PARAMETER_SCHEMA_ENTRIES = 80
private const val MAX_PRESET_NAME_LENGTH = 60
private const val MAX_BATCH_TASKS = 10

/**
 * 把模型的动作建议转换成经过本地校验的确认卡片，并在用户确认后执行。
 *
 * 校验与执行都重新读取任务数据源的实时状态，不信任模型提供的任务名或状态。
 */
class ChatTaskActionHandler(
    private val dataSource: ChatTaskActionDataSource,
    private val nodeInstaller: NodeInstaller? = null,
) {

    fun propose(action: ChatTaskAction, promptId: String): Result<ChatActionProposal> {
        val task = dataSource.findTask(promptId)
        val history = dataSource.findHistory(promptId)
        val running = dataSource.isRunning(promptId)
        val pending = dataSource.isPending(promptId)
        if (task == null && history == null && !running && !pending) {
            return Result.failure(IllegalStateException("找不到该任务"))
        }
        when (action) {
            ChatTaskAction.RETRY -> {
                if (task == null || task.values.isEmpty()) {
                    return Result.failure(IllegalStateException("找不到可重试的参数快照"))
                }
            }

            ChatTaskAction.INTERRUPT -> {
                if (!running) {
                    return Result.failure(IllegalStateException("该任务当前不在运行中"))
                }
            }

            ChatTaskAction.DELETE -> {
                if (!pending) {
                    return Result.failure(IllegalStateException("该任务当前不在等待队列中"))
                }
            }

            ChatTaskAction.SUBMIT -> {
                return Result.failure(IllegalStateException("请使用提交任务建议"))
            }

            ChatTaskAction.BATCH_RETRY,
            ChatTaskAction.BATCH_DELETE,
            ChatTaskAction.INSTALL_NODES,
            -> return Result.failure(
                IllegalStateException("请使用对应的专用建议"),
            )
        }
        val status = when {
            running -> "running"
            pending -> "queued"
            history != null -> history.status
            else -> task?.status ?: "unknown"
        }
        return Result.success(
            ChatActionProposal(
                id = UUID.randomUUID().toString(),
                action = action,
                promptId = promptId,
                title = task?.templateName?.takeIf { it.isNotBlank() } ?: "未命名任务",
                status = status,
            ),
        )
    }

    suspend fun execute(proposal: ChatActionProposal): Result<String> = when (proposal.action) {
        ChatTaskAction.RETRY -> retry(proposal)
        ChatTaskAction.INTERRUPT -> interrupt(proposal)
        ChatTaskAction.DELETE -> deletePending(proposal)
        ChatTaskAction.SUBMIT -> submit(proposal)
        ChatTaskAction.BATCH_RETRY -> batchRetry(proposal)
        ChatTaskAction.BATCH_DELETE -> batchDelete(proposal)
        ChatTaskAction.INSTALL_NODES -> installNodes(proposal)
    }

    suspend fun proposeSubmit(
        workflowKeyword: String?,
        prompt: String,
        parameters: List<ChatSubmissionParameter> = emptyList(),
    ): Result<ChatActionProposal> {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isBlank()) {
            return Result.failure(IllegalStateException("提示词不能为空"))
        }
        val template = resolveTemplate(workflowKeyword).getOrElse { return Result.failure(it) }
        val overrides = validateParameterOverrides(template, parameters)
            .getOrElse { return Result.failure(it) }

        val options = template.candidates
            .filter { candidate ->
                candidate.group == ParameterGroup.PROMPT &&
                    !candidate.readOnly &&
                    !isNegativePrompt(candidate)
            }
            .map { candidate ->
                ChatPromptOption(
                    id = candidate.id,
                    label = candidate.label,
                    nodeId = candidate.nodeId,
                )
            }
        if (options.isEmpty()) {
            return Result.failure(IllegalStateException("该模板没有可编辑的正面提示词"))
        }
        val selected = template.primaryPromptCandidateId
            ?.let { id -> options.firstOrNull { it.id == id } }
            ?: findPositivePromptCandidate(template)?.let { candidate ->
                options.firstOrNull { it.id == candidate.id }
            }
            ?: options.first()
        return Result.success(
            ChatActionProposal(
                id = UUID.randomUUID().toString(),
                action = ChatTaskAction.SUBMIT,
                promptId = "",
                title = template.name,
                status = "text_to_image",
                workflowId = template.id,
                promptText = trimmedPrompt,
                promptCandidateId = selected.id,
                promptCandidateLabel = selected.label,
                promptOptions = options,
                parameterOverrides = overrides,
            ),
        )
    }

    suspend fun proposeBatch(
        action: ChatTaskAction,
        promptIds: List<String>,
    ): Result<ChatActionProposal> {
        if (action != ChatTaskAction.BATCH_RETRY && action != ChatTaskAction.BATCH_DELETE) {
            return Result.failure(IllegalStateException("不支持的批量操作"))
        }
        val ids = promptIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (ids.isEmpty()) {
            return Result.failure(IllegalStateException("批量操作至少需要一个任务"))
        }
        if (ids.size > MAX_BATCH_TASKS) {
            return Result.failure(
                IllegalStateException("一次最多操作 $MAX_BATCH_TASKS 个任务"),
            )
        }

        val targets = mutableListOf<ChatBatchTarget>()
        val errors = mutableListOf<String>()
        ids.forEach { promptId ->
            val task = dataSource.findTask(promptId)
            val history = dataSource.findHistory(promptId)
            when (action) {
                ChatTaskAction.BATCH_RETRY -> {
                    val status = history?.status ?: task?.status.orEmpty()
                    when {
                        task == null || task.values.isEmpty() -> {
                            errors += "${shortId(promptId)} 缺少参数快照"
                        }

                        status != "failed" && status != "interrupted" -> {
                            errors += "${shortId(promptId)} 不是失败或中断状态"
                        }

                        else -> {
                            targets += ChatBatchTarget(
                                promptId = promptId,
                                title = task.templateName.ifBlank { "未命名任务" },
                                status = status,
                            )
                        }
                    }
                }

                ChatTaskAction.BATCH_DELETE -> {
                    if (!dataSource.isPending(promptId)) {
                        errors += "${shortId(promptId)} 不在等待队列"
                    } else {
                        targets += ChatBatchTarget(
                            promptId = promptId,
                            title = task?.templateName?.takeIf { it.isNotBlank() }
                                ?: "未命名任务",
                            status = "queued",
                        )
                    }
                }
            }
        }
        if (errors.isNotEmpty()) {
            return Result.failure(
                IllegalStateException(
                    "批量操作校验失败：" +
                        errors.take(3).joinToString("；") +
                        if (errors.size > 3) " 等 ${errors.size} 项" else "",
                ),
            )
        }
        return Result.success(
            ChatActionProposal(
                id = UUID.randomUUID().toString(),
                action = action,
                promptId = "",
                title = "${targets.size} 个任务",
                status = "batch",
                batchTargets = targets,
            ),
        )
    }

    suspend fun proposeNodeInstall(workflowKeyword: String?): Result<ChatActionProposal> {
        val installer = nodeInstaller
            ?: return Result.failure(IllegalStateException("节点安装服务未配置"))
        val template = resolveInstallTemplate(workflowKeyword).getOrElse { return Result.failure(it) }
        val plan = installer.preparePlan(template.id).getOrElse { return Result.failure(it) }
        return Result.success(
            ChatActionProposal(
                id = UUID.randomUUID().toString(),
                action = ChatTaskAction.INSTALL_NODES,
                promptId = "",
                title = plan.templateName,
                status = "missing_nodes",
                workflowId = plan.templateId,
                installPackages = plan.packages,
                unresolvedNodeTypes = plan.unresolvedNodeTypes,
                missingNodeTypeCount = plan.missingNodeTypes.size,
            ),
        )
    }

    private suspend fun resolveInstallTemplate(
        workflowKeyword: String?,
    ): Result<WorkflowTemplate> {
        val templates = dataSource.listTemplates()
        if (templates.isEmpty()) {
            return Result.failure(IllegalStateException("还没有导入工作流模板"))
        }
        val keyword = workflowKeyword?.trim().orEmpty()
        val matched = if (keyword.isBlank()) {
            templates
        } else {
            templates.filter { template ->
                template.id == keyword ||
                    template.name.equals(keyword, ignoreCase = true) ||
                    template.name.contains(keyword, ignoreCase = true)
            }
        }
        if (matched.isEmpty()) {
            return Result.failure(IllegalStateException("没有找到匹配的工作流模板"))
        }
        if (matched.size == 1) {
            return Result.success(matched.single())
        }
        val withMissing = matched.filter { it.missingNodeTypes.isNotEmpty() }
        return when {
            withMissing.isEmpty() -> Result.failure(
                IllegalStateException(
                    "匹配到多个工作流，请指定名称：" +
                        matched.joinToString("、") { it.name },
                ),
            )

            withMissing.size == 1 -> Result.success(withMissing.single())
            else -> Result.failure(
                IllegalStateException(
                    "匹配到多个缺少节点的工作流，请指定名称：" +
                        withMissing.joinToString("、") { it.name },
                ),
            )
        }
    }

    private suspend fun installNodes(proposal: ChatActionProposal): Result<String> {
        val installer = nodeInstaller
            ?: return Result.failure(IllegalStateException("节点安装服务未配置"))
        val templateId = proposal.workflowId
            ?: return Result.failure(IllegalStateException("安装建议缺少工作流"))
        if (proposal.installPackages.isEmpty()) {
            return Result.failure(IllegalStateException("安装计划为空"))
        }
        val plan = NodeInstallPlan(
            templateId = templateId,
            templateName = proposal.title,
            packages = proposal.installPackages,
            unresolvedNodeTypes = proposal.unresolvedNodeTypes,
        )
        return installer.install(
            plan = plan,
            restartAfterInstall = proposal.restartAfterInstall,
        ).fold(
            onSuccess = { outcome -> Result.success(outcome.summary()) },
            onFailure = { error ->
                Result.failure(
                    IllegalStateException(error.message ?: "节点安装失败"),
                )
            },
        )
    }

    suspend fun describeParameters(
        workflowKeyword: String?,
        includeAdvanced: Boolean = false,
    ): Result<String> {
        val template = resolveTemplate(workflowKeyword).getOrElse { return Result.failure(it) }
        return Result.success(renderParameterSchema(template, includeAdvanced))
    }

    private suspend fun resolveTemplate(workflowKeyword: String?): Result<WorkflowTemplate> {
        val templates = dataSource.listTemplates()
            .filter { it.compatibility?.mode == WorkflowMode.TEXT_TO_IMAGE }
        if (templates.isEmpty()) {
            return Result.failure(
                IllegalStateException("还没有可用的文生图模板，请先导入工作流"),
            )
        }
        val keyword = workflowKeyword?.trim().orEmpty()
        val matched = when {
            keyword.isBlank() && templates.size == 1 -> templates
            keyword.isBlank() -> return Result.failure(
                IllegalStateException(
                    "有多个文生图模板，请在提问里指定名称：" +
                        templates.joinToString("、") { it.name },
                ),
            )

            else -> templates.filter { template ->
                template.id == keyword ||
                    template.name.equals(keyword, ignoreCase = true) ||
                    template.name.contains(keyword, ignoreCase = true)
            }
        }
        if (matched.isEmpty()) {
            return Result.failure(IllegalStateException("没有找到匹配的文生图模板"))
        }
        if (matched.size > 1) {
            return Result.failure(
                IllegalStateException(
                    "匹配到多个文生图模板，请指定更准确的名称：" +
                        matched.joinToString("、") { it.name },
                ),
            )
        }
        return Result.success(matched.single())
    }

    private suspend fun retry(proposal: ChatActionProposal): Result<String> {
        val task = dataSource.findTask(proposal.promptId)
            ?: return Result.failure(IllegalStateException("找不到该任务的参数快照"))
        if (task.values.isEmpty()) {
            return Result.failure(IllegalStateException("该任务没有保存参数，无法重试"))
        }
        return when (val result = dataSource.resubmit(task)) {
            is WorkflowSubmitResult.Success -> {
                dataSource.refresh()
                Result.success(
                    "已重新提交「${proposal.title}」，新任务 ID：${result.promptId.take(8)}",
                )
            }

            WorkflowSubmitResult.NoServer -> Result.failure(
                IllegalStateException("尚未连接服务器"),
            )

            is WorkflowSubmitResult.Validation -> Result.failure(
                IllegalStateException(result.summary),
            )

            is WorkflowSubmitResult.Error -> Result.failure(
                IllegalStateException(result.message),
            )
        }
    }

    private suspend fun interrupt(proposal: ChatActionProposal): Result<String> {
        if (!dataSource.isRunning(proposal.promptId)) {
            return Result.failure(IllegalStateException("该任务已经不在运行中"))
        }
        return dataSource.interrupt(proposal.promptId).fold(
            onSuccess = {
                dataSource.refresh()
                Result.success("已请求中断「${proposal.title}」")
            },
            onFailure = { error ->
                Result.failure(
                    IllegalStateException(error.message ?: "服务器拒绝了中断请求"),
                )
            },
        )
    }

    private suspend fun deletePending(proposal: ChatActionProposal): Result<String> {
        if (!dataSource.isPending(proposal.promptId)) {
            return Result.failure(IllegalStateException("该任务已经不在等待队列中"))
        }
        return dataSource.deletePending(proposal.promptId).fold(
            onSuccess = {
                dataSource.refresh()
                Result.success("已删除等待中的任务「${proposal.title}」")
            },
            onFailure = { error ->
                Result.failure(
                    IllegalStateException(error.message ?: "服务器拒绝了删除请求"),
                )
            },
        )
    }

    private suspend fun batchRetry(proposal: ChatActionProposal): Result<String> {
        var succeeded = 0
        val errors = mutableListOf<String>()
        proposal.batchTargets.forEach { target ->
            val task = dataSource.findTask(target.promptId)
            if (task == null || task.values.isEmpty()) {
                errors += "${target.title}：缺少参数"
                return@forEach
            }
            when (val result = dataSource.resubmit(task)) {
                is WorkflowSubmitResult.Success -> succeeded++
                WorkflowSubmitResult.NoServer -> errors += "未连接服务器"
                is WorkflowSubmitResult.Validation -> {
                    errors += "${target.title}：${result.summary}"
                }

                is WorkflowSubmitResult.Error -> {
                    errors += "${target.title}：${result.message}"
                }
            }
        }
        dataSource.refresh()
        return batchResult("重试", succeeded, errors)
    }

    private suspend fun batchDelete(proposal: ChatActionProposal): Result<String> {
        var succeeded = 0
        val errors = mutableListOf<String>()
        proposal.batchTargets.forEach { target ->
            if (!dataSource.isPending(target.promptId)) {
                errors += "${target.title}：已不在等待队列"
                return@forEach
            }
            dataSource.deletePending(target.promptId).fold(
                onSuccess = { succeeded++ },
                onFailure = { error ->
                    errors += "${target.title}：${error.message ?: "删除失败"}"
                },
            )
        }
        dataSource.refresh()
        return batchResult("删除", succeeded, errors)
    }

    private fun batchResult(
        actionLabel: String,
        succeeded: Int,
        errors: List<String>,
    ): Result<String> {
        if (succeeded == 0) {
            return Result.failure(
                IllegalStateException(
                    "批量$actionLabel 失败：" +
                        errors.take(3).joinToString("；").ifBlank { "没有可执行的任务" },
                ),
            )
        }
        val base = "已批量$actionLabel $succeeded 个任务"
        return if (errors.isEmpty()) {
            Result.success(base)
        } else {
            Result.success(
                "$base；${errors.size} 个失败：" +
                    errors.take(2).joinToString("；"),
            )
        }
    }

    private fun shortId(promptId: String): String =
        if (promptId.length <= 8) promptId else promptId.take(8)

    private suspend fun submit(proposal: ChatActionProposal): Result<String> {
        val template = prepareSubmission(proposal).getOrElse { return Result.failure(it) }
        return when (val result = dataSource.submit(template)) {
            is WorkflowSubmitResult.Success -> {
                dataSource.refresh()
                Result.success(
                    "已提交「${template.name}」，任务 ID：${result.promptId.take(8)}",
                )
            }

            WorkflowSubmitResult.NoServer -> Result.failure(
                IllegalStateException("尚未连接服务器"),
            )

            is WorkflowSubmitResult.Validation -> Result.failure(
                IllegalStateException(result.summary),
            )

            is WorkflowSubmitResult.Error -> Result.failure(
                IllegalStateException(result.message),
            )
        }
    }

    suspend fun savePreset(
        proposal: ChatActionProposal,
        name: String,
    ): Result<String> {
        if (proposal.action != ChatTaskAction.SUBMIT) {
            return Result.failure(IllegalStateException("只有提交建议可以保存为预设"))
        }
        if (proposal.state != ChatActionState.SUCCESS) {
            return Result.failure(IllegalStateException("请先提交任务再保存预设"))
        }
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_PRESET_NAME_LENGTH) {
            return Result.failure(IllegalStateException("预设名称长度需在 1–60 个字符之间"))
        }
        val templateId = proposal.workflowId
            ?: return Result.failure(IllegalStateException("提交建议缺少工作流"))
        val template = dataSource.getTemplate(templateId)
            ?: return Result.failure(IllegalStateException("找不到原工作流模板"))
        if (template.presets.any { it.name.equals(trimmed, ignoreCase = true) }) {
            return Result.failure(IllegalStateException("同名预设已存在，请换一个名称"))
        }
        val prepared = prepareSubmission(proposal).getOrElse { return Result.failure(it) }
        val preset = WorkflowPreset(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            entries = WorkflowPresetMatcher.buildEntries(prepared.candidates),
            updatedAt = System.currentTimeMillis(),
        )
        val updated = template.copy(presets = template.presets + preset)
        dataSource.updateTemplate(updated).getOrElse { error ->
            return Result.failure(
                IllegalStateException(error.message ?: "保存预设失败"),
            )
        }
        return Result.success("已保存预设「$trimmed」")
    }

    private suspend fun prepareSubmission(
        proposal: ChatActionProposal,
    ): Result<WorkflowTemplate> {
        val templateId = proposal.workflowId
            ?: return Result.failure(IllegalStateException("提交建议缺少工作流"))
        val template = dataSource.getTemplate(templateId)
            ?: return Result.failure(IllegalStateException("找不到原工作流模板"))
        if (template.compatibility?.mode != WorkflowMode.TEXT_TO_IMAGE) {
            return Result.failure(IllegalStateException("该模板当前不是文生图模式"))
        }
        val prompt = proposal.promptText?.trim().orEmpty()
        if (prompt.isBlank()) {
            return Result.failure(IllegalStateException("提示词为空，无法提交"))
        }
        val target = proposal.promptCandidateId
            ?.let { id ->
                template.candidates.firstOrNull { candidate ->
                    candidate.id == id &&
                        candidate.group == ParameterGroup.PROMPT &&
                        !candidate.readOnly &&
                        !isNegativePrompt(candidate)
                }
            }
            ?: findPositivePromptCandidate(template)
            ?: return Result.failure(IllegalStateException("模板缺少可编辑的正面提示词"))
        val updated = applyPrompt(template, target.id, prompt)
            ?: return Result.failure(IllegalStateException("提示词字段已经变化，请重新发起提交"))
        return applyParameterOverrides(template, updated, proposal.parameterOverrides)
    }

    private fun findPositivePromptCandidate(template: WorkflowTemplate) =
        template.candidates
            .asSequence()
            .filter { candidate ->
                candidate.group == ParameterGroup.PROMPT &&
                    !candidate.readOnly &&
                    !isNegativePrompt(candidate)
            }
            .maxByOrNull { candidate -> promptScore(candidate) }

    private fun promptScore(candidate: ParameterCandidate): Int {
        val label = candidate.label.lowercase()
        var score = 0
        if (label.contains("正面")) score += 100
        if (label.contains("positive")) score += 50
        if (candidate.inputName == "positive") score += 50
        if (label == "提示词" || label == "prompt") score += 40
        if (label.contains("画师") || label.contains("artist") || label.contains("style")) {
            score -= 30
        }
        if (label.contains("文本")) score -= 10
        if (candidate.value.length > 40) score += 10
        return score
    }

    private fun applyPrompt(
        template: WorkflowTemplate,
        candidateId: String,
        prompt: String,
    ): WorkflowTemplate? {
        if (template.candidates.none { it.id == candidateId }) return null
        return template.copy(
            candidates = template.candidates.map { candidate ->
                if (candidate.id == candidateId) {
                    candidate.copy(value = prompt, selected = true)
                } else {
                    candidate
                }
            },
        )
    }

    private fun isNegativePrompt(candidate: ParameterCandidate): Boolean =
        candidate.label.contains("负") ||
            candidate.label.contains("negative", ignoreCase = true) ||
            candidate.inputName.contains("negative", ignoreCase = true)
}

interface ChatTaskActionDataSource {
    fun findTask(promptId: String): TaskRecord?

    fun findHistory(promptId: String): HistoryEntry?

    fun isRunning(promptId: String): Boolean

    fun isPending(promptId: String): Boolean

    suspend fun resubmit(task: TaskRecord): WorkflowSubmitResult

    suspend fun interrupt(promptId: String): Result<Unit>

    suspend fun deletePending(promptId: String): Result<Unit>

    suspend fun listTemplates(): List<WorkflowTemplate>

    suspend fun getTemplate(templateId: String): WorkflowTemplate?

    suspend fun submit(template: WorkflowTemplate): WorkflowSubmitResult

    suspend fun updateTemplate(template: WorkflowTemplate): Result<Unit>

    fun refresh()
}

class DefaultChatTaskActionDataSource(
    private val taskManager: TaskManager,
    private val workflowRepository: WorkflowRepository,
    private val queueRepository: QueueRepository,
) : ChatTaskActionDataSource {

    override fun findTask(promptId: String): TaskRecord? =
        taskManager.tasks.value.firstOrNull { it.promptId == promptId }

    override fun findHistory(promptId: String): HistoryEntry? =
        taskManager.history.value.firstOrNull { it.promptId == promptId }

    override fun isRunning(promptId: String): Boolean =
        taskManager.queue.value?.running?.any { it.promptId == promptId } == true

    override fun isPending(promptId: String): Boolean =
        taskManager.queue.value?.pending?.any { it.promptId == promptId } == true

    override suspend fun resubmit(task: TaskRecord): WorkflowSubmitResult =
        workflowRepository.resubmit(task)

    override suspend fun interrupt(promptId: String): Result<Unit> =
        queueRepository.interrupt(promptId)

    override suspend fun deletePending(promptId: String): Result<Unit> =
        queueRepository.deletePending(promptId)

    override suspend fun listTemplates(): List<WorkflowTemplate> =
        workflowRepository.listTemplates()

    override suspend fun getTemplate(templateId: String): WorkflowTemplate? =
        workflowRepository.getTemplate(templateId)

    override suspend fun submit(template: WorkflowTemplate): WorkflowSubmitResult =
        workflowRepository.submit(template)

    override suspend fun updateTemplate(template: WorkflowTemplate): Result<Unit> =
        runCatching { workflowRepository.updateTemplate(template) }

    override fun refresh() {
        taskManager.refresh()
    }
}
