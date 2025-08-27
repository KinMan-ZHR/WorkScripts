package test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ReflectionBenchmark {
    
    private DirectCall direct;
    private Method reflectedMethod;
    private MethodHandle methodHandle;
    
    @Setup
    public void setup() throws Exception {
        direct = new DirectCall();
        reflectedMethod = DirectCall.class.getMethod("execute");
        reflectedMethod.setAccessible(true);
        methodHandle = MethodHandles.lookup()
            .findVirtual(DirectCall.class, "execute", 
                MethodType.methodType(void.class));
    }
    
    @Benchmark
    public void directCall() {
        direct.execute();
    }
    
    @Benchmark
    public void reflectedCall() throws Exception {
        reflectedMethod.invoke(direct);
    }
    
    @Benchmark
    public void methodHandleCall() throws Throwable {
        methodHandle.invokeExact(direct);
    }
    
    public static class DirectCall {
        public void execute() {
            // 模拟一个简单操作
            Math.log(1234.5678);
        }
    }
}
