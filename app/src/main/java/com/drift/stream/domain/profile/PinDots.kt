package com.drift.stream.domain.profile

fun pinDotSlots(typed: Int): Int = typed.coerceIn(PIN_MIN_LENGTH, PIN_MAX_LENGTH)
