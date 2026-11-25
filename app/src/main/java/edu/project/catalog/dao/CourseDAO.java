package edu.project.catalog.dao;

import edu.project.catalog.model.Course;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

public final class CourseDAO {
    public long insert(Connection c, String code, String name, BigDecimal credits) throws SQLException {
        String number = CatalogValues.text(code, 20, "课程代码");
        String label = CatalogValues.text(name, 100, "课程名称");
        BigDecimal value = CatalogValues.credits(credits);
        try (PreparedStatement p = c.prepareStatement("INSERT INTO course(course_code,name,credits) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, number); p.setString(2, label); p.setBigDecimal(3, value);
            if (!CatalogValues.affected(p.executeUpdate())) throw new SQLException("课程添加未生效");
            try (ResultSet r = p.getGeneratedKeys()) {
                if (!r.next()) throw new SQLException("未返回课程 ID");
                return r.getLong(1);
            }
        }
    }

    public Optional<Course> findById(Connection c, long id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT id,course_code,name,credits FROM course WHERE id=?")) {
            p.setLong(1, CatalogValues.id(id));
            try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(map(r)) : Optional.empty(); }
        }
    }

    public List<Course> findAll(Connection c) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT id,course_code,name,credits FROM course ORDER BY course_code,id"); ResultSet r = p.executeQuery()) {
            return rows(r);
        }
    }

    public boolean update(Connection c, long id, String code, String name, BigDecimal credits) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("UPDATE course SET course_code=?,name=?,credits=? WHERE id=?")) {
            p.setString(1, CatalogValues.text(code, 20, "课程代码"));
            p.setString(2, CatalogValues.text(name, 100, "课程名称"));
            p.setBigDecimal(3, CatalogValues.credits(credits)); p.setLong(4, CatalogValues.id(id));
            int affected = p.executeUpdate();
            // useAffectedRows=true returns zero for an existing row with unchanged values.
            return CatalogValues.affected(affected) || findById(c, id).isPresent();
        }
    }

    public boolean delete(Connection c, long id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("DELETE FROM course WHERE id=?")) {
            p.setLong(1, CatalogValues.id(id)); return CatalogValues.affected(p.executeUpdate());
        }
    }

    public List<Course> findByMinimumCredits(Connection c, BigDecimal minimum) throws SQLException {
        if (minimum == null || minimum.signum() < 0) throw new IllegalArgumentException("最低学分不能为负数");
        try (PreparedStatement p = c.prepareStatement("SELECT id,course_code,name,credits FROM course WHERE credits>=? ORDER BY credits DESC,course_code")) {
            p.setBigDecimal(1, minimum);
            try (ResultSet r = p.executeQuery()) { return rows(r); }
        }
    }

    public List<Course> findBySemester(Connection c, long semesterId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT id,course_code,name,credits FROM course WHERE id IN "
                + "(SELECT course_id FROM teaching_class WHERE semester_id=?) ORDER BY course_code")) {
            p.setLong(1, CatalogValues.id(semesterId));
            try (ResultSet r = p.executeQuery()) { return rows(r); }
        }
    }

    private List<Course> rows(ResultSet r) throws SQLException {
        List<Course> rows = new ArrayList<>();
        while (r.next()) rows.add(map(r));
        return rows;
    }

    private Course map(ResultSet r) throws SQLException {
        return new Course(r.getLong("id"), r.getString("course_code"), r.getString("name"), r.getBigDecimal("credits"));
    }
}
