package com.example.module

import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.Tuple
import kotlinx.coroutines.*
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Sheet
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
                                Path.of("/home/misha/IdeaProjects/uksivt-schedule/src/main/resources/Общеобразовательное 22-23 1сем.xlsx")
                            )
                        })).toMutableList()
                        lessons += parseFile(XSSFWorkbook(withContext(Dispatchers.IO) {
                            Files.newInputStream(
                                Path.of("/home/misha/IdeaProjects/uksivt-schedule/src/main/resources/Отделение ВТ 1 сем 22-23.xlsx")
                            )
                        }))
                        lessons += parseFile(XSSFWorkbook(withContext(Dispatchers.IO) {
                            Files.newInputStream(
                                Path.of("/home/misha/IdeaProjects/uksivt-schedule/src/main/resources/Отделение права 1 сем 2022-2023.xlsx")
                            )
                        }))
                        lessons += parseFile(XSSFWorkbook(withContext(Dispatchers.IO) {
                            Files.newInputStream(
                                Path.of("/home/misha/IdeaProjects/uksivt-schedule/src/main/resources/Отделение Э и ЗИО 1 сем 22-23.xlsx")
                            )
                        }))
                        lessons += parseFile(XSSFWorkbook(withContext(Dispatchers.IO) {
                            Files.newInputStream(
                                Path.of("/home/misha/IdeaProjects/uksivt-schedule/src/main/resources/Программирование 1 семестр 22-23.xlsx")
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
        var lesson: String? = null
        var teacher: String? = null
        var lessonHall: String? = null
        var lastRowIndex = 0

        val clearData = {
            lesson = null
            teacher = null
            lessonHall = null
            lastRowIndex = 0
        }

        val result: MutableList<Lesson> = mutableListOf()
        sheet.drop(2).forEachIndexed { rowIndex, row ->
            val cellLessonHall: Cell? = row.getCell(cellNumber + 3)
            val valueLessonHall = when (cellLessonHall?.cellType) {
                CellType.STRING -> cellLessonHall.stringCellValue
                CellType.NUMERIC -> cellLessonHall.numericCellValue.toInt().toString()
                else -> ""
            }
            if (valueLessonHall.isNotBlank()) {
                lessonHall = valueLessonHall
            }

            val cellLessonOrTeacher: Cell? = row.getCell(cellNumber)
            val lessonOrTeacher = when (cellLessonOrTeacher?.cellType) {
                CellType.STRING -> cellLessonOrTeacher.stringCellValue
                CellType.NUMERIC -> cellLessonOrTeacher.numericCellValue.toInt().toString()
                else -> ""
            }
            if (lessonOrTeacher.isNotBlank()) {
                if (lesson != null && rowIndex - lastRowIndex >= 2) {
                    result.add(
                        Lesson(
                            collegeGroup = group,
                            lesson = lesson!!,
                            teacher = teacher ?: "",
                            lessonHall = lessonHall ?: "",
                            lessonType = getLessonType(lastRowIndex + 1),
                            dayOfWeek = getDayOfWeek(lastRowIndex + 1, firstTime),
                            lessonNumber = getLessonNumber(lastRowIndex + 1)
                        )
                    )
                    clearData()
                }
                if (lesson == null) {
                    lesson = lessonOrTeacher
                } else if (teacher == null) {
                    teacher = lessonOrTeacher.trim()
                }
                lastRowIndex = rowIndex
            }

            val line = rowIndex % 4
            if (line == 1 && lesson != null && teacher != null) {
                result.add(
                    Lesson(
                        collegeGroup = group,
                        lesson = lesson!!,
                        teacher = teacher ?: "",
                        lessonHall = lessonHall ?: "",
                        lessonType = LessonType.NotEven,
                        dayOfWeek = getDayOfWeek(rowIndex, firstTime),
                        lessonNumber = getLessonNumber(rowIndex),
                    )
                )
                clearData()
            }
            if (line == 2 && lesson != null && teacher != null) {
                result.add(
                    Lesson(
                        collegeGroup = group,
                        lesson = lesson!!,
                        teacher = teacher ?: "",
                        lessonHall = lessonHall ?: "",
                        lessonType = LessonType.None,
                        dayOfWeek = getDayOfWeek(rowIndex, firstTime),
                        lessonNumber = getLessonNumber(rowIndex),
                    )
                )
                clearData()
            }
            if (line == 3 && lesson != null && teacher != null) {
                result.add(
                    Lesson(
                        collegeGroup = group,
                        lesson = lesson!!,
                        teacher = teacher ?: "",
                        lessonHall = lessonHall ?: "",
                        lessonType = LessonType.Even,
                        dayOfWeek = getDayOfWeek(rowIndex, firstTime),
                        lessonNumber = getLessonNumber(rowIndex),
                    )
                )
                clearData()
            }
        }
        return result.toList()
    }

    private fun getLessonType(rowIndex: Int): LessonType {
        return when (rowIndex % 4) {
            0 -> LessonType.NotEven
            1 -> LessonType.NotEven
            2 -> LessonType.None
            3 -> LessonType.Even
            else -> LessonType.None
        }
    }

    private fun getDayOfWeek(row: Int, firstTime: Boolean): DayOfWeek {
        if (row <= 27) {
            if (firstTime) {
                return DayOfWeek.MONDAY
            }
            return DayOfWeek.THURSDAY

        }
        if (row <= 55) {
            if (firstTime) {
                return DayOfWeek.TUESDAY
            }
            return DayOfWeek.FRIDAY
        }
        if (row <= 83) {
            if (firstTime) {
                return DayOfWeek.WEDNESDAY
            }
            return DayOfWeek.SATURDAY
        }
        return DayOfWeek.SUNDAY
    }

    private fun getLessonNumber(row: Int): Int {
        if (row in 0..3 || row in 28..31 || row in 56..59) {
            return 0
        }
        if (row in 4..7 || row in 32..35 || row in 60..63) {
            return 1
        }
        if (row in 8..11 || row in 36..39 || row in 64..67) {
            return 2
        }
        if (row in 12..15 || row in 40..43 || row in 68..71) {
            return 3
        }
        if (row in 16..19 || row in 44..47 || row in 72..75) {
            return 4
        }
        if (row in 20..23 || row in 48..51 || row in 76..79) {
            return 5
        }
        if (row in 24..27 || row in 52..55 || row in 80..83) {
            return 6
        }
        return 10
    }
}
