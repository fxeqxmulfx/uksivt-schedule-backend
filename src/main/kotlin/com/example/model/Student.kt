package com.example.model

import com.example.repository.RepositoryStudent
import java.time.LocalDate
import java.time.Month
import java.util.*

object Student {
    suspend fun getAllGroup(): List<String> {
        return RepositoryStudent.getAllGroup().map {
            it.getString(0)
        }
    }

    private fun calculateCourse(group: String): Int? {
        val groupYear = "20${group[0].toString() + group[1]}".toIntOrNull() ?: return null
        val year = LocalDate.now().year
        val month = LocalDate.now().month
        if (month >= Month.AUGUST) {
            return year - groupYear + 1
        }
        return year - groupYear
    }

    internal fun getTimeByLessonNumberAndDay(lessonNumber: Int, dayOfWeek: Int, college_group: String): String {
        val course = calculateCourse(college_group) ?: ""
        return when (dayOfWeek) {
            6 -> when (lessonNumber) {
                0 -> "s08:00 e09:20"
                1 -> "s09:30 e10:50"
                2 -> "s11:00 e12:20"
                3 -> "s12:30 e13:50"
                4 -> "s14:00 e15:20"
                5 -> "s15:30 e16:50"
                6 -> "s17:00 e18:20"
                else -> ""
            }
            else -> when (lessonNumber) {
                0 -> "s07:50 e09:20"
                1 -> "s09:30 e10:15 s10:20 e11:05"
                2 -> when (course) {
                    1 -> "s11:15 e12:00 s12:45 e13:30"
                    2 -> "s11:15 e12:00 s12:05 e12:50"
                    3 -> "s12:00 e13:30"
                    4 -> "s12:00 e13:30"
                    else -> ""
                }
                3 -> "s13:35 e14:20 s14:25 e15:10"
                4 -> "s15:20 e16:50"
                5 -> "s17:00 e18:20"
                6 -> "s18:30 e19:50"
                else -> ""
            }
        }
    }


    suspend fun getAllLessonByGroup(
        collegeGroup: String,
        fromDate: LocalDate
    ): Map<Int, List<Map<String, Any>>> {
        return RepositoryStudent.getAllLessonByGroup(collegeGroup, fromDate, fromDate.plusDays(6))
            .groupBy { it.getInteger("day_of_week") }
            .map { pair ->
                pair.key to pair.value.sortedBy {
                    it.getInteger("lesson_number")
                }.map {
                    val time = getTimeByLessonNumberAndDay(
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