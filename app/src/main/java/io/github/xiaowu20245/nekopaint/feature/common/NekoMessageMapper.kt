package io.github.xiaowu20245.nekopaint.feature.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey

/**
 * NekoMessageKey → strings.xml 的唯一映射入口。
 * data/network 层不引用 Android 资源；UI/feature 层只从这里取文案。
 */
object NekoMessageMapper {

    @StringRes
    fun stringRes(key: NekoMessageKey): Int = when (key) {
        NekoMessageKey.GENERIC_ERROR -> R.string.error_generic
        NekoMessageKey.SERVER_NOT_CONFIGURED -> R.string.error_server_not_configured
        NekoMessageKey.SERVER_CACHE_UNKNOWN -> R.string.error_cache_identity_unknown
        NekoMessageKey.SERVER_ENDPOINT_CHANGED -> R.string.error_server_endpoint_changed
        NekoMessageKey.SERVER_CONFIG_INVALID -> R.string.error_invalid_config
        NekoMessageKey.QUEUE_REFRESH_FAILED -> R.string.error_queue_refresh
        NekoMessageKey.HISTORY_REFRESH_FAILED -> R.string.error_history_refresh
        NekoMessageKey.HISTORY_LOAD_MORE_FAILED -> R.string.error_history_load_more
        NekoMessageKey.HISTORY_DELETE_FAILED -> R.string.error_history_delete
        NekoMessageKey.HISTORY_CLEAR_FAILED -> R.string.error_history_clear
        NekoMessageKey.HISTORY_DELETE_CURRENT_HOST -> R.string.error_history_delete_current_host
        NekoMessageKey.QUEUE_INTERRUPT_FAILED -> R.string.error_queue_interrupt
        NekoMessageKey.QUEUE_DELETE_FAILED -> R.string.error_queue_delete
        NekoMessageKey.CACHE_CLEAR_FAILED -> R.string.error_cache_clear
        NekoMessageKey.SERVER_SWITCHED -> R.string.error_server_switched
        NekoMessageKey.RECHECK_FOUND -> R.string.queue_recheck_found
        NekoMessageKey.RECHECK_NOT_FOUND -> R.string.queue_recheck_not_found
        NekoMessageKey.WORKFLOW_RECORD_NO_VALUES -> R.string.error_workflow_record_no_values
        NekoMessageKey.WORKFLOW_TEMPLATE_NOT_FOUND -> R.string.error_workflow_template_not_found
        NekoMessageKey.WORKFLOW_PARAMS_CHANGED -> R.string.error_workflow_params_changed
        NekoMessageKey.WORKFLOW_TASK_RECORD_MISSING -> R.string.error_workflow_task_record_missing
        NekoMessageKey.WORKFLOW_TASK_SUPERSEDED -> R.string.error_workflow_task_superseded
        NekoMessageKey.WORKFLOW_TASK_NOT_RETRYABLE -> R.string.error_workflow_task_not_retryable
        NekoMessageKey.WORKFLOW_REPAIR_REQUIRED -> R.string.error_workflow_repair_required
        NekoMessageKey.WORKFLOW_JSON_NOT_FOUND -> R.string.error_workflow_json_not_found
        NekoMessageKey.WORKFLOW_CANDIDATES_INVALID -> R.string.error_workflow_candidates_invalid
        NekoMessageKey.WORKFLOW_NO_SCALAR_PARAMS -> R.string.error_workflow_no_scalar_params
        NekoMessageKey.WORKFLOW_APPLY_PARAMS_FAILED -> R.string.error_workflow_apply_params
        NekoMessageKey.WORKFLOW_CLIENT_ID_FAILED -> R.string.error_workflow_client_id
        NekoMessageKey.WORKFLOW_LOCAL_RECORD_FAILED -> R.string.error_workflow_local_record
        NekoMessageKey.WORKFLOW_NETWORK_REQUEST_FAILED -> R.string.error_workflow_network_request
        NekoMessageKey.WORKFLOW_SUBMIT_UNKNOWN -> R.string.error_workflow_submit_unknown
        NekoMessageKey.WORKFLOW_SUBMIT_EMPTY_RESPONSE -> R.string.error_workflow_submit_empty
        NekoMessageKey.WORKFLOW_SERVER_REJECTED -> R.string.error_workflow_server_rejected
        NekoMessageKey.WORKFLOW_OLD_RECORD_CLEANUP_FAILED -> R.string.error_workflow_old_record_cleanup
        NekoMessageKey.WORKFLOW_VALIDATION_FAILED -> R.string.error_workflow_validation
        NekoMessageKey.WORKFLOW_IMPORT_FAILED -> R.string.error_workflow_import
        NekoMessageKey.WORKFLOW_IMPORT_FILE_TOO_LARGE -> R.string.error_workflow_import_too_large
        NekoMessageKey.WORKFLOW_IMPORT_ENV_UNSUPPORTED -> R.string.error_workflow_import_env
        NekoMessageKey.WORKFLOW_IMPORT_READ_FAILED -> R.string.error_workflow_import_read
        NekoMessageKey.WORKFLOW_NO_SERVER -> R.string.workflow_submit_no_server
        NekoMessageKey.WORKFLOW_UNKNOWN_RESULT -> R.string.error_workflow_unknown_result
        NekoMessageKey.WORKFLOW_FAILED -> R.string.error_workflow_failed
        NekoMessageKey.WORKFLOW_EXECUTION_VALUES_FAILED -> R.string.error_workflow_execution_values
        NekoMessageKey.WORKFLOW_UNKNOWN_CONFIRM -> R.string.error_workflow_unknown_confirm
        NekoMessageKey.OPERATION_ALREADY_RUNNING -> R.string.error_operation_already_running
        NekoMessageKey.OPERATION_CANCELLED -> R.string.error_operation_cancelled
        NekoMessageKey.OPERATION_EXCEPTION -> R.string.error_operation_exception
        NekoMessageKey.OPERATION_PARTIAL_FAILED -> R.string.error_operation_partial
        NekoMessageKey.WORKFLOW_RENAME_FAILED -> R.string.error_workflow_rename
        NekoMessageKey.WORKFLOW_COPY_FAILED -> R.string.error_workflow_copy
        NekoMessageKey.WORKFLOW_DELETE_FAILED -> R.string.error_workflow_delete
        NekoMessageKey.WORKFLOW_QUICK_RUN_START_FAILED -> R.string.error_workflow_quick_run_start
        NekoMessageKey.SETTINGS_AI_BASE_URL_REQUIRED -> R.string.error_ai_base_url_required
        NekoMessageKey.SETTINGS_AI_BASE_URL_INVALID -> R.string.error_ai_base_url_invalid
        NekoMessageKey.SETTINGS_DISCLOSURE_SAVE_FAILED -> R.string.error_ai_disclosure_save
        NekoMessageKey.SETTINGS_AI_CLEAR_FAILED -> R.string.error_ai_clear
        NekoMessageKey.SETTINGS_AI_KEY_CLEARED -> R.string.error_ai_key_cleared
        NekoMessageKey.SETTINGS_AI_OLD_KEY_CLEARED -> R.string.error_ai_old_key_cleared
        NekoMessageKey.SETTINGS_AI_OLD_CONFIG_INVALID -> R.string.error_ai_old_config_invalid
        NekoMessageKey.SETTINGS_HTTP_CONFIRM_SAVE_FAILED -> R.string.error_ai_http_confirm_save
        NekoMessageKey.SETTINGS_AI_TEST_FAILED -> R.string.error_ai_test_failed
        NekoMessageKey.SETTINGS_AI_SAVE_FAILED -> R.string.error_ai_save_failed
        NekoMessageKey.SETTINGS_AI_BLOCKED -> R.string.error_ai_blocked
        NekoMessageKey.SETTINGS_NEKO_SAVE_FAILED -> R.string.error_neko_settings_save
        NekoMessageKey.NODE_INSTALL_SERVER_REQUIRED -> R.string.error_node_install_server_required
        NekoMessageKey.NODE_INSTALL_TEMPLATE_NOT_FOUND -> R.string.error_node_install_template_not_found
        NekoMessageKey.NODE_INSTALL_ALREADY_COMPLETE -> R.string.error_node_install_already_complete
        NekoMessageKey.NODE_INSTALL_NO_MISSING -> R.string.error_node_install_no_missing
        NekoMessageKey.NODE_INSTALL_CONFIG_INVALID -> R.string.error_node_install_config_invalid
        NekoMessageKey.NODE_INSTALL_MANAGER_UNREACHABLE -> R.string.error_node_install_manager_unreachable
        NekoMessageKey.NODE_INSTALL_MAPPING_MISSING_PACKAGES -> R.string.error_node_install_mapping_missing
        NekoMessageKey.NODE_INSTALL_PLAN_EMPTY -> R.string.error_node_install_plan_empty
        NekoMessageKey.NODE_INSTALL_PLAN_NO_BINDING_NEW -> R.string.error_node_install_plan_no_binding_new
        NekoMessageKey.NODE_INSTALL_PLAN_NO_BINDING_RETRY -> R.string.error_node_install_plan_no_binding_retry
        NekoMessageKey.NODE_INSTALL_SERVER_CHANGED -> R.string.error_node_install_server_changed
        NekoMessageKey.NODE_INSTALL_SERVER_NOT_RECONNECTED -> R.string.error_node_install_server_not_reconnected
        NekoMessageKey.NODE_INSTALL_MAPPING_READ_FAILED -> R.string.error_node_install_mapping_read
        NekoMessageKey.NODE_INSTALL_MAPPING_EMPTY -> R.string.error_node_install_mapping_empty
        NekoMessageKey.NODE_INSTALL_MAPPING_UNSUPPORTED -> R.string.error_node_install_mapping_unsupported
        NekoMessageKey.NODE_INSTALL_MAPPING_HTTP_FAILED -> R.string.error_node_install_mapping_http
        NekoMessageKey.NODE_INSTALL_ALL_FAILED -> R.string.error_node_install_all_failed
        NekoMessageKey.NODE_INSTALL_MANAGER_FORBIDDEN -> R.string.error_node_install_manager_forbidden
        NekoMessageKey.NODE_INSTALL_MANAGER_DISABLED -> R.string.error_node_install_manager_disabled
        NekoMessageKey.NODE_INSTALL_MANAGER_HTTP -> R.string.error_node_install_manager_http
        NekoMessageKey.NODE_INSTALL_PACKAGE_FORBIDDEN -> R.string.error_node_install_package_forbidden
        NekoMessageKey.NODE_INSTALL_PACKAGE_NOT_FOUND -> R.string.error_node_install_package_not_found
        NekoMessageKey.NODE_INSTALL_PACKAGE_HTTP -> R.string.error_node_install_package_http
        NekoMessageKey.NODE_INSTALL_CHECK_FAILED -> R.string.error_node_install_check
        NekoMessageKey.NODE_INSTALL_FAILED -> R.string.error_node_install_failed
        NekoMessageKey.NODE_INSTALL_RECHECK_FAILED -> R.string.error_node_install_recheck
        NekoMessageKey.WORKFLOW_REPAIR_FAILED -> R.string.error_workflow_repair_failed
        NekoMessageKey.OPERATION_START_FAILED -> R.string.error_operation_start
        NekoMessageKey.MASK_UPLOAD_FAILED -> R.string.error_mask_upload
        NekoMessageKey.MASK_EXPORT_FAILED -> R.string.error_mask_export
        NekoMessageKey.IMAGE_UPLOAD_FAILED -> R.string.error_image_upload
    }
}

@Composable
fun NekoMessage.asString(): String {
    val id = NekoMessageMapper.stringRes(key)
    return if (args.isEmpty()) {
        stringResource(id)
    } else {
        stringResource(id, *args.toTypedArray())
    }
}

/** 非 Compose 环境（Chat 动作、后台结果）统一使用同一映射。 */
fun NekoMessage.asString(context: android.content.Context): String {
    val id = NekoMessageMapper.stringRes(key)
    return if (args.isEmpty()) {
        context.getString(id)
    } else {
        context.getString(id, *args.toTypedArray())
    }
}

class AndroidNekoMessageFormatter(
    private val context: android.content.Context,
) : io.github.xiaowu20245.nekopaint.core.common.NekoMessageFormatter {
    override fun format(message: NekoMessage): String = message.asString(context)
}
