// AopDemoApplication.java
package AOP;

import AOP.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AopDemoApplication implements CommandLineRunner {

    @Autowired
    private UserService userService;

    public static void main(String[] args) {
        SpringApplication.run(AopDemoApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("===== 测试AOP =====");
        
        // 创建用户
        User user = userService.createUser("john_doe", "john@example.com");
        
        // 获取用户
        userService.getUserById(1L);
        
        // 更新用户
        user.setEmail("updated@example.com");
        userService.updateUser(user);
        
        // 删除用户（正常情况）
        userService.deleteUser(1L);
        
        // 删除用户（异常情况）
        try {
            userService.deleteUser(0L);
        } catch (Exception e) {
            // 异常已在切面中处理
        }
    }
}