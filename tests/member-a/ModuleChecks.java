package edu.project.student;

import edu.project.student.dao.*;
import edu.project.student.service.*;
import java.sql.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Standalone integration checks. H2 is a local supplement, not MySQL certification. */
public final class ModuleChecks {
    private static ConnectionProvider provider;
    private static boolean mysql;
    private static Path root;
    private static int passed;
    private static final StudentDAO students = new StudentDAO();
    private static final EnrollmentDAO enrollments = new EnrollmentDAO();
    @FunctionalInterface interface Check { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        root = Path.of(args[0]).toAbsolutePath();
        mysql = args.length > 1;
        if (mysql) {
            Properties config = new Properties();
            try (Reader r = Files.newBufferedReader(Path.of(args[1]))) { config.load(r); }
            String url = config.getProperty("url", "");
            // Fail closed: only a specifically named, dedicated test database may be used.
            if (!url.matches("jdbc:mysql://[^/]+/member_a_test_[A-Za-z0-9_]+(?:\\?.*)?"))
                throw new IllegalArgumentException("URL必须指向member_a_test_开头的独立空测试库");
            provider = () -> DriverManager.getConnection(url,config.getProperty("user"),config.getProperty("password"));
        } else {
            String url = "jdbc:h2:mem:member_a_checks;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
            provider = () -> DriverManager.getConnection(url,"sa","");
        }
        // Keep the DDL session alive: H2 2.4.240 CHECK IN evaluation retained its session.
        Connection keeper = provider.open();
        { Connection c = keeper;
            try (ResultSet tables = c.getMetaData().getTables(c.getCatalog(),mysql ? null : "PUBLIC","%",new String[]{"TABLE"})) {
                if (tables.next()) throw new IllegalStateException("测试库不是空库，拒绝创建和清理");
            }
            System.out.println("Database: " + c.getMetaData().getDatabaseProductName() + " " + c.getMetaData().getDatabaseProductVersion());
            script(c,"database/member-a/01_create_student.sql");
            try (Statement s = c.createStatement()) {
                // Minimal read/lock fixtures, never production B/C module implementations.
                s.execute("CREATE TABLE course(id BIGINT PRIMARY KEY,name VARCHAR(100) NOT NULL,credits DECIMAL(4,1) NOT NULL)");
                s.execute("CREATE TABLE teaching_class(id BIGINT PRIMARY KEY,class_code VARCHAR(30) NOT NULL,course_id BIGINT NOT NULL,semester_id BIGINT NOT NULL,capacity INT NOT NULL CHECK(capacity>0),FOREIGN KEY(course_id) REFERENCES course(id))");
            }
            script(c,"database/member-a/04_create_enrollment.sql");
        }
        run("A01 student CRUD, generated key, missing row", () -> {
            try (Connection c = provider.open()) {
                long id = students.insert(c,"NEW01","测试学生","测试专业");
                eq("测试学生",students.findById(c,id).orElseThrow().name());
                yes(students.update(c,id,"NEW02","修改姓名","软件工程"));
                eq("修改姓名",students.findById(c,id).orElseThrow().name());
                yes(students.delete(c,id)); yes(students.findById(c,id).isEmpty()); yes(!students.delete(c,id));
            }
        });
        run("A02 duplicate number, blank and overlength rejected", () -> {
            try (Connection c = provider.open()) {
                rejects(SQLException.class, () -> students.insert(c,"20260001","重复","软件工程"));
                rejects(IllegalArgumentException.class, () -> students.insert(c," ","姓名","专业"));
                rejects(IllegalArgumentException.class, () -> students.insert(c,"x".repeat(21),"姓名","专业"));
                rejects(IllegalArgumentException.class, () -> students.insert(c,"BADENC","\uFFFD","专业"));
                eq(10L,scalar(c,"SELECT COUNT(*) FROM student"));
            }
        });
        run("A03 referenced student delete restricted", () -> {
            try (Connection c = provider.open()) { rejects(SQLException.class, () -> students.delete(c,1001)); }
        });
        run("A04 enroll success and duplicate rollback", () -> {
            EnrollmentService service = new EnrollmentService(provider);
            service.enroll(1010,2001);
            rejects(BusinessException.class, () -> service.enroll(1010,2001));
            try (Connection c = provider.open()) { eq(1L,scalar(c,"SELECT COUNT(*) FROM enrollment WHERE student_id=1010 AND teaching_class_id=2001")); }
        });
        run("A05 full class refuses without write", () -> {
            try (Connection c = provider.open(); Statement s = c.createStatement()) { s.executeUpdate("UPDATE teaching_class SET capacity=1 WHERE id=2001"); }
            rejects(BusinessException.class, () -> new EnrollmentService(provider).enroll(1010,2001));
            try (Connection c = provider.open()) { eq(10L,scalar(c,"SELECT COUNT(*) FROM enrollment")); }
        });
        run("A06 withdraw and reactivate preserve id/time rules", () -> {
            EnrollmentService service = new EnrollmentService(provider);
            service.withdraw(1001,2002);
            long id;
            try (Connection c = provider.open()) {
                var row = enrollments.find(c,1001,2002).orElseThrow(); id = row.id();
                eq("WITHDRAWN",row.status()); yes(row.withdrawnAt() != null);
            }
            rejects(BusinessException.class, () -> service.withdraw(1001,2002));
            service.enroll(1001,2002);
            try (Connection c = provider.open()) {
                var row = enrollments.find(c,1001,2002).orElseThrow();
                eq(id,row.id()); eq("ENROLLED",row.status()); yes(row.withdrawnAt() == null); yes(row.score() == null);
                eq(10L,scalar(c,"SELECT COUNT(*) FROM enrollment"));
            }
        });
        run("A07 grade boundaries, invalid input, withdrawn and graded rules", () -> {
            GradeService grades = new GradeService(provider);
            grades.record(1001,2002,new BigDecimal("0")); grades.record(1001,2002,new BigDecimal("100.00"));
            grades.record(1001,2002,new BigDecimal("90.50"));
            for (String invalid : List.of("-0.01","100.01","1.001"))
                rejects(BusinessException.class, () -> grades.record(1001,2002,new BigDecimal(invalid)));
            rejects(BusinessException.class, () -> grades.record(1001,2002,null));
            rejects(BusinessException.class, () -> grades.record(1008,2009,BigDecimal.TEN));
            rejects(BusinessException.class, () -> new EnrollmentService(provider).withdraw(1001,2002));
            try (Connection c = provider.open()) { eq(0,enrollments.find(c,1001,2002).orElseThrow().score().compareTo(new BigDecimal("90.50"))); }
        });
        run("A08 missing student/class/relationship", () -> {
            EnrollmentService service = new EnrollmentService(provider);
            rejects(BusinessException.class, () -> service.enroll(9999,2001));
            rejects(BusinessException.class, () -> service.enroll(1001,9999));
            rejects(BusinessException.class, () -> service.withdraw(1010,2001));
        });
        run("A09 injected failure after write rolls back", () -> {
            ConnectionProvider failing = () -> {
                Connection real = provider.open();
                return (Connection) Proxy.newProxyInstance(ModuleChecks.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,values) -> {
                    if (method.getName().equals("commit")) throw new SQLException("Injected failure before commit", "45000");
                    try { return method.invoke(real,values); } catch (InvocationTargetException e) { throw e.getCause(); }
                });
            };
            rejects(SQLException.class, () -> new EnrollmentService(failing).enroll(1010,2001));
            try (Connection c = provider.open()) { eq(10L,scalar(c,"SELECT COUNT(*) FROM enrollment")); eq(0L,scalar(c,"SELECT COUNT(*) FROM enrollment WHERE student_id=1010")); }
        });
        run("A10 joins, null score, zero score, NOT EXISTS, sorted major", () -> {
            try (Connection c = provider.open()) {
                eq(2,enrollments.details(c,1001).size()); eq(1,enrollments.transcript(c,1001).size());
                yes(enrollments.details(c,1001).stream().anyMatch(row -> row.score() == null));
                eq(0,enrollments.transcript(c,1002).get(0).score().compareTo(BigDecimal.ZERO));
                eq(4,students.findByMajor(c,"计算机科学与技术").size());
                eq(List.of(1008L,1010L),ids(c,"SELECT s.id FROM student s WHERE NOT EXISTS(SELECT 1 FROM enrollment e WHERE e.student_id=s.id AND e.status='ENROLLED') ORDER BY s.id"));
                script(c,"database/member-a/queries.sql");
            }
        });
        run("A11 database CHECK/UNIQUE/FK block illegal records", () -> {
            try (Connection c = provider.open(); Statement s = c.createStatement()) {
                for (String sql : List.of(
                    "UPDATE enrollment SET score=101 WHERE id=3001",
                    "UPDATE enrollment SET status='BAD' WHERE id=3001",
                    "UPDATE enrollment SET status='enrolled' WHERE id=3001",
                    "UPDATE enrollment SET status='ENROLLED ' WHERE id=3001",
                    "UPDATE enrollment SET status='WITHDRAWN' WHERE id=3001",
                    "UPDATE enrollment SET score=10 WHERE id=3009",
                    "UPDATE enrollment SET withdrawn_at='2026-01-01 00:00:00' WHERE id=3009",
                    "INSERT INTO enrollment(student_id,teaching_class_id) VALUES(9999,2001)",
                    "INSERT INTO enrollment(student_id,teaching_class_id) VALUES(1001,9999)",
                    "INSERT INTO enrollment(student_id,teaching_class_id) VALUES(1001,2001)",
                    "INSERT INTO student(student_no,name,major) VALUES('BLANK','   ','专业')")) {
                    rejects(SQLException.class, () -> s.executeUpdate(sql));
                }
            }
        });
        run("A12 concurrent last seat has one winner", () -> {
            try (Connection c = provider.open(); Statement s = c.createStatement()) { s.executeUpdate("UPDATE teaching_class SET capacity=2 WHERE id=2001"); }
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Future<Boolean>> futures = new ArrayList<>();
                for (long studentId : new long[]{1009,1010}) futures.add(pool.submit(() -> {
                    start.await();
                    try { new EnrollmentService(provider).enroll(studentId,2001); return true; }
                    catch (BusinessException expected) { return false; }
                }));
                start.countDown(); int winners = 0;
                for (Future<Boolean> result : futures) if (result.get(20,TimeUnit.SECONDS)) winners++;
                eq(1,winners);
                try (Connection c = provider.open()) { eq(2L,enrollments.activeCount(c,2001)); }
            } finally { pool.shutdownNow(); }
        });
        run("A13 failure after reactivation/withdraw/grade restores original", () -> {
            ConnectionProvider failing = () -> {
                Connection real = provider.open();
                return (Connection) Proxy.newProxyInstance(ModuleChecks.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,values) -> {
                    if (method.getName().equals("commit")) throw new SQLException("Injected failure", "45000");
                    try { return method.invoke(real,values); } catch (InvocationTargetException e) { throw e.getCause(); }
                });
            };
            rejects(SQLException.class, () -> new EnrollmentService(failing).enroll(1008,2009));
            rejects(SQLException.class, () -> new EnrollmentService(failing).withdraw(1001,2002));
            rejects(SQLException.class, () -> new GradeService(failing).record(1001,2002,BigDecimal.TEN));
            try (Connection c = provider.open()) {
                eq("WITHDRAWN",enrollments.find(c,1008,2009).orElseThrow().status());
                var row = enrollments.find(c,1001,2002).orElseThrow();
                eq("ENROLLED",row.status()); yes(row.score() == null); yes(row.withdrawnAt() == null);
            }
        });
        run("A14 menu handles invalid input and does not close shared scanner", () -> {
            Scanner input = new Scanner("2\nnot-a-number\n2\n99999\n0\nremaining\n");
            new StudentMenu(provider).run(input); eq("remaining",input.nextLine()); input.close();
        });
        keeper.close();
        System.out.println("RESULT: " + passed + " / 14 passed; backend=" + (mysql ? "MySQL" : "H2 (supplement only)"));
    }
    private static void run(String name, Check test) throws Exception {
        reset(); test.run(); passed++; System.out.println("PASS " + name);
    }
    private static void reset() throws Exception {
        try (Connection c = provider.open(); Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM enrollment"); s.executeUpdate("DELETE FROM student");
            s.executeUpdate("DELETE FROM teaching_class"); s.executeUpdate("DELETE FROM course");
            for (int i=1;i<=10;i++) {
                s.executeUpdate("INSERT INTO course VALUES(" + i + ",'Course " + i + "',3.0)");
                s.executeUpdate("INSERT INTO teaching_class VALUES(" + (2000+i) + ",'CLASS" + i + "'," + i + ",1,3)");
            }
            script(c,"database/member-a/11_seed_student.sql"); script(c,"database/member-a/14_seed_enrollment.sql");
        }
    }
    private static void script(Connection c, String path) throws Exception {
        String sql = Files.readString(root.resolve(path)).replaceAll("(?m)^\\s*--[^\\r\\n]*", "");
        if (!mysql) sql = sql.replace("CHARACTER SET ascii COLLATE ascii_bin", "")
            .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        try (Statement s = c.createStatement()) {
            for (String part : sql.split(";")) if (!part.isBlank()) s.execute(part);
        }
    }
    private static long scalar(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) { r.next(); return r.getLong(1); }
    }
    private static List<Long> ids(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            List<Long> ids = new ArrayList<>(); while (r.next()) ids.add(r.getLong(1)); return ids;
        }
    }
    private static void eq(Object expected, Object actual) {
        if (!Objects.equals(expected,actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void yes(boolean value) { if (!value) throw new AssertionError("Condition failed"); }
    private static void rejects(Class<? extends Throwable> type, Check code) throws Exception {
        try { code.run(); } catch (Throwable e) {
            if (type.isInstance(e)) return;
            if (e instanceof Exception ex) throw ex;
            throw (Error)e;
        }
        throw new AssertionError("Expected rejection: " + type.getSimpleName());
    }
}
