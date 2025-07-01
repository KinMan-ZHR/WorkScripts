package 导出.高级版;

import com.alibaba.fastjson.JSON;
import com.jiuaoedu.common.Page;
import com.jiuaoedu.contract.edu.servicepublic.pojo.PageIn;
import com.jiuaoedu.contract.edu.servicepublic.pojo.exporttask.CreateTaskIn;
import com.jiuaoedu.servicepublic.export.service.ExportClient;
import com.jiuaoedu.threadpool.ExecutorTask;
import com.jiuaoedu.threadpool.TaskPool;
import com.jiuaoedu.threadpool.TaskPoolFactory;
import com.jiuaoedu.web.util.RequestContextHolderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 导出工具类：支持通过方法引用动态创建导出功能
 * <p>
 * **使用方式说明**：
 * 1. 在Spring容器中注入`ExportTool`实例
 * 2. 定义查询方法（返回`Page<R>`）和转换方法（`R`转`E`）
 * 3. 通过`createExportHandler`创建导出处理器
 * 4. 在控制器中映射接口路径并调用处理器
 * <p>
 * **示例代码**：
 * ```java
 * * @RestController
 * * public class StudentExportController {
 * *     @Resource
 * *     private ExportTool exportTool;
 * *     @Resource
 * *     private StudentService studentService;
 * <p>
 * *     // 1. 定义查询方法（需返回Page<R>）
 * *     public Page<Student> queryStudents(StudentQueryIn in) {
 * *         return studentService.queryByPage(in);
 * *     }
 * <p>
 * *     // 2. 定义转换方法（R转E，E为导出类型）
 * *     public StudentExportVo convertToExportVo(Student student) {
 * *         // 字段映射逻辑
 * *         return new StudentExportVo();
 * *     }
 * <p>
 * *     // 3. 创建导出处理器
 * *     private final ExportTool.ExportHandler<StudentQueryIn> exportHandler =
 * *         exportTool.createExportHandler(
 * *             this::queryStudents,    // 查询方法引用
 * *             this::convertToExportVo, // 转换方法引用
 * *             "学生数据导出"           // 导出文件名
 * *         );
 * <p>
 * *     // 4. 控制器接口
 * *     @PostMapping("/api/students/export")
 * *     public Result<Long> export(@RequestBody StudentQueryIn in) {
 * *         return exportHandler.handle(in);
 * *     }
 * * }
 * ```
 *
 * @author ZhangHaoRan
 * @since 2025/7/1
 */
public class ExportTool {
    private final TaskPool es = TaskPoolFactory.getTaskPool(
            1, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(300),
            new ThreadPoolExecutor.AbortPolicy()
    );
    private final Logger log = LoggerFactory.getLogger(ExportTool.class);

    @Resource
    private ExportClient exportClient;

    /**
     * 创建导出处理器（自动推导导出类型）
     *
     * @param <I>            查询条件类型
     * @param <R>            原始查询结果类型
     * @param queryMethod    查询方法
     * @param convertMethod  转换方法（隐含导出类型）
     * @param exportFileName 导出文件名
     * @return 导出接口处理函数
     */
    public <I extends PageIn, R, E> ExportHandler<I> createExportHandler(
            ExportQueryFunction<I, R> queryMethod,
            Function<R, E> convertMethod,
            String exportFileName) {
        // 通过转换方法反射获取导出类型
        Class<E> exportClass = getExportClass(convertMethod);

        return in -> {
            CreateTaskIn createTaskIn = new CreateTaskIn();
            createTaskIn.setOperatorId(RequestContextHolderUtils.getEmployeeId());
            createTaskIn.setFileNme(exportFileName);
            in.setPageSize(Integer.MAX_VALUE);
            in.setPageNum(1);
            createTaskIn.setParams(JSON.toJSONString(in));
            Long taskId = exportClient.createExportTask(createTaskIn);

            es.submit(new ExecutorTask() {
                @Override
                public void execute() {
                    try {
                        List<E> exportData = fetchAndConvertData(in, queryMethod, convertMethod);
                        exportSingleSheet(taskId, exportData, exportClass, exportFileName);
                    } catch (Exception e) {
                        log.error(exportFileName + "导出失败", e);
                        exportClient.failedExportTask(taskId, e.getMessage());
                    }
                }

                private void exportSingleSheet(Long taskId, List<?> dataList, Class<?> modelClass, String fileName) {
                    Map<String, List<?>> datas = new HashMap<>(4);
                    datas.put("sheet1", dataList);
                    Map<String, Class<?>> models = new HashMap<>(4);
                    models.put("sheet1", modelClass);
                    exportClient.exportFileWithMultipleSheet(datas, models, fileName + "_" + UUID.randomUUID().toString().substring(0, 5) + ".xlsx", taskId);
                }

            });

            return com.jiuaoedu.common.Result.success(taskId);
        };
    }

    public <I, R, E> ExportHandler<I> createExportHandlerNoPageProcess(
            ExportQueryFunctionNoPageProcess<I, R> queryMethod,
            Function<R, E> convertMethod,
            String exportFileName) {
        // 通过转换方法反射获取导出类型
        Class<E> exportClass = getExportClass(convertMethod);

        return in -> {
            CreateTaskIn createTaskIn = new CreateTaskIn();
            createTaskIn.setOperatorId(RequestContextHolderUtils.getEmployeeId());
            createTaskIn.setFileNme(exportFileName);
            createTaskIn.setParams(JSON.toJSONString(in));
            Long taskId = exportClient.createExportTask(createTaskIn);

            es.submit(new ExecutorTask() {
                @Override
                public void execute() {
                    try {
                        List<E> exportData = fetchAndConvertDataNoPageProcess(in, queryMethod, convertMethod);
                        exportSingleSheet(taskId, exportData, exportClass, exportFileName);
                    } catch (Exception e) {
                        log.error(exportFileName + "导出失败", e);
                        exportClient.failedExportTask(taskId, e.getMessage());
                    }
                }

                private void exportSingleSheet(Long taskId, List<?> dataList, Class<?> modelClass, String fileName) {
                    Map<String, List<?>> datas = new HashMap<>(4);
                    datas.put("sheet1", dataList);
                    Map<String, Class<?>> models = new HashMap<>(4);
                    models.put("sheet1", modelClass);
                    exportClient.exportFileWithMultipleSheet(datas, models, fileName + "_" + UUID.randomUUID().toString().substring(0, 5) + ".xlsx", taskId);
                }

            });

            return com.jiuaoedu.common.Result.success(taskId);
        };
    }

    /**
     * 通过转换方法反射获取导出类型
     */
    @SuppressWarnings("unchecked")
    private <R, E> Class<E> getExportClass(Function<R, E> convertMethod) {
        try {
            // 获取函数式接口的实现类
            Class<?> functionClass = convertMethod.getClass();

            // 获取接口类型
            Type[] interfaces = functionClass.getGenericInterfaces();
            for (Type type : interfaces) {
                if (type instanceof ParameterizedType) {
                    ParameterizedType paramType = (ParameterizedType) type;
                    // 检查是否为Function接口
                    if (paramType.getRawType() == Function.class) {
                        // 获取第二个泛型参数（即返回类型）
                        Type returnType = paramType.getActualTypeArguments()[1];
                        if (returnType instanceof Class) {
                            return (Class<E>) returnType;
                        }
                    }
                }
            }

            // 备用方案：通过apply方法获取返回类型
            Method applyMethod = functionClass.getMethod("apply", Object.class);
            return (Class<E>) applyMethod.getReturnType();
        } catch (Exception e) {
            log.error("获取导出类型失败，转换方法: {}",
                    convertMethod.getClass().getName(), e);
            throw new RuntimeException("导出类型推导失败，请检查转换方法的返回类型", e);
        }
    }

    /**
     * 分页查询并转换数据
     */
    private <I extends PageIn, R, E> List<E> fetchAndConvertData(
            I in,
            ExportQueryFunction<I, R> queryMethod,
            Function<R, E> convertMethod) {

        int pageSize = 20000;
        int pageNum = 1;
        boolean hasNextPage = true;
        List<E> exportData = new ArrayList<>();

        while (hasNextPage) {
            in.setPageSize(pageSize);
            in.setPageNum(pageNum);
            Page<R> pageResult = queryMethod.apply(in);
            List<R> results = pageResult.getList();

            for (R result : results) {
                exportData.add(convertMethod.apply(result));
            }

            hasNextPage = pageResult.isHasNextPage();
            pageNum++;
        }

        return exportData;
    }

    /**
     * 分页查询并转换数据
     */
    private <I, R, E> List<E> fetchAndConvertDataNoPageProcess(I in, ExportQueryFunctionNoPageProcess<I, R> queryMethod, Function<R, E> convertMethod) {
        List<E> exportData = new ArrayList<>();

        List<R> results = queryMethod.apply(in);

        for (R result : results) {
            exportData.add(convertMethod.apply(result));
        }
        return exportData;
    }

    /**
     * 导出接口处理函数：接收查询条件，返回导出任务ID
     */
    @FunctionalInterface
    public interface ExportHandler<I> {
        /**
         * 处理导出请求
         *
         * @param in 查询条件（需实现PageIn接口）
         * @return 导出任务ID
         */
        com.jiuaoedu.common.Result<Long> handle(@RequestBody @Validated I in);
    }

    /**
     * 导出查询函数：接收查询条件和分页参数，返回分页结果
     *
     * @param <I> 查询条件类型，需实现PageIn接口
     * @param <R> 原始查询结果类型
     */
    @FunctionalInterface
    public interface ExportQueryFunction<I extends PageIn, R> {
        /**
         * 执行分页查询
         *
         * @param in 查询条件
         * @return 分页查询结果
         */
        Page<R> apply(I in);
    }

    /**
     * 导出查询函数：接收查询条件和分页参数，返回分页结果
     *
     * @param <I> 查询条件类型，无需实现PageIn接口
     * @param <R> 原始查询结果类型
     */
    @FunctionalInterface
    public interface ExportQueryFunctionNoPageProcess<I, R> {
        /**
         * 执行列表查询
         *
         * @param in 查询条件
         * @return 列表查询结果
         */
        List<R> apply(I in);
    }
}