package edu.project.catalog.checks;

import edu.project.Main;
import edu.project.catalog.CatalogMenu;
import edu.project.catalog.dao.CourseDAO;
import edu.project.catalog.dao.TeacherDAO;
import edu.project.catalog.model.Course;
import edu.project.catalog.model.TeacherClass;
import edu.project.catalog.service.TeacherCourseService;
import edu.project.common.DBUtil;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Real MySQL integration checks; refuses nonempty or non-test databases. */
public final class ModuleChecks {
    @FunctionalInterface interface Action { void run() throws Exception; }
    private static int total;
    private static int passed;
    private static final TeacherCourseService service = new TeacherCourseService();

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        try (Connection c = DBUtil.getConnection()) {
            String database = c.getCatalog();
            require(database != null && database.startsWith("member_b_test_"), "database must start with member_b_test_");
            require(c.getMetaData().getDatabaseProductName().equals("MySQL"), "real MySQL required");
            boolean existing = args.length > 1 && args[1].equals("--existing");
            if (!existing) try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=?")) {
                p.setString(1, database);
                try (ResultSet r = p.executeQuery()) { r.next(); require(r.getInt(1) == 0, "database must be empty; no existing data is reset"); }
            }
            System.out.println("ENV MySQL=" + c.getMetaData().getDatabaseProductVersion() + "; Java=" + System.getProperty("java.version"));
            if (!existing) {
            script(c, root.resolve("database/member-a/01_create_student.sql"));
            script(c, root.resolve("database/member-b/02_create_teacher_course.sql"));
            // C is absent from main; dependency fixture stays strictly inside the test directory.
            script(c, root.resolve("database/member-b/12_seed_teacher_course.sql"));
            script(c, root.resolve("tests/member-b/fixtures/cross-module.sql"));
            script(c, root.resolve("database/member-a/04_create_enrollment.sql"));
            script(c, root.resolve("database/member-a/11_seed_student.sql"));
            script(c, root.resolve("database/member-a/14_seed_enrollment.sql"));
            }
        }

        check("B01 initialization", "six tables each contain 10 rows", () -> {
            for (String table : List.of("teacher", "course", "student", "semester", "teaching_class", "enrollment"))
                require(scalar("SELECT COUNT(*) FROM " + table) == 10, table + " count");
        });
        check("B02 teacher CRUD and Chinese roundtrip", "create/read/update/delete one teacher", () -> {
            long id = service.addTeacher("TEST_TEACHER", "中文教师😀");
            require(service.teacher(id).orElseThrow().name().equals("中文教师😀"), "UTF-8 exact roundtrip");
            require(service.updateTeacher(id, "TEST_TEACHER_2", "修改姓名"), "teacher updated");
            require(service.teacher(id).orElseThrow().name().equals("修改姓名"), "update visible");
            require(service.deleteTeacher(id) && service.teacher(id).isEmpty(), "teacher deleted");
        });
        check("B03 course CRUD and exact decimal", "3.5 credits and Chinese name survive roundtrip", () -> {
            long id = service.addCourse("TEST_COURSE", "中文课程", new BigDecimal("3.5"));
            require(service.course(id).orElseThrow().credits().equals(new BigDecimal("3.5")), "exact decimal");
            require(service.updateCourse(id, "TEST_COURSE_2", "课程修改", new BigDecimal("4.0")), "course updated");
            require(service.course(id).orElseThrow().name().equals("课程修改"), "updated name");
            require(service.deleteCourse(id) && service.course(id).isEmpty(), "course deleted");
        });
        check("B04 duplicate teacher", "1062; seed remains unchanged", () -> {
            sqlError(1062, () -> service.addTeacher("T001", "重复教师"));
            require(scalar("SELECT COUNT(*) FROM teacher") == 10, "teacher unchanged");
        });
        check("B05 duplicate course", "1062; seed remains unchanged", () -> {
            sqlError(1062, () -> service.addCourse("CS001", "重复课程", new BigDecimal("3.0")));
            require(scalar("SELECT COUNT(*) FROM course") == 10, "course unchanged");
        });
        check("B06 credit boundaries", "reject 0/-1/3.55/1000; accept 0.1/999.9/3.50", () -> {
            for (String value : List.of("0", "-1", "3.55", "1000"))
                invalid(() -> service.addCourse("BAD_CREDITS", "错误学分", new BigDecimal(value)));
            invalid(() -> service.addCourse("BAD_CREDITS", "空学分", null));
            for (String value : List.of("0.1", "999.9", "3.50")) {
                long id = service.addCourse("BOUNDARY", "边界课程", new BigDecimal(value));
                require(service.course(id).orElseThrow().credits().compareTo(new BigDecimal(value)) == 0, "boundary roundtrip");
                service.deleteCourse(id);
            }
        });
        check("B07 invalid text", "blank/overlength/replacement character rejected", () -> {
            invalid(() -> service.addTeacher("\t  ", "教师"));
            invalid(() -> service.addTeacher("X".repeat(21), "教师"));
            invalid(() -> service.addTeacher("INVALID", "\uFFFD"));
            invalid(() -> service.addCourse("INVALID", "\u2003", BigDecimal.ONE));
            invalid(() -> service.addCourse("INVALID", "课".repeat(101), BigDecimal.ONE));
        });
        check("B08 direct SQL constraints", "CHECK=3819; NOT NULL=1048", () -> {
            sqlError(3819, () -> execute("INSERT INTO teacher(teacher_no,name) VALUES('BAD_NAME',CHAR(9))"));
            sqlError(3819, () -> execute("INSERT INTO course(course_code,name,credits) VALUES('   ','bad',1.0)"));
            sqlError(3819, () -> execute("INSERT INTO course(course_code,name,credits) VALUES('BAD_ZERO','bad',0)"));
            sqlError(1048, () -> execute("INSERT INTO course(course_code,name,credits) VALUES('BAD_NULL',NULL,1.0)"));
        });
        check("B09 referenced course delete", "1451; course and referencing classes preserved", () -> {
            sqlError(1451, () -> service.deleteCourse(1201));
            require(service.course(1201).isPresent() && scalar("SELECT COUNT(*) FROM teaching_class WHERE course_id=1201") == 2, "references preserved");
        });
        check("B10 referenced teacher delete", "1451; teacher remains", () -> {
            sqlError(1451, () -> service.deleteTeacher(1101));
            require(service.teacher(1101).isPresent(), "teacher remains");
        });
        check("B11 absent records", "empty lookup; update/delete false; bad ID rejected", () -> {
            require(service.course(999999).isEmpty() && service.teacher(999999).isEmpty(), "empty lookup");
            require(!service.updateCourse(999999, "ABSENT", "不存在", BigDecimal.ONE), "absent course update");
            require(!service.deleteCourse(999999) && !service.deleteTeacher(999999), "absent delete");
            require(!service.updateTeacher(999999, "ABSENT", "不存在"), "absent teacher update");
            invalid(() -> service.course(0));
        });
        check("B12 Q1 filter/order", "CS001,CS003,CS004,CS002,CS005,CS010", () -> {
            List<String> actual = service.byMinimumCredits(new BigDecimal("3.0")).stream().map(Course::courseCode).toList();
            require(actual.equals(List.of("CS001", "CS003", "CS004", "CS002", "CS005", "CS010")), "Q1 actual=" + actual);
            require(service.byMinimumCredits(new BigDecimal("1000")).isEmpty(), "empty threshold");
            invalid(() -> service.byMinimumCredits(new BigDecimal("-1")));
        });
        check("B13 Q2 LEFT JOIN", "11 rows; teacher 1110 retained with null class", () -> {
            List<TeacherClass> rows = service.teacherClasses();
            require(rows.size() == 11, "Q2 row count=" + rows.size());
            require(rows.stream().anyMatch(r -> r.teacherId() == 1110 && r.teachingClassId() == null), "unassigned teacher retained");
            require(rows.stream().filter(r -> r.teacherId() == 1101).count() == 2, "multiple classes preserved");
        });
        check("B14 Q3 IN subquery", "semester 1301 returns course 1201 once; unknown semester empty", () -> {
            List<Course> rows = service.bySemester(1301);
            require(rows.size() == 1 && rows.get(0).id() == 1201, "duplicate classes do not duplicate course");
            require(service.bySemester(999999).isEmpty(), "unknown semester empty");
        });
        check("B15 PreparedStatement input", "SQL-looking number stored as literal; no extra rows affected", () -> {
            String input = "' OR 1=1 --";
            long id = service.addTeacher(input, "参数化教师");
            require(service.teacher(id).orElseThrow().teacherNo().equals(input), "literal input preserved");
            require(scalar("SELECT COUNT(*) FROM teacher") == 11, "exactly one inserted");
            service.deleteTeacher(id);
        });
        check("B16 connection ownership", "fresh autoCommit connections; one close does not close another", () -> {
            try (Connection first = DBUtil.getConnection(); Connection second = DBUtil.getConnection()) {
                require(first != second && first.getAutoCommit() && second.getAutoCommit(), "independent defaults");
                first.close();
                require(!second.isClosed() && second.isValid(2), "second survives");
            }
        });
        check("B17 DAO rollback ownership", "caller rollback removes uncommitted teacher and course", () -> {
            try (Connection c = DBUtil.getConnection()) {
                c.setAutoCommit(false);
                try {
                    long teacher = new TeacherDAO().insert(c, "ROLLBACK_TEACHER", "回滚教师");
                    long course = new CourseDAO().insert(c, "ROLLBACK_COURSE", "回滚课程", BigDecimal.ONE);
                    c.rollback();
                    require(service.teacher(teacher).isEmpty() && service.course(course).isEmpty(), "DAO never commits");
                } catch (Exception e) {
                    try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                    throw e;
                }
            }
        });
        check("B18 main/shared Scanner", "enter A/B, return, then main remains usable", () -> {
            String output = capture(() -> Main.run(new Scanner("1\n0\n2\n7\n1201\n0\n0\n")));
            require(output.contains("学生模块") && output.contains("教师课程模块") && output.contains("数据库系统"), "both menus execute");
            require(output.split("高校选课系统", -1).length >= 4, "returned to main after both menus");
        });
        check("B19 recover from business/database errors", "duplicate and FK rejection followed by successful query", () -> {
            String output = capture(() -> new CatalogMenu().run(new Scanner("6\nCS001\n重复\n3\n10\n1201\n7\n1201\n0\n")));
            require(output.contains("重复") && output.contains("不能删除") && output.contains("数据库系统"), "menu survives errors");
        });
        check("B20 missing configuration", "SQLException and usable menu without secret disclosure", () -> {
            String original = System.getProperty("db.config");
            try {
                System.setProperty("db.config", root.resolve("tests/member-b/build/nonexistent.properties").toString());
                try { DBUtil.getConnection(); throw new AssertionError("missing config accepted"); }
                catch (SQLException e) { require("08001".equals(e.getSQLState()), "configuration SQLState"); }
                String output = capture(() -> new CatalogMenu().run(new Scanner("7\n1201\n0\n")));
                require(output.contains("数据库操作失败") && !output.contains("jdbc:mysql"), "safe menu error");
            } finally { System.setProperty("db.config", original); }
        });
        check("B21 query SQL file", "all 3 saved SQL queries execute on MySQL", () -> {
            try (Connection c = DBUtil.getConnection()) { script(c, root.resolve("database/member-b/queries.sql")); }
        });
        check("B22 absent C module", "clear message and return to main", () -> {
            try { Class.forName("edu.project.teaching.TeachingMenu"); }
            catch (ClassNotFoundException e) {
                String output = capture(() -> Main.run(new Scanner("3\n2\n0\n0\n")));
                require(output.contains("尚未集成") && output.contains("教师课程模块"), "absence is explicit, scanner reusable");
            }
        });
        check("B23 invalid configuration path", "invalid path becomes configuration SQLException", () -> {
            String original = System.getProperty("db.config");
            try {
                System.setProperty("db.config", "invalid\u0000path");
                try { DBUtil.getConnection(); throw new AssertionError("invalid path accepted"); }
                catch (SQLException e) { require("08001".equals(e.getSQLState()), "configuration SQLState"); }
            } finally { System.setProperty("db.config", original); }
        });
        check("B24 unchanged update with affected-row mode", "existing unchanged row must not be reported missing", () -> {
            Path config = Path.of(System.getProperty("db.config"));
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) { properties.load(reader); }
            properties.setProperty("url", properties.getProperty("url") + "&useAffectedRows=true");
            Path temporary = Files.createTempFile(root.resolve("tests/member-b/build"), "affected-", ".properties");
            try {
                try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { properties.store(writer, "private test configuration"); }
                System.setProperty("db.config", temporary.toString());
                String output = capture(() -> new CatalogMenu().run(new Scanner("9\n1201\nCS001\n数据库系统\n4.0\n0\n")));
                require(!output.contains("课程不存在"), "existing unchanged course was reported missing");
            } finally {
                System.setProperty("db.config", config.toString()); Files.deleteIfExists(temporary);
            }
        });
        System.out.println("RESULT: " + passed + " / " + total + " passed; backend=MySQL; C dependencies=TEST FIXTURE ONLY");
        if (passed != total) System.exit(1);
    }

    private static void check(String name, String expected, Action action) {
        total++;
        try {
            action.run(); passed++;
            System.out.println("PASS " + name + " | expected=" + expected + " | actual=matched");
        } catch (Throwable e) {
            System.out.println("FAIL " + name + " | expected=" + expected + " | actual=" + e.getClass().getSimpleName());
            // Driver exception messages may contain connection information. Print only assertion details.
            if (e instanceof AssertionError) System.out.println(e.getMessage());
            else if (e instanceof SQLException s) System.out.println("SQLState=" + s.getSQLState() + "; code=" + s.getErrorCode());
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void invalid(Action action) throws Exception {
        try { action.run(); throw new AssertionError("invalid input accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void sqlError(int code, Action action) throws Exception {
        try { action.run(); throw new AssertionError("SQL accepted; expected error " + code); }
        catch (SQLException expected) { require(expected.getErrorCode() == code, "expected code=" + code + ", actual=" + expected.getErrorCode()); }
    }
    private static long scalar(String sql) throws SQLException {
        try (Connection c = DBUtil.getConnection(); PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) { r.next(); return r.getLong(1); }
    }
    private static void execute(String sql) throws SQLException {
        try (Connection c = DBUtil.getConnection(); PreparedStatement p = c.prepareStatement(sql)) { p.executeUpdate(); }
    }
    private static void script(Connection c, Path path) throws IOException, SQLException {
        String sql = Files.readString(path, StandardCharsets.UTF_8).replaceAll("(?m)^\\s*--[^\\r\\n]*", "");
        for (String statement : sql.split(";")) {
            if (!statement.isBlank()) try (Statement s = c.createStatement()) { s.execute(statement); }
        }
    }
    private static String capture(Action action) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream target = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(target); action.run();
        } finally { System.setOut(original); }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
