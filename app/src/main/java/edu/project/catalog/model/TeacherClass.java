package edu.project.catalog.model;

/** Nullable class/course/semester columns represent a teacher with no teaching class. */
public record TeacherClass(long teacherId, String teacherNo, String teacherName,
        Long teachingClassId, String classCode, String courseName, String semesterName) { }
