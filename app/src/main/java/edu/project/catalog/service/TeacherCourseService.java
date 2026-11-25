package edu.project.catalog.service;

import edu.project.catalog.dao.CourseDAO;
import edu.project.catalog.dao.TeacherDAO;
import edu.project.catalog.model.Course;
import edu.project.catalog.model.Teacher;
import edu.project.catalog.model.TeacherClass;
import edu.project.common.DBUtil;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Each single-step operation owns one independent auto-commit connection. */
public final class TeacherCourseService {
    private final TeacherDAO teachers = new TeacherDAO();
    private final CourseDAO courses = new CourseDAO();

    public long addTeacher(String number, String name) throws SQLException {
        try (Connection c = DBUtil.getConnection()) { return teachers.insert(c, number, name); }
    }
    public Optional<Teacher> teacher(long id) throws SQLException {
        try (Connection c = DBUtil.getConnection()) { return teachers.findById(c, id); }
    }
    public List<Teacher> teachers() throws SQLException {
        try (Connection c = DBUtil.getConnection()) { return teachers.findAll(c); }
    }
    public boolean updateTeacher(long id, String number, String name) throws SQLException {
        try (Connection c = DBUtil.getConnection()) { return teachers.update(c, id, number, name); }
    }
    public boolean deleteTeacher(long id) throws SQLException {
        try (Connection c = DBUtil.getConnection()) { return teachers.delete(c, id); }
    }
    public long addCourse(String code, String name, BigDecimal credits) throws SQLException {
        try (Connection c = DBUtil.getConnection()) { return courses.insert(c, code, name, credits); }
    }
    public Optional<Course> course(long id) throws SQLException {
        try (Connection c = DBUtil.getConnection()) { return courses.findById(c, id); }
    }
    public List<Course> courses() throws SQLException {
        try (Connection c = DBUtil.getConnection()) { return courses.findAll(c); }
    }
    public boolean updateCourse(long id, String code, String name, BigDecimal credits) throws SQLException {
        try (Connection c = DBUtil.getConnection()) { return courses.update(c, id, code, name, credits); }
    }
    public boolean deleteCourse(long id) throws SQLException {
        try (Connection c = DBUtil.getConnection()) { return courses.delete(c, id); }
    }
    public List<Course> byMinimumCredits(BigDecimal minimum) throws SQLException {
        try (Connection c = DBUtil.getConnection()) { return courses.findByMinimumCredits(c, minimum); }
    }
    public List<TeacherClass> teacherClasses() throws SQLException {
        try (Connection c = DBUtil.getConnection()) { return teachers.teachingClasses(c); }
    }
    public List<Course> bySemester(long semesterId) throws SQLException {
        try (Connection c = DBUtil.getConnection()) { return courses.findBySemester(c, semesterId); }
    }
}
