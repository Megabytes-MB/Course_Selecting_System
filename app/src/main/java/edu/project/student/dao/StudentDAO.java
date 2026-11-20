package edu.project.student.dao;

import edu.project.student.model.Student;
import java.sql.*;
import java.util.*;

public final class StudentDAO {
    private static String text(String value, int max, String label) {
        if (value == null || value.isBlank() || value.strip().codePointCount(0, value.strip().length()) > max)
            throw new IllegalArgumentException(label + "不能为空且长度不能超过" + max);
        if (value.indexOf('\uFFFD') >= 0) throw new IllegalArgumentException("输入包含无法解码的字符，请确认终端使用UTF-8后重新输入");
        return value.strip();
    }

    public long insert(Connection c, String number, String name, String major) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "INSERT INTO student(student_no,name,major) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, text(number,20,"学号")); p.setString(2,text(name,50,"姓名"));
            p.setString(3,text(major,100,"专业"));
            if (p.executeUpdate() != 1) throw new SQLException("添加学生影响行数异常");
            try (ResultSet r = p.getGeneratedKeys()) {
                if (!r.next()) throw new SQLException("未返回学生编号");
                return r.getLong(1);
            }
        }
    }

    public Optional<Student> findById(Connection c, long id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT id,student_no,name,major FROM student WHERE id=?")) {
            p.setLong(1,id);
            try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(map(r)) : Optional.empty(); }
        }
    }

    public List<Student> findByMajor(Connection c, String major) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "SELECT id,student_no,name,major FROM student WHERE major=? ORDER BY student_no")) {
            p.setString(1,text(major,100,"专业"));
            try (ResultSet r = p.executeQuery()) {
                List<Student> rows = new ArrayList<>();
                while (r.next()) rows.add(map(r));
                return rows;
            }
        }
    }

    public boolean update(Connection c, long id, String number, String name, String major) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("UPDATE student SET student_no=?,name=?,major=? WHERE id=?")) {
            p.setString(1,text(number,20,"学号")); p.setString(2,text(name,50,"姓名"));
            p.setString(3,text(major,100,"专业")); p.setLong(4,id);
            return p.executeUpdate() == 1;
        }
    }

    public boolean delete(Connection c, long id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("DELETE FROM student WHERE id=?")) {
            p.setLong(1,id); return p.executeUpdate() == 1;
        }
    }

    private Student map(ResultSet r) throws SQLException {
        return new Student(r.getLong("id"),r.getString("student_no"),r.getString("name"),r.getString("major"));
    }
}
