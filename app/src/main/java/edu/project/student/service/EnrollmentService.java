package edu.project.student.service;

import edu.project.student.ConnectionProvider;
import edu.project.student.dao.*;
import edu.project.student.model.Enrollment;
import java.sql.SQLException;
import java.util.Optional;

public final class EnrollmentService {
    private final ConnectionProvider provider;
    private final EnrollmentDAO enrollments = new EnrollmentDAO();
    private final StudentDAO students = new StudentDAO();
    public EnrollmentService(ConnectionProvider provider) { this.provider = provider; }
    public void enroll(long studentId, long classId) throws SQLException {
        Transactions.execute(provider, c -> {
            int capacity = enrollments.lockClass(c,classId);
            if (capacity < 1) throw new BusinessException("教学班不存在或容量非法");
            if (students.findById(c,studentId).isEmpty()) throw new BusinessException("学生不存在");
            Optional<Enrollment> current = enrollments.find(c,studentId,classId);
            if (current.isPresent() && current.get().status().equals("ENROLLED"))
                throw new BusinessException("不能重复选课");
            if (enrollments.activeCount(c,classId) >= capacity) throw new BusinessException("教学班已满");
            if (current.isPresent()) enrollments.reactivate(c,current.get().id());
            else enrollments.insert(c,studentId,classId);
            return null;
        });
    }
    public void withdraw(long studentId, long classId) throws SQLException {
        Transactions.execute(provider, c -> {
            if (enrollments.lockClass(c,classId) < 1) throw new BusinessException("教学班不存在");
            Enrollment row = enrollments.find(c,studentId,classId).orElseThrow(() -> new BusinessException("选课记录不存在"));
            if (!row.status().equals("ENROLLED")) throw new BusinessException("该记录已退课");
            if (row.score() != null) throw new BusinessException("已录入成绩，不能退课");
            enrollments.withdraw(c,row.id());
            return null;
        });
    }
}
