CREATE TABLE student (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_no VARCHAR(20) NOT NULL,
    name VARCHAR(50) NOT NULL,
    major VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_student_no UNIQUE (student_no),
    CONSTRAINT ck_student_no CHECK (CHAR_LENGTH(TRIM(student_no)) > 0),
    CONSTRAINT ck_student_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_student_major CHECK (CHAR_LENGTH(TRIM(major)) > 0),
    INDEX ix_student_major_no (major, student_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
