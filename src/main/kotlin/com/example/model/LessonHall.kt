package com.example.model

import com.example.repository.RepositoryLessonHall
import java.time.LocalDate

object LessonHall {
    suspend fun getAllLessonHall(): List<String> {
        return RepositoryLessonHall.getAllLessonHall().map {
            it.getString(0)
        }
    }

    suspend fun getAllLessonByLessonHall(
        teacher: String,
        fromDate: LocalDate
    ): Map<Int, List<Map<String, Any>>> {
        return RepositoryLessonHall.getAllLessonByLessonHall(teacher, fromDate, fromDate.plusDays(6))
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