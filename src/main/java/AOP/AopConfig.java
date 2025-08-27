// AopConfig.java
package AOP;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true) // 启用CGLIB代理
public class AopConfig {
    // 配置类内容
}