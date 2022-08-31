create table lesson
(
    id            int primary key generated always as identity,
    college_group text not null,
    day_of_week   int  not null,
    lesson_type   text not null,
    lesson_number int  not null,
    lesson        text not null,
    teacher       text not null,
    lesson_hall   text not null
);

create table lesson_replacement
(
    id                           int primary key generated always as identity,
    college_group                text not null,
    for_the_whole_day            bool not null,
    lesson_number                int  not null,
    replaceable_lesson           text not null,
    substitute_lesson            text not null,
    substitute_teacher           text not null,
    lesson_hall                  text not null,
    replacement_date             date not null,
    replacement_date_day_of_week int  not null,
    generated                    bool not null
);