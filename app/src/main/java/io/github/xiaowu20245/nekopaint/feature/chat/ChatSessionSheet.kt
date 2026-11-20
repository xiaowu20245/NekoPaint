package io.github.xiaowu20245.nekopaint.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.xiaowu20245.nekopaint.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatSessionSheet(
    sessions: List<ChatSessionSummary>,
    currentSessionId: String?,
    sending: Boolean,
    actionExecuting: Boolean,
    isNewSession: Boolean,
    onDismiss: () -> Unit,
    onNewSession: () -> Unit,
    onSelect: (String) -> Unit,
    onRename: (ChatSessionSummary) -> Unit,
    onTogglePin: (ChatSessionSummary) -> Unit,
    onDelete: (ChatSessionSummary) -> Unit,
) {
    val enabled = !sending && !actionExecuting
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(sessions, query) {
        if (query.isBlank()) {
            sessions
        } else {
            sessions.filter { it.title.contains(query, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chat_sessions),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (!isNewSession) {
                    TextButton(onClick = onNewSession, enabled = enabled) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.chat_session_new))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.chat_session_search)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(
                                    R.string.chat_session_search_clear,
                                ),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
            )
            Spacer(Modifier.height(8.dp))

            when {
                sessions.isEmpty() -> Text(
                    text = stringResource(R.string.chat_sessions_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )

                filtered.isEmpty() -> Text(
                    text = stringResource(R.string.chat_session_no_match),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )

                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(filtered, key = { it.id }) { session ->
                        val selected = session.id == currentSessionId
                        var menuExpanded by remember { mutableStateOf(false) }
                        val kind = stringResource(
                            if (session.promptId != null) {
                                R.string.chat_session_task
                            } else {
                                R.string.chat_session_global
                            },
                        )
                        val sessionModeLabel = stringResource(
                            if (session.mode == ChatMode.AGENT) {
                                R.string.chat_mode_agent
                            } else {
                                R.string.chat_mode_chat
                            },
                        )
                        val count = stringResource(
                            R.string.chat_session_count,
                            session.messageCount,
                        )
                        Surface(
                            onClick = { onSelect(session.id) },
                            enabled = enabled,
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                            } else {
                                Color.Transparent
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    start = 12.dp,
                                    top = 8.dp,
                                    bottom = 8.dp,
                                    end = 4.dp,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (session.pinned) {
                                            Icon(
                                                imageVector = Icons.Outlined.Star,
                                                contentDescription = stringResource(
                                                    R.string.chat_session_pinned,
                                                ),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = session.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        text = "$sessionModeLabel · $kind · $count",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = stringResource(
                                            R.string.chat_session_current,
                                        ),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Box {
                                    IconButton(
                                        onClick = { menuExpanded = true },
                                        enabled = enabled,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.MoreVert,
                                            contentDescription = stringResource(
                                                R.string.chat_more,
                                            ),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(R.string.chat_session_rename))
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                onRename(session)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(
                                                        if (session.pinned) {
                                                            R.string.chat_session_unpin
                                                        } else {
                                                            R.string.chat_session_pin
                                                        },
                                                    ),
                                                )
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                onTogglePin(session)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = stringResource(
                                                        R.string.chat_session_delete,
                                                    ),
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                onDelete(session)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

