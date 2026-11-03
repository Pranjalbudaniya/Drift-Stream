package com.drift.stream.domain.usecase

import com.drift.stream.domain.model.ListStatus
import com.drift.stream.domain.model.PosterMark

fun buildPosterMarks(
    statuses: Map<String, ListStatus>,
    watchedMovieIds: Set<String>,
): Map<String, PosterMark> {
    val marks = mutableMapOf<String, PosterMark>()

    for ((id, status) in statuses) {
        if (status == ListStatus.COMPLETED) {
            marks[id] = PosterMark.WATCHED
        } else if (status == ListStatus.WATCHING) {
            marks[id] = PosterMark.WATCHING
        }
    }

    for (id in watchedMovieIds) {
        marks[id] = PosterMark.WATCHED
    }

    return marks
}
