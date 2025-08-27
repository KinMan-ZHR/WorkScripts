package AOP;

import AOP.service.UserService;
import org.springframework.stereotype.Service;

/**
 * @author ZhangHaoRan or KinMan Zhang
 * @since 2025/7/12 18:24
 */
@Service
public class UserServiceImpl implements UserService {

    @Override
    public User createUser(String username, String email) {
        System.out.println("创建用户: " + username);
        return new User(1L, username, email);
    }

    @Override
    public User getUserById(Long id) {
        System.out.println("获取用户ID: " + id);
        return new User(id, "testUser", "test@example.com");
    }

    @Override
    public void updateUser(User user) {
        System.out.println("更新用户: " + user.getUsername());
    }

    @Override
    public void deleteUser(Long id) {
        System.out.println("删除用户ID: " + id);
        // 模拟异常
        if (id == 0) {
            throw new RuntimeException("无效用户ID");
        }
    }
}
