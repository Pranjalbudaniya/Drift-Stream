package com.drift.stream.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface ActiveProfileProvider {
    val activeProfileId: StateFlow<Long?>
}
