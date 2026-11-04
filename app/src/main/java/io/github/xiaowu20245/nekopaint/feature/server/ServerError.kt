package io.github.xiaowu20245.nekopaint.feature.server

import androidx.annotation.StringRes
import io.github.xiaowu20245.nekopaint.R

enum class ServerError(@param:StringRes val messageRes: Int) {
    EmptyHost(R.string.error_empty_host),
    InvalidHost(R.string.error_invalid_host),
    InvalidPort(R.string.error_invalid_port),
    Unreachable(R.string.error_unreachable),
    Timeout(R.string.error_timeout),
    Http(R.string.error_http),
    InvalidResponse(R.string.error_invalid_response),
    Parse(R.string.error_parse),
    Unknown(R.string.error_unknown),
}
