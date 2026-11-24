package edu.project.student.dao;

import edu.project.student.model.*;
import java.sql.*;
import java.util.*;
import java.math.BigDecimal;

public final class EnrollmentDAO {
    public int lockClass(Connection c, long classId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT capacity FROM teaching_class WHERE id=? FOR UPDATE")) {
            p.setLong(1,classId);
            try (ResultSet r = p.executeQuery()) { return r.next() ? r.getInt(1) : -1; }
        }
    }
    public long activeCount(Connection c, long classId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM enrollment WHERE teaching_class_id=? AND status='ENROLLED'")) {
            p.setLong(1,classId);
            try (ResultSet r = p.executeQuery()) { r.next(); return r.getLong(1); }
        }
    }
    public Optional<Enrollment> find(Connection c, long studentId, long classId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT * FROM enrollment WHERE student_id=? AND teaching_class_id=? FOR UPDATE")) {
            p.setLong(1,studentId); p.setLong(2,classId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return Optional.empty();
                Timestamp withdrawn = r.getTimestamp("withdrawn_at");
                return Optional.of(new Enrollment(r.getLong("id"),studentId,classId,r.getString("status"),
                    r.getTimestamp("selected_at").toLocalDateTime(),withdrawn == null ? null : withdrawn.toLocalDateTime(),r.getBigDecimal("score")));
            }
        }
    }
    public void insert(Connection c, long studentId, long classId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO enrollment(student_id,teaching_class_id,status,selected_at) VALUES(?,?,'ENROLLED',CURRENT_TIMESTAMP(6))")) {
            p.setLong(1,studentId); p.setLong(2,classId); one(p.executeUpdate());
        }
    }
    public void reactivate(Connection c, long id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("UPDATE enrollment SET status='ENROLLED',selected_at=CURRENT_TIMESTAMP(6),withdrawn_at=NULL,score=NULL WHERE id=? AND status='WITHDRAWN'")) {
            p.setLong(1,id); one(p.executeUpdate());
        }
    }
    public void withdraw(Connection c, long id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("UPDATE enrollment SET status='WITHDRAWN',withdrawn_at=CURRENT_TIMESTAMP(6) WHERE id=? AND status='ENROLLED' AND score IS NULL")) {
            p.setLong(1,id); one(p.executeUpdate());
        }
    }
    public void setScore(Connection c, long id, BigDecimal score) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("UPDATE enrollment SET score=? WHERE id=? AND status='ENROLLED'")) {
            p.setBigDecimal(1,score); p.setLong(2,id); one(p.executeUpdate());
        }
    }
    private void one(int affected) throws SQLException {
        if (affected != 1) throw new SQLException("选课记录写入影响行数异常：" + affected);
    }
    public List<CourseResult> details(Connection c, long studentId) throws SQLException { return query(c,studentId,false); }
    public List<CourseResult> transcript(Connection c, long studentId) throws SQLException { return query(c,studentId,true); }
    private List<CourseResult> query(Connection c, long studentId, boolean gradedOnly) throws SQLException {
        String sql = "SELECT s.student_no,s.name AS student_name,tc.class_code,c.name AS course_name,c.credits,e.status,e.score "
            + "FROM student s INNER JOIN enrollment e ON e.student_id=s.id "
            + "INNER JOIN teaching_class tc ON tc.id=e.teaching_class_id INNER JOIN course c ON c.id=tc.course_id "
            + "WHERE s.id=? AND e.status='ENROLLED' " + (gradedOnly ? "AND e.score IS NOT NULL " : "") + "ORDER BY tc.class_code";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1,studentId);
            try (ResultSet r = p.executeQuery()) {
                List<CourseResult> rows = new ArrayList<>();
                while (r.next()) rows.add(new CourseResult(r.getString("student_no"),r.getString("student_name"),
                    r.getString("class_code"),r.getString("course_name"),r.getBigDecimal("credits"),r.getString("status"),r.getBigDecimal("score")));
                return rows;
            }
        }
    }
}
