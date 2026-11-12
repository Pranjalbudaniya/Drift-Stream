package com.drift.stream.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
data object Home

@Serializable
data object Discover

@Serializable
data object Library

@Serializable
data object Downloads

@Serializable
data object Settings

@Serializable
data class Addons(
    val prefillUrl: String? = null,
    val storeCategory: String? = null,
)

@Serializable
data class Detail(
    val type: String,
    val id: String,
)

@Serializable
data object Player

@Serializable
data object ProfilesList

@Serializable
data class ProfileEdit(
    val profileId: Long,
)

@Serializable
data object ProfileAdd
