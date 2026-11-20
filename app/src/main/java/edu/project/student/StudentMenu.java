package edu.project.student;

import edu.project.student.dao.*;
import edu.project.student.service.*;
import java.sql.*;
import java.math.BigDecimal;
import java.util.Scanner;

public final class StudentMenu {
    private final ConnectionProvider provider;
    private final StudentDAO students = new StudentDAO();
    private final EnrollmentDAO enrollments = new EnrollmentDAO();
    private final EnrollmentService enrollmentService;
    private final GradeService gradeService;
    public StudentMenu(ConnectionProvider provider) {
        this.provider = provider;
        enrollmentService = new EnrollmentService(provider); gradeService = new GradeService(provider);
    }
    public void run(Scanner scanner) {
        while (true) {
            System.out.println("\n学生模块 1添加 2按ID查询 3按专业查询 4修改 5删除 6选课 7退课 8成绩录入/修改 9选课明细 10成绩单 0返回");
            if (!scanner.hasNextLine()) return;
            String choice = scanner.nextLine().strip();
            if (choice.equals("0")) return;
            try {
                switch (choice) {
                    case "1" -> {
                        String no = read(scanner,"学号"), name = read(scanner,"姓名"), major = read(scanner,"专业");
                        try (Connection c = provider.open()) { System.out.println("添加成功，ID=" + students.insert(c,no,name,major)); }
                    }
                    case "2" -> {
                        long id = id(scanner,"学生ID");
                        try (Connection c = provider.open()) { System.out.println(students.findById(c,id).map(Object::toString).orElse("学生不存在")); }
                    }
                    case "3" -> {
                        String major = read(scanner,"专业");
                        try (Connection c = provider.open()) { print(students.findByMajor(c,major)); }
                    }
                    case "4" -> {
                        long id = id(scanner,"学生ID");
                        String no = read(scanner,"新学号"), name = read(scanner,"新姓名"), major = read(scanner,"新专业");
                        try (Connection c = provider.open()) { System.out.println(students.update(c,id,no,name,major) ? "修改成功" : "学生不存在"); }
                    }
                    case "5" -> {
                        long id = id(scanner,"学生ID");
                        try (Connection c = provider.open()) { System.out.println(students.delete(c,id) ? "删除成功" : "学生不存在"); }
                    }
                    case "6", "7", "8" -> {
                        long studentId = id(scanner,"学生ID"), classId = id(scanner,"教学班ID");
                        if (choice.equals("6")) enrollmentService.enroll(studentId,classId);
                        else if (choice.equals("7")) enrollmentService.withdraw(studentId,classId);
                        else gradeService.record(studentId,classId,new BigDecimal(read(scanner,"成绩")));
                        System.out.println("操作成功");
                    }
                    case "9", "10" -> {
                        long id = id(scanner,"学生ID");
                        try (Connection c = provider.open()) {
                            if (students.findById(c,id).isEmpty()) System.out.println("学生不存在");
                            else print(choice.equals("9") ? enrollments.details(c,id) : enrollments.transcript(c,id));
                        }
                    }
                    default -> System.out.println("请输入菜单编号");
                }
            } catch (IllegalArgumentException | BusinessException e) {
                System.out.println("操作未完成：" + e.getMessage());
            } catch (SQLException e) {
                if (e.getErrorCode() == 1062) System.out.println("操作未完成：学号或选课记录重复");
                else if (e.getErrorCode() == 1451) System.out.println("操作未完成：存在关联业务记录，不能删除");
                else if (e.getErrorCode() == 1452) System.out.println("操作未完成：关联数据不存在");
                else if ("40001".equals(e.getSQLState())) System.out.println("并发冲突，本次操作已回滚，请重试");
                else System.out.println("数据库操作失败，SQLState=" + e.getSQLState() + "，错误码=" + e.getErrorCode());
            } catch (java.util.NoSuchElementException e) { return; }
        }
    }
    private static String read(Scanner scanner, String label) { System.out.print(label + "："); return scanner.nextLine().strip(); }
    private static long id(Scanner scanner, String label) {
        long value = Long.parseLong(read(scanner,label));
        if (value <= 0) throw new IllegalArgumentException("ID必须为正整数");
        return value;
    }
    private static void print(java.util.List<?> rows) {
        if (rows.isEmpty()) System.out.println("无记录"); else rows.forEach(System.out::println);
    }
}
