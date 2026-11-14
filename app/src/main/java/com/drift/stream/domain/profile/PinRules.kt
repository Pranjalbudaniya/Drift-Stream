package com.drift.stream.domain.profile

const val PIN_LENGTH = 4
const val PIN_ATTEMPTS_PER_LOCK = 5

fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }
