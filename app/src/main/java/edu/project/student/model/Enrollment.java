package edu.project.student.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Enrollment(long id, long studentId, long teachingClassId, String status,
                         LocalDateTime selectedAt, LocalDateTime withdrawnAt, BigDecimal score) { }
