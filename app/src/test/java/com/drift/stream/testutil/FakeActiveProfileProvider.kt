package com.drift.stream.testutil

import com.drift.stream.domain.repository.ActiveProfileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeActiveProfileProvider(
    initialId: Long? = 1L,
) : ActiveProfileProvider {
    private val _activeProfileId = MutableStateFlow(initialId)
    override val activeProfileId: StateFlow<Long?> = _activeProfileId.asStateFlow()

    fun setActive(id: Long?) {
        _activeProfileId.value = id
    }
}
