package com.example.module

import com.example.repository.RepositoryTeacher.getLesson
import com.example.repository.RepositoryTeacher.getLessons
import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.Tuple
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFTableRow
import org.jsoup.Jsoup
import java.io.InputStream
import java.net.URL
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.minutes

object ReplacementParser {
    private const val days = 7
    private val googleApiKey: String = System.getenv("GOOGLE_API")

    suspend fun run() {
        coroutineScope {
            launch {
                while (true) {
                    try {
                        scheduledParseSiteAndPutDb()
                    } catch (e: Exception) {
                        println(e.toString())
                    }
                    delay(3600000L)
                }
            }
        }
    }

    data class LessonReplacement(
        val collegeGroup: String,
        val forTheWholeDay: Boolean,
        val lessonNumber: Int,
        val replaceableLesson: String,
        val substituteLesson: String,
        val substituteTeacher: String,
        val lessonHall: String,
        val replacementDate: LocalDate,
        val generated: Boolean,
    ) {
        fun toTuple(): Tuple {
            return Tuple.of(
                this.collegeGroup, this.forTheWholeDay,
                this.lessonNumber, this.replaceableLesson,
                this.substituteLesson, this.substituteTeacher,
                this.lessonHall, this.replacementDate,
                this.replacementDate.dayOfWeek.value,
                this.generated
            )
        }
    }

    private var cachedReference: List<Pair<URL, LocalDate>> = listOf()

    var lastUpdate: ZonedDateTime? = null

    private suspend fun parseSiteAndPutDb(forceUpdate: Boolean) {
        var tempClient: SqlClient? = null
        try {
            val client = DataBase.getClient()
            tempClient = client

            val links = getLinks()

            if (links.lastOrNull()?.second == cachedReference.lastOrNull()?.second && !forceUpdate) {
                println("no changes")
                return
            }

            cachedReference = links

            val lessonReplacements: List<LessonReplacement> = links.downloadFiles().flatMap { inputStreamAndDate ->
                inputStreamAndDate.inputStream.use {
                    inputStreamAndDate.parseDocx()
                }
            }

            if (lessonReplacements.isEmpty()) {
                return
            }

            client.preparedQuery("delete from lesson_replacement where replacement_date = $1")
                .executeBatch(lessonReplacements.map { Tuple.of(it.replacementDate) }.toSet().toList()).await()

            client.preparedQuery(
                """
                    insert into lesson_replacement(college_group, for_the_whole_day, lesson_number, replaceable_lesson, 
                    substitute_lesson, substitute_teacher, lesson_hall, replacement_date, replacement_date_day_of_week,
                    generated) 
                    values($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
                """.trimIndent()
            ).executeBatch(lessonReplacements.map { it.toTuple() }).await()

            if (lessonReplacements.last().replacementDate == LocalDate.now()
                || lessonReplacements.last().replacementDate.isAfter(LocalDate.now())
            ) {
                lastUpdate = ZonedDateTime.now()
            }

            println("substitutions ${lessonReplacements.map { it.replacementDate }.toSortedSet()}")
        } finally {
            tempClient?.close()?.await()
        }
    }

    private suspend fun scheduledParseSiteAndPutDb() {
        var count = 0
        while (true) {
            count += 1
            if (count == 60) {
                parseSiteAndPutDb(true)
                count = 0
            } else {
                parseSiteAndPutDb(false)
            }
            delay((1).minutes)
        }
    }

    private fun getLinks(): List<Pair<URL, LocalDate>> {
        val html = Jsoup.connect("https://www.uksivt.ru/zameny").get()
        val tables = html.body().getElementsByTag("table").toList().subList(0, 1)
        val referencesGoogleDocsAndDate = tables.flatMap { table ->
            (table?.getElementsByAttributeValueContaining("href", "docs.google.com")
                ?.map { it.attr("href") }
                ?.takeLast(days) ?: listOf())
                .map { url ->
                    val document = Jsoup.connect(url).get()
                    val date = document.head().getElementsByTag("title").first()?.text()?.split(".")
                    val day = date?.getOrNull(0)?.trim()?.toIntOrNull() ?: 1
                    val month = date?.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
                    url to LocalDate.of(LocalDate.now().year, month, day)
                }
        }
        val referencesUksivt = tables.flatMap { table ->
            (table?.getElementsByAttributeValueContaining("href", "storage")
                ?.map { it.attr("href") }
                ?.takeLast(days) ?: listOf()).mapNotNull { brokenUrl ->
                val url = "https://www.uksivt.ru/storage/files/all/ZAMENY/${brokenUrl.split("/").last()}"
                val stringDate = url.split("/").last()
                try {
                    val day = stringDate.take(2).toInt()
                    val month = stringDate.drop(3).take(2).toInt()
                    val year = "20${stringDate.drop(6).take(2)}".toInt()
                    url.toURL() to LocalDate.of(year, month, day)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }
        val referencesGoogleDiscAndDate: List<Pair<URL, LocalDate>> = referencesGoogleDocsAndDate.map {
            it.first.convertReferenceGoogleDocsToReferenceGoogleDisc().toURL() to it.second
        }
        return referencesGoogleDiscAndDate + referencesUksivt
    }

    private fun List<Pair<URL, LocalDate>>.downloadFiles(): List<InputStreamAndDate> {
        return map { InputStreamAndDate(it.first.openStream(), it.second) }
    }

    private fun String.convertReferenceGoogleDocsToReferenceGoogleDisc(): String? {
        val id = this.split("/d/").getOrNull(1)?.split("/")?.firstOrNull()
        return if (id != null) {
            "https://www.googleapis.com/drive/v3/files/$id?key=$googleApiKey&alt=media"
        } else {
            null
        }
    }

    private fun String?.toURL(): URL {
        if (this == null) return URL("")
        return URL(this)
    }

    private data class InputStreamAndDate(val inputStream: InputStream, val date: LocalDate) {
        suspend fun parseDocx(): List<LessonReplacement> {
            val docx = XWPFDocument(this.inputStream)
            return docx.tables.flatMap { table ->
                val rows = table.rows
                var previewGroup = ""
                var forTheWholeDayIn = false
                rows.flatMap { row ->
                    val list = createPairReplacementFromRow(row, previewGroup, forTheWholeDayIn, this.date)
                    previewGroup = list.firstOrNull()?.collegeGroup ?: ""
                    forTheWholeDayIn = list.firstOrNull()?.forTheWholeDay ?: false
                    list
                }.filter { it.lessonNumber != -1 }
            }
        }
    }


    private suspend fun createPairReplacementFromRow(
        row: XWPFTableRow, previewGroup: String, forTheWholeDayIn: Boolean, replacementDate: LocalDate
    ): List<LessonReplacement> {
        if (row.tableCells.size == 0) {
            return listOf()
        }
        val groupZero = row.getCell(0).text
        if (row.tableCells.size == 3 && row.getCell(1).text.trim() == "Экзамен") {
            val lesson = LessonReplacement(
                collegeGroup = groupZero.replace("_", "-").trim().uppercase(),
                forTheWholeDay = true,
                lessonNumber = 0,
                replaceableLesson = "",
                substituteLesson = "Экзамен",
                substituteTeacher = "",
                lessonHall = "",
                replacementDate = replacementDate,
                false
            )
            return getLessons(lesson.collegeGroup, lesson.replacementDate.dayOfWeek).map {
                LessonReplacement(
                    collegeGroup = lesson.collegeGroup,
                    forTheWholeDay = false,
                    lessonNumber = it.getInteger("lesson_number"),
                    replaceableLesson = "",
                    substituteLesson = "Нет",
                    substituteTeacher = it.getString("teacher"),
                    lessonHall = "",
                    replacementDate = lesson.replacementDate,
                    true
                )
            } + lesson
        }

        if (row.tableCells.size != 7) {
            return listOf()
        }

        val groupThree = row.getCell(3).text
        val (group, forTheWholeDay) = when {
            groupZero.isBlank() && groupThree.isNotBlank() -> groupThree to true
            groupZero.isNotBlank() -> groupZero to false
            else -> previewGroup to forTheWholeDayIn
        }
        if (group?.matches("^\\d+\\D{1,5}\\d+\$".toRegex()) == false) {
            return listOf()
        }
        val replaceableLesson = row.getCell(2).text
        val substituteLesson = row.getCell(4).text
        val substituteTeacher = row.getCell(5).text
        val lessonHall = row.getCell(6).text
        val lessonReplacements = row.getCell(1).text.split(",").map { it.trim() }.map { pairNumber ->
            LessonReplacement(
                collegeGroup = group.replace("_", "-").trim().uppercase(),
                forTheWholeDay = forTheWholeDay,
                lessonNumber = pairNumber.toIntOrNull() ?: -1,
                replaceableLesson = replaceableLesson.trim(),
                substituteLesson = substituteLesson.trim(),
                substituteTeacher = substituteTeacher.trim(),
                lessonHall = lessonHall.trim(),
                replacementDate = replacementDate,
                false
            )
        }
        if (lessonReplacements.isEmpty()) {
            return listOf()
        }
        if (lessonReplacements.first().forTheWholeDay) {
            val lesson = lessonReplacements.first()
            return lessonReplacements + getLessons(lesson.collegeGroup, lesson.replacementDate.dayOfWeek).map {
                LessonReplacement(
                    collegeGroup = lesson.collegeGroup,
                    forTheWholeDay = false,
                    lessonNumber = it.getInteger("lesson_number"),
                    replaceableLesson = "",
                    substituteLesson = "Нет",
                    substituteTeacher = it.getString("teacher"),
                    lessonHall = "",
                    replacementDate = lesson.replacementDate,
                    true
                )
            }
        }
        return lessonReplacements + lessonReplacements.map {
            val lesson = getLesson(it.collegeGroup, it.replacementDate.dayOfWeek, it.lessonNumber) ?: return@map null
            LessonReplacement(
                collegeGroup = it.collegeGroup,
                forTheWholeDay = it.forTheWholeDay,
                lessonNumber = it.lessonNumber,
                replaceableLesson = it.replaceableLesson,
                substituteLesson = "Нет",
                substituteTeacher = lesson.getString("teacher"),
                lessonHall = "",
                replacementDate = it.replacementDate,
                true
            )
            it
        }.filterNotNull()
    }
}