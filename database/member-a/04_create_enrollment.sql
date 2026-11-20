CREATE TABLE enrollment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    teaching_class_id BIGINT NOT NULL,
    status VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ENROLLED',
    selected_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    withdrawn_at DATETIME(6) NULL,
    score DECIMAL(5,2) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_enrollment_student_class UNIQUE (student_id, teaching_class_id),
    CONSTRAINT fk_enrollment_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_enrollment_class FOREIGN KEY (teaching_class_id) REFERENCES teaching_class(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_enrollment_status CHECK ((status = 'ENROLLED' AND CHAR_LENGTH(status) = 8) OR (status = 'WITHDRAWN' AND CHAR_LENGTH(status) = 9)),
    CONSTRAINT ck_enrollment_score CHECK (score IS NULL OR score BETWEEN 0 AND 100),
    CONSTRAINT ck_enrollment_state CHECK (
        (status = 'ENROLLED' AND withdrawn_at IS NULL) OR
        (status = 'WITHDRAWN' AND score IS NULL AND withdrawn_at IS NOT NULL AND withdrawn_at >= selected_at)
    ),
    INDEX ix_enrollment_class_status (teaching_class_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
