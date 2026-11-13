package com.drift.stream.domain.profile

const val PIN_MIN_LENGTH = 4
const val PIN_MAX_LENGTH = 6
const val PIN_ATTEMPTS_PER_LOCK = 5

fun isValidPin(pin: String): Boolean = pin.length in PIN_MIN_LENGTH..PIN_MAX_LENGTH && pin.all { it in '0'..'9' }
