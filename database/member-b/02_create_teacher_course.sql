-- MySQL 8.4; caller selects an empty database. No cross-module tables are created here.
CREATE TABLE teacher (
    id BIGINT NOT NULL AUTO_INCREMENT,
    teacher_no VARCHAR(20) NOT NULL,
    name VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_teacher_no UNIQUE (teacher_no),
    CONSTRAINT ck_teacher_no CHECK (REGEXP_LIKE(teacher_no, '[^[:space:]]')),
    CONSTRAINT ck_teacher_name CHECK (REGEXP_LIKE(name, '[^[:space:]]'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE course (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    credits DECIMAL(4,1) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_course_code UNIQUE (course_code),
    CONSTRAINT ck_course_code CHECK (REGEXP_LIKE(course_code, '[^[:space:]]')),
    CONSTRAINT ck_course_name CHECK (REGEXP_LIKE(name, '[^[:space:]]')),
    CONSTRAINT ck_course_credits CHECK (credits > 0),
    INDEX ix_course_credits_code (credits DESC, course_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
