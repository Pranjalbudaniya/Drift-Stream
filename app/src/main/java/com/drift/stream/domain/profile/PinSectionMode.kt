package com.drift.stream.domain.profile

import com.drift.stream.domain.model.Profile

enum class PinSectionMode { Full, OwnerManage, None }

fun pinSectionMode(
    target: Profile,
    active: Profile?,
    host: DeleteHost,
): PinSectionMode =
    when {
        target.isOwner -> PinSectionMode.Full
        host == DeleteHost.Picker -> PinSectionMode.OwnerManage
        active == null -> PinSectionMode.None
        active.id == target.id -> PinSectionMode.Full
        active.isOwner -> PinSectionMode.OwnerManage
        else -> PinSectionMode.None
    }
