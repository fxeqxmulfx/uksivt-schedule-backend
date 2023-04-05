package com.example.module

import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.Tuple
import kotlinx.coroutines.*
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.nio.file.Files
import java.nio.file.Path
import java.time.DayOfWeek

object ScheduleParser {
    suspend fun run() {
        coroutineScope {
            launch {
                while (true) {
                    try {
                        val lessons = parseFile(XSSFWorkbook(withContext(Dispatchers.IO) {
                            Files.newInputStream(
                                Path.of("/home/misha/IdeaProjects/uksivt-schedule/src/main/resources/Общеобразовательное 22-23 2 сем (проект).xlsx")
                            )
                        })).toMutableList()
                        lessons += parseFile(XSSFWorkbook(withContext(Dispatchers.IO) {
                            Files.newInputStream(
                                Path.of("/home/misha/IdeaProjects/uksivt-schedule/src/main/resources/ВТ 2 семестр 22-23 (проект).xlsx")
                            )
                        }))
                        lessons += parseFile(XSSFWorkbook(withContext(Dispatchers.IO) {
                            Files.newInputStream(
                                Path.of("/home/misha/IdeaProjects/uksivt-schedule/src/main/resources/Юристы 2 семестр 22-23 (проект).xlsx")
                            )
                        }))
                        lessons += parseFile(XSSFWorkbook(withContext(Dispatchers.IO) {
                            Files.newInputStream(
                                Path.of("/home/misha/IdeaProjects/uksivt-schedule/src/main/resources/Экономисты 2 семестр 22-23 (проект).xlsx")
                            )
                        }))
                        lessons += parseFile(XSSFWorkbook(withContext(Dispatchers.IO) {
                            Files.newInputStream(
                                Path.of("/home/misha/IdeaProjects/uksivt-schedule/src/main/resources/Программирование 2 семестр 22-23 (проект).xlsx")
                            )
                        }))
                        putIntoDb(lessons)
                        println("schedule ok")
                    } catch (e: Exception) {
                        println(e.toString())
                    }
                    delay(3600000L)
                }
            }
        }
    }

    private suspend fun putIntoDb(lessons: List<Lesson>) {
        var tempClient: SqlClient? = null
        try {
            val client = DataBase.getClient()
            tempClient = client

            client.preparedQuery("delete from lesson where college_group = $1")
                .executeBatch(lessons.map { Tuple.of(it.collegeGroup) }.toSet().toList()).await()
            client.preparedQuery(
                """
                    insert into lesson(college_group, day_of_week, lesson_type, lesson_number, 
                    lesson, teacher, lesson_hall) 
                    values($1, $2, $3, $4, $5, $6, $7)
                """.trimIndent()
            ).executeBatch(lessons.map { it.toTuple() }).await()
        } finally {
            tempClient?.close()?.await()
        }
    }

    data class Lesson(
        val collegeGroup: String, val dayOfWeek: DayOfWeek,
        val lessonType: LessonType, val lessonNumber: Int,
        val lesson: String, val teacher: String,
        val lessonHall: String
    ) {
        fun toTuple(): Tuple {
            return Tuple.of(
                this.collegeGroup, this.dayOfWeek.value,
                this.lessonType.toString(), this.lessonNumber,
                this.lesson, this.teacher,
                this.lessonHall,
            )
        }
    }

    enum class LessonType {
        None,
        NotEven,
        Even,
    }

    private fun parseFile(workbook: XSSFWorkbook): List<Lesson> {
        val groups: MutableMap<String, Int> = mutableMapOf()
        return workbook.flatMap { sheet ->
            sheet.take(2).flatMap { row ->
                row.mapIndexed { cellIndex, cell ->
                    if (cell.stringCellValue.isNotBlank()) {
                        val group = cell.stringCellValue.uppercase()
                        if (group !in groups) {
                            groups[group] = 0
                        }
                        groups[group] = groups[group]!! + 1
                        return@mapIndexed parseSheetColumn(sheet, cellIndex, groups[group] == 1, group)
                    }
                    return@mapIndexed null
                }
            }
        }.filterNotNull().flatten()
    }

