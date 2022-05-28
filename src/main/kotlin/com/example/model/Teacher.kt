package com.example.model

import com.example.repository.RepositoryTeacher
import java.time.LocalDate
import java.util.*

object Teacher {
    private val regex = " +".toRegex()

    suspend fun getAllTeacher(): SortedSet<String> {
        return RepositoryTeacher.getAllTeacher().map {
            it.getString(0)
                .replace('.', ' ')
                .replace(',', ' ')
                .split(regex)
                .joinToString(separator = " ")
                .trim()
        }.toSortedSet()
    }

    suspend fun getAllLessonByTeacher(
        teacher: String,
        fromDate: LocalDate
    ): Map<Int, List<Map<String, Any>>> {
        return RepositoryTeacher.getAllLessonByTeacher(teacher, fromDate, fromDate.plusDays(6))
            .groupBy { it.getInteger("day_of_week") }
            .map { pair ->
                pair.key to pair.value.sortedBy {
                    it.getInteger("lesson_number")
                }.map {
                    val time = Student.getTimeByLessonNumberAndDay(
                        it.getInteger("lesson_number"),
                        it.getInteger("day_of_week"),
                        it.getString("college_group"),
                    )
                    val map = it.toJson().map
                    map["time"] = time
                    map.toMap()
                }
            }
            .toMap()
    }
}