package com.example.plugins

import com.example.model.LessonHall
import com.example.model.Student
import com.example.model.Teacher
import com.example.module.ReplacementParser
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime


fun Application.configureRouting() {
    routing {
        get("/api/v1/college_group") {
            call.respond(Student.getAllGroup())
        }
        get("/api/v1/college_group/{college_group}/from_date/{from_date}") {
            val collegeGroup = call.parameters["college_group"] ?: return@get call.respondText(
                "Missing college_group",
                status = HttpStatusCode.BadRequest
            )
            val fromDateString = call.parameters["from_date"] ?: return@get call.respondText(
                "Missing from_date",
                status = HttpStatusCode.BadRequest
            )
            val fromDate = LocalDate.parse(fromDateString)
            call.respond(Student.getAllLessonByGroup(collegeGroup, fromDate))
        }
    }
    routing {
        get("/api/v1/teacher") {
            call.respond(Teacher.getAllTeacher())
        }
        get("/api/v1/teacher/{teacher}/from_date/{from_date}") {
            val teacher = call.parameters["teacher"] ?: return@get call.respondText(
                "Missing teacher",
                status = HttpStatusCode.BadRequest
            )
            val fromDateString = call.parameters["from_date"] ?: return@get call.respondText(
                "Missing from_date",
                status = HttpStatusCode.BadRequest
            )
            val fromDate = LocalDate.parse(fromDateString)
            call.respond(Teacher.getAllLessonByTeacher(teacher, fromDate))
        }
    }

    routing {
        get("/api/v1/lesson_hall") {
            call.respond(LessonHall.getAllLessonHall())
        }
        get("/api/v1/lesson_hall/{lesson_hall}/from_date/{from_date}") {
            val lessonHall = call.parameters["lesson_hall"] ?: return@get call.respondText(
                "Missing lesson_hall",
                status = HttpStatusCode.BadRequest
            )
            val fromDateString = call.parameters["from_date"] ?: return@get call.respondText(
                "Missing from_date",
                status = HttpStatusCode.BadRequest
            )
            val fromDate = LocalDate.parse(fromDateString)
            call.respond(LessonHall.getAllLessonByLessonHall(lessonHall, fromDate))
        }
    }

    routing {
        get("/api/v1/replacement_last_update") {
            call.respond(
                (ReplacementParser.lastUpdate ?: ZonedDateTime.now().minusDays(1))
                    .withZoneSameInstant(ZoneId.of("UTC"))
                    .toString()
            )
        }
    }
}
