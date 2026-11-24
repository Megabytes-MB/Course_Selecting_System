package edu.project.student.model;

import java.math.BigDecimal;

public record CourseResult(String studentNo, String studentName, String classCode,
                           String courseName, BigDecimal credits, String status, BigDecimal score) { }
