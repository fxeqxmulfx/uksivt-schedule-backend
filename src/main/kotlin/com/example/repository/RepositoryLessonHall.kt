package com.example.repository

import com.example.module.DataBase
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import java.time.LocalDate

object RepositoryLessonHall {
    suspend fun getAllLessonHall(): RowSet<Row> {
        return DataBase.fetchAll(
            """
            select lesson_hall
            from lesson
            group by lesson_hall
            order by lesson_hall;
            """.trimIndent(), Tuple.tuple()
        )
    }

    suspend fun getAllLessonByLessonHall(lessonHall: String, fromDate: LocalDate, byDate: LocalDate): RowSet<Row> {
        val sql = """
        select distinct coalesce(lr.college_group,
                                 lrg.college_group,
                                 l.college_group) as college_group,
                        coalesce(lr.replacement_date_day_of_week,
                                 lrg.replacement_date_day_of_week,
                                 l.day_of_week)   as day_of_week,
                        coalesce(lr.lesson_number,
                                 lrg.lesson_number,
                                 l.lesson_number) as lesson_number,
                        coalesce(lr.substitute_lesson,
                                 lrg.substitute_lesson,
                                 l.lesson)        as lesson,
                        coalesce(lr.substitute_teacher,
                                 lrg.substitute_teacher,
                                 l.teacher)       as teacher,
                        coalesce(lr.lesson_hall,
                                 lrg.lesson_hall,
                                 l.lesson_hall)   as lesson_hall,
                        case
                            when lr.college_group is not null
                                or lrg.college_group is not null
                                then true
                            else false
                            end                   as replacement
        from lesson l
                 full join (select *
                            from lesson_replacement lrt
                            where lrt.replacement_date >= $1
                              and lrt.replacement_date <= $2
                              and not lrt.generated) lr
                           on lr.college_group = l.college_group
                               and lr.replacement_date_day_of_week = l.day_of_week
                               and lr.lesson_number = l.lesson_number
                 full join (select *
                            from lesson_replacement lrt
                            where lrt.replacement_date >= $3
                              and lrt.replacement_date <= $4
                              and lrt.generated) lrg
                           on lrg.college_group = l.college_group
                               and lrg.replacement_date_day_of_week = l.day_of_week
                               and lrg.lesson_number = l.lesson_number
        where coalesce(lr.lesson_hall, lrg.lesson_hall, l.lesson_hall) ilike $5;        
        """.trimIndent()
        val tuple = Tuple.of(
            fromDate, byDate,
            fromDate, byDate,
            "${
                lessonHall
                    .replace(' ', '%')
                    .replace('.', '%')
                    .replace(',', '%')
            }%"
        )
        return DataBase.fetchAll(sql, tuple)
    }
}