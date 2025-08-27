// UserService.java
package AOP.service;

import AOP.User;

public interface UserService {
    User createUser(String username, String email);
    User getUserById(Long id);
    void updateUser(User user);
    void deleteUser(Long id);
}


