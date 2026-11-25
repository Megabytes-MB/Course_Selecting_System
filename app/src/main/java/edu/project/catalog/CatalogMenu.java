package edu.project.catalog;

import edu.project.catalog.model.TeacherClass;
import edu.project.catalog.service.TeacherCourseService;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

public final class CatalogMenu {
    private final TeacherCourseService service = new TeacherCourseService();

    public void run(Scanner scanner) {
        while (true) {
            System.out.println("\n教师课程模块 1添加教师 2查询教师 3教师列表 4修改教师 5删除教师"
                    + "\n6添加课程 7查询课程 8课程列表 9修改课程 10删除课程 11按学分查询 12教师教学班 13按学期查询课程 0返回");
            if (!scanner.hasNextLine()) return;
            String choice = scanner.nextLine().strip();
            if (choice.equals("0")) return;
            try {
                switch (choice) {
                    case "1" -> System.out.println("添加成功，ID=" + service.addTeacher(read(scanner, "教师编号"), read(scanner, "教师姓名")));
                    case "2" -> System.out.println(service.teacher(id(scanner, "教师ID")).map(Object::toString).orElse("教师不存在"));
                    case "3" -> print(service.teachers());
                    case "4" -> System.out.println(service.updateTeacher(id(scanner, "教师ID"), read(scanner, "新教师编号"), read(scanner, "新姓名")) ? "修改成功" : "教师不存在");
                    case "5" -> System.out.println(service.deleteTeacher(id(scanner, "教师ID")) ? "删除成功" : "教师不存在");
                    case "6" -> System.out.println("添加成功，ID=" + service.addCourse(read(scanner, "课程代码"), read(scanner, "课程名称"), decimal(scanner, "学分")));
                    case "7" -> System.out.println(service.course(id(scanner, "课程ID")).map(Object::toString).orElse("课程不存在"));
                    case "8" -> print(service.courses());
                    case "9" -> System.out.println(service.updateCourse(id(scanner, "课程ID"), read(scanner, "新课程代码"), read(scanner, "新课程名称"), decimal(scanner, "新学分")) ? "修改成功" : "课程不存在");
                    case "10" -> System.out.println(service.deleteCourse(id(scanner, "课程ID")) ? "删除成功" : "课程不存在");
                    case "11" -> print(service.byMinimumCredits(decimal(scanner, "最低学分")));
                    case "12" -> {
                        List<TeacherClass> rows = service.teacherClasses();
                        if (rows.isEmpty()) System.out.println("无记录");
                        for (TeacherClass row : rows) {
                            System.out.println(row.teacherNo() + " " + row.teacherName() + " | "
                                    + (row.teachingClassId() == null ? "未开课" : row.classCode() + " | " + row.courseName() + " | " + row.semesterName()));
                        }
                    }
                    case "13" -> print(service.bySemester(id(scanner, "学期ID")));
                    default -> System.out.println("请输入菜单编号");
                }
            } catch (IllegalArgumentException e) {
                System.out.println("操作未完成：" + e.getMessage());
            } catch (SQLException e) {
                System.out.println(databaseMessage(e));
            } catch (NoSuchElementException e) {
                return;
            }
        }
    }

    public static String databaseMessage(SQLException e) {
        return switch (e.getErrorCode()) {
            case 1062 -> "操作未完成：教师编号或课程代码重复";
            case 1451 -> "操作未完成：已有关联教学班，不能删除";
            case 1452 -> "操作未完成：关联数据不存在";
            case 3819, 1048, 1406, 1264 -> "操作未完成：字段违反数据库约束或超出范围";
            case 1146 -> "查询所需的表尚未建齐，请按集成顺序初始化正式数据库";
            default -> "数据库操作失败，请检查本地配置、服务与授权（SQLState=" + e.getSQLState() + "，错误码=" + e.getErrorCode() + "）";
        };
    }

    private static String read(Scanner scanner, String label) { System.out.print(label + "："); return scanner.nextLine().strip(); }
    private static long id(Scanner scanner, String label) {
        long value = Long.parseLong(read(scanner, label));
        if (value <= 0) throw new IllegalArgumentException("ID 必须为正整数");
        return value;
    }
    private static BigDecimal decimal(Scanner scanner, String label) { return new BigDecimal(read(scanner, label)); }
    private static void print(List<?> rows) { if (rows.isEmpty()) System.out.println("无记录"); else rows.forEach(System.out::println); }
}
