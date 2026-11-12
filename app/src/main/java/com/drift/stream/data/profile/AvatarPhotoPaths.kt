package com.drift.stream.data.profile

import java.io.File

fun avatarPhotoFile(
    rootDir: File,
    profileId: Long,
): File = File(File(rootDir, "avatars"), "$profileId.jpg")
