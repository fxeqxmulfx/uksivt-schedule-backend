package com.example.repository

import com.example.module.DataBase
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.*

object RepositoryStudent {
    suspend fun getAllGroup(): RowSet<Row> {
        return DataBase.fetchAll(
            """
select college_group
from lesson
group by college_group
order by college_group;
        """.trimIndent(), Tuple.tuple()
        )
    }

    suspend fun getAllLessonByGroup(collegeGroup: String, fromDate: LocalDate, byDate: LocalDate): RowSet<Row> {
        val woy = WeekFields.of(Locale("ru")).weekOfWeekBasedYear();
        val lessonType = if (fromDate.get(woy) % 2 == 0) {
            "Even"
        } else {
            "NotEven"
        }
        val sql =
            """
select distinct coalesce(for_the_whole_day_true.college_group,
                         for_the_whole_day_false.college_group)                                      as college_group,
                coalesce(for_the_whole_day_true.replacement_date_day_of_week,
                         for_the_whole_day_false.day_of_week)                                        as day_of_week,
                coalesce(for_the_whole_day_true.lesson_number,
                         for_the_whole_day_false.lesson_number)                                      as lesson_number,
                coalesce(for_the_whole_day_true.substitute_lesson, for_the_whole_day_false.lesson)   as lesson,
                coalesce(for_the_whole_day_true.substitute_teacher, for_the_whole_day_false.teacher) as teacher,
                coalesce(for_the_whole_day_true.lesson_hall,
                         for_the_whole_day_false.lesson_hall)                                        as lesson_hall,
                coalesce(for_the_whole_day_true.replacement, for_the_whole_day_false.replacement)    as replacement
from (select coalesce(lr.college_group, l.college_group)              as college_group,
             coalesce(lr.replacement_date_day_of_week, l.day_of_week) as day_of_week,
             coalesce(lr.lesson_number, l.lesson_number)              as lesson_number,
             coalesce(lr.substitute_lesson, l.lesson)                 as lesson,
             coalesce(lr.substitute_teacher, l.teacher)               as teacher,
             coalesce(lr.lesson_hall, l.lesson_hall)                  as lesson_hall,
             case
                 when lr.college_group is not null then true
                 else false
                 end                                                  as replacement
      from lesson l
               full join lesson_replacement lr
                         on l.college_group = lr.college_group
                             and l.day_of_week = lr.replacement_date_day_of_week
                             and l.lesson_number = lr.lesson_number
                             and not lr.for_the_whole_day
                             and not lr.generated
                             and lr.replacement_date >= $1
                             and lr.replacement_date <= $2
      where l.lesson_type = 'None'
         or l.lesson_type = '$lessonType') for_the_whole_day_false
         full join (select lr.college_group,
                           lr.lesson_number,
                           lr.substitute_lesson,
                           lr.substitute_teacher,
                           lr.lesson_hall,
                           lr.replacement_date_day_of_week,
                           true as replacement
                    from lesson_replacement lr
                    where lr.for_the_whole_day
                      and not lr.generated
                      and lr.replacement_date >= $3
                      and lr.replacement_date <= $4) for_the_whole_day_true
                   on for_the_whole_day_false.college_group = for_the_whole_day_true.college_group
                       and for_the_whole_day_false.day_of_week = for_the_whole_day_true.replacement_date_day_of_week
where for_the_whole_day_false.college_group = $5
   or for_the_whole_day_true.college_group = $6;
                """.trimIndent()
        val tuple = Tuple.of(
            fromDate, byDate,
            fromDate, byDate,
            collegeGroup, collegeGroup,
        )
        return DataBase.fetchAll(sql, tuple)
    }
}