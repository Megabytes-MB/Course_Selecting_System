package edu.project;

import edu.project.catalog.CatalogMenu;
import edu.project.common.DBUtil;
import edu.project.student.StudentMenu;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            run(scanner);
        }
    }

    public static void run(Scanner scanner) {
        while (true) {
            System.out.println("\n高校选课系统 1学生与选课成绩 2教师与课程 3学期与教学班 0退出");
            if (!scanner.hasNextLine()) return;
            switch (scanner.nextLine().strip()) {
                case "0": return;
                case "1": new StudentMenu(DBUtil::getConnection).run(scanner); break;
                case "2": new CatalogMenu().run(scanner); break;
                case "3": runTeaching(scanner); break;
                default: System.out.println("请输入菜单编号");
            }
        }
    }

    /** C is integrated later; absence must not prevent the B branch from compiling. */
    private static void runTeaching(Scanner scanner) {
        try {
            Class<?> type = Class.forName("edu.project.teaching.TeachingMenu");
            Object menu = type.getConstructor().newInstance();
            type.getMethod("run", Scanner.class).invoke(menu, scanner);
        } catch (ClassNotFoundException e) {
            System.out.println("学期与教学班模块尚未集成，请合并成员 C 的正式实现后再使用");
        } catch (ReflectiveOperationException | LinkageError e) {
            // Do not expose the invoked exception, which could include connection credentials.
            String kind = e instanceof InvocationTargetException ? "运行失败" : "接口不兼容";
            System.out.println("教学模块" + kind + "，请核对无参构造器和 run(Scanner) 接口");
        }
    }
}