    private fun parseSheetColumn(
        sheet: Sheet, cellNumber: Int,
        firstTime: Boolean, group: String,
    ): List<Lesson> {
        val result: MutableList<Lesson> = mutableListOf()
        sheet.drop(2).chunked(4).forEachIndexed { rowIndex, rows ->
            val rowsString = rows.map { it.getCell(cellNumber).valueToString() }
            if (rows.getOrNull(1)?.getCell(cellNumber)?.cellStyle?.borderBottom == BorderStyle.NONE) {
                val lesson = rowsString.firstOrNull { it.isNotBlank() } ?: ""
                if (lesson.isNotBlank()) {
                    val teacher = rowsString.firstOrNull { it.isNotBlank() && it != lesson } ?: ""
                    val lessonHall =
                        rows.map { it.getCell(cellNumber + 3).valueToString() }.firstOrNull { it.isNotBlank() } ?: ""
                    result.add(
                        Lesson(
                            collegeGroup = group,
                            lesson = lesson,
                            teacher = teacher,
                            lessonHall = lessonHall,
                            lessonType = LessonType.None,
                            dayOfWeek = getDayOfWeek(rowIndex, firstTime),
                            lessonNumber = getLessonNumber(rowIndex)
                        )
                    )
                }
            } else {
                val lessonNotEven = rowsString[0]
                if (lessonNotEven.isNotBlank()) {
                    val teacherNotEven = rowsString[1]
                    val lessonHallNotEven =
                        rows.map { it.getCell(cellNumber + 3).valueToString() }.take(2).firstOrNull { it.isNotBlank() }
                            ?: ""
                    result.add(
                        Lesson(
                            collegeGroup = group,
                            lesson = "$lessonNotEven (Нечетная неделя)",
                            teacher = teacherNotEven,
                            lessonHall = lessonHallNotEven,
                            lessonType = LessonType.NotEven,
                            dayOfWeek = getDayOfWeek(rowIndex, firstTime),
                            lessonNumber = getLessonNumber(rowIndex)
                        )
                    )
                }
                val lessonEven = rowsString[0]
                if (lessonEven.isNotBlank()) {
                    val teacherEven = rowsString[1]
                    val lessonHallEven =
                        rows.map { it.getCell(cellNumber + 3).valueToString() }.drop(2).firstOrNull { it.isNotBlank() }
                            ?: ""
                    result.add(
                        Lesson(
                            collegeGroup = group,
                            lesson = "$lessonEven (Четная неделя)",
                            teacher = teacherEven,
                            lessonHall = lessonHallEven,
                            lessonType = LessonType.Even,
                            dayOfWeek = getDayOfWeek(rowIndex, firstTime),
                            lessonNumber = getLessonNumber(rowIndex)
                        )
                    )
                }
            }
        }
        return result.toList()
    }

    private fun Cell?.valueToString(): String {
        if (this == null) {
            return ""
        }
        return when (cellType) {
            CellType.STRING -> stringCellValue
            CellType.NUMERIC -> numericCellValue.toInt().toString()
            else -> ""
        }
    }

    private fun getDayOfWeek(row: Int, firstTime: Boolean): DayOfWeek {
        if (row <= 6) {
            if (firstTime) {
                return DayOfWeek.MONDAY
            }
            return DayOfWeek.THURSDAY

        }
        if (row <= 13) {
            if (firstTime) {
                return DayOfWeek.TUESDAY
            }
            return DayOfWeek.FRIDAY
        }
        if (row <= 20) {
            if (firstTime) {
                return DayOfWeek.WEDNESDAY
            }
            return DayOfWeek.SATURDAY
        }
        return DayOfWeek.SUNDAY
    }

    private fun getLessonNumber(row: Int): Int {
        if (row == 0 || row == 7 || row == 14) {
            return 0
        }
        if (row == 1 || row == 8 || row == 15) {
            return 1
        }
        if (row == 2 || row == 9 || row == 16) {
            return 2
        }
        if (row == 3 || row == 10 || row == 17) {
            return 3
        }
        if (row == 4 || row == 11 || row == 18) {
            return 4
        }
        if (row == 5 || row == 12 || row == 19) {
            return 5
        }
        if (row == 6 || row == 13 || row == 20) {
            return 6
        }
        return 10
    }
}
