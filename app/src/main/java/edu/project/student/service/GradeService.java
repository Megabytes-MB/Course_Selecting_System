package edu.project.student.service;

import edu.project.student.ConnectionProvider;
import edu.project.student.dao.EnrollmentDAO;
import edu.project.student.model.Enrollment;
import java.math.BigDecimal;
import java.sql.SQLException;

public final class GradeService {
    private final ConnectionProvider provider;
    private final EnrollmentDAO enrollments = new EnrollmentDAO();
    public GradeService(ConnectionProvider provider) { this.provider = provider; }
    public void record(long studentId, long classId, BigDecimal score) throws SQLException {
        if (score == null || score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(new BigDecimal("100")) > 0
                || score.stripTrailingZeros().scale() > 2)
            throw new BusinessException("成绩必须为0—100且最多两位小数");
        Transactions.execute(provider, c -> {
            if (enrollments.lockClass(c,classId) < 1) throw new BusinessException("教学班不存在");
            Enrollment row = enrollments.find(c,studentId,classId).orElseThrow(() -> new BusinessException("选课记录不存在"));
            if (!row.status().equals("ENROLLED")) throw new BusinessException("仅已选记录可录入成绩");
            enrollments.setScore(c,row.id(),score);
            return null;
        });
    }
}
