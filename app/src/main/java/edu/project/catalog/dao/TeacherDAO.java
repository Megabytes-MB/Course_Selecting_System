package edu.project.catalog.dao;

import edu.project.catalog.model.Teacher;
import edu.project.catalog.model.TeacherClass;
import java.sql.*;
import java.util.*;

public final class TeacherDAO {
    public long insert(Connection c, String number, String name) throws SQLException {
        String no = CatalogValues.text(number, 20, "教师编号");
        String label = CatalogValues.text(name, 50, "教师姓名");
        try (PreparedStatement p = c.prepareStatement("INSERT INTO teacher(teacher_no,name) VALUES(?,?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, no); p.setString(2, label);
            if (!CatalogValues.affected(p.executeUpdate())) throw new SQLException("教师添加未生效");
            try (ResultSet r = p.getGeneratedKeys()) {
                if (!r.next()) throw new SQLException("未返回教师 ID");
                return r.getLong(1);
            }
        }
    }

    public Optional<Teacher> findById(Connection c, long id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT id,teacher_no,name FROM teacher WHERE id=?")) {
            p.setLong(1, CatalogValues.id(id));
            try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(map(r)) : Optional.empty(); }
        }
    }

    public List<Teacher> findAll(Connection c) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT id,teacher_no,name FROM teacher ORDER BY teacher_no,id"); ResultSet r = p.executeQuery()) {
            List<Teacher> rows = new ArrayList<>();
            while (r.next()) rows.add(map(r));
            return rows;
        }
    }

    public boolean update(Connection c, long id, String number, String name) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("UPDATE teacher SET teacher_no=?,name=? WHERE id=?")) {
            p.setString(1, CatalogValues.text(number, 20, "教师编号"));
            p.setString(2, CatalogValues.text(name, 50, "教师姓名")); p.setLong(3, CatalogValues.id(id));
            int affected = p.executeUpdate();
            return CatalogValues.affected(affected) || findById(c, id).isPresent();
        }
    }

    public boolean delete(Connection c, long id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("DELETE FROM teacher WHERE id=?")) {
            p.setLong(1, CatalogValues.id(id)); return CatalogValues.affected(p.executeUpdate());
        }
    }

    public List<TeacherClass> teachingClasses(Connection c) throws SQLException {
        String sql = "SELECT t.id,t.teacher_no,t.name,tc.id AS class_id,tc.class_code,c.name AS course_name,s.name AS semester_name "
                + "FROM teacher t LEFT JOIN teaching_class tc ON tc.teacher_id=t.id "
                + "LEFT JOIN course c ON c.id=tc.course_id LEFT JOIN semester s ON s.id=tc.semester_id "
                + "ORDER BY t.teacher_no,tc.class_code";
        try (PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
            List<TeacherClass> rows = new ArrayList<>();
            while (r.next()) rows.add(new TeacherClass(r.getLong("id"), r.getString("teacher_no"), r.getString("name"),
                    r.getObject("class_id", Long.class), r.getString("class_code"), r.getString("course_name"), r.getString("semester_name")));
            return rows;
        }
    }

    private Teacher map(ResultSet r) throws SQLException { return new Teacher(r.getLong("id"), r.getString("teacher_no"), r.getString("name")); }
}
