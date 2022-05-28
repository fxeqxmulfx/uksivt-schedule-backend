package com.example.plugins

import com.example.model.Student
import com.example.model.Teacher
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import java.time.LocalDate


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
}
