// LoggingAspect.java
package AOP;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingAspect {
    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    // 定义切入点：匹配service包下的所有方法
    @Pointcut("execution(* AOP.service.*.*(..))")
    public void servicePointcut() {}

    // 前置通知：在方法执行前执行
    @Before("servicePointcut()")
    public void beforeAdvice(JoinPoint joinPoint) {
        logger.info("前置通知：即将执行方法 {}", joinPoint.getSignature().getName());
        logger.info("方法参数：{}", joinPoint.getArgs());
    }

    // 后置通知：在方法执行后执行（无论是否发生异常）
    @After("servicePointcut()")
    public void afterAdvice(JoinPoint joinPoint) {
        logger.info("后置通知：方法 {} 执行完毕", joinPoint.getSignature().getName());
    }

    // 返回通知：在方法正常返回后执行
    @AfterReturning(pointcut = "servicePointcut()", returning = "result")
    public void afterReturningAdvice(JoinPoint joinPoint, Object result) {
        logger.info("返回通知：方法 {} 返回结果 {}", joinPoint.getSignature().getName(), result);
    }

    // 异常通知：在方法抛出异常后执行
    @AfterThrowing(pointcut = "servicePointcut()", throwing = "ex")
    public void afterThrowingAdvice(JoinPoint joinPoint, Exception ex) {
        logger.error("异常通知：方法 {} 抛出异常 {}", joinPoint.getSignature().getName(), ex.getMessage());
    }
}