package com.drift.stream.presentation.feature.addons

import com.drift.stream.domain.model.AppError
import com.drift.stream.presentation.common.toUserMessage as commonToUserMessage

typealias AddonErrorText = com.drift.stream.presentation.common.AddonErrorText

fun AppError.toUserMessage(): AddonErrorText = commonToUserMessage()
