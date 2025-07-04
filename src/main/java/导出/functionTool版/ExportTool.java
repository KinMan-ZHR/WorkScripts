package 导出.functionTool版;

import com.alibaba.fastjson.JSON;
import com.jiuaoedu.common.Page;
import com.jiuaoedu.contract.edu.servicepublic.pojo.exporttask.CreateTaskIn;
import com.jiuaoedu.servicepublic.export.service.ExportClient;
import com.jiuaoedu.threadpool.ExecutorTask;
import com.jiuaoedu.threadpool.TaskPool;
import com.jiuaoedu.web.util.RequestContextHolderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 智能导出工具类：自动识别分页需求，支持灵活配置
 * <p>
 * **使用方式说明**：
 * 1. 在Spring容器中注入`ExportTool`实例
 * 2. 定义查询方法（返回`Page<R>`或`List<R>`）和转换方法（`R`转`E`）
 * 3. 通过`createSmartExportHandler`创建导出处理器（支持多种重载形式）
 * 4. 在控制器中映射接口路径并调用处理器
 * <p>
 * <strong>示例代码</strong>：
 * <pre>{@code
 * @RestController
 * public class StudentExportController {
 *    @Resource
 *    private ExportTool exportTool;
 *    @Resource
 *    private StudentService studentService;
 *
 *    private final TaskPool es = TaskPoolFactory.getTaskPool(1, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(300), new ThreadPoolExecutor.AbortPolicy());
 *
 *
 *    // 分页查询方法（返回Page<R>）
 *    public Page<Student> queryStudents(StudentQuery in) {
 *        return studentService.queryByPage(in);
 *    }
 *
 *    // 或不支持分页的查询方法（返回List<R>）
 *    public List<Student> queryAllStudents(StudentQuerySimple in) {
 *        return studentService.queryAll(in);
 *    }
 *
 *    // 转换方法
 *    public StudentExportVo convertToExportVo(Student student) {
 *        return new StudentExportVo();
 *    }
 *
 *    // 创建智能导出处理器（自动判断是否分页）
 *    private final ExportTool.ExportHandler<StudentQuery> exportHandler =
 *        exportTool.createSmartExportHandler(
 *            this::queryStudents,    // 分页或非分页查询方法均可
 *            this::convertToExportVo,
 *            es,
 *            "学生数据导出"
 *        );
 *
 *    @PostMapping("/api/students/export")
 *    public Result<Long> export(@RequestBody StudentQuery in) {
 *        return exportHandler.handle(in);
 *    }
 * }
 * }</pre>
 *
 * @author ZhangHaoRan
 * @since 2025/7/1
 */
@Component
public class ExportTool {

    private static final int DEFAULT_PAGE_SIZE = 10000;
    private final ExportClient exportClient;
    private final Logger log = LoggerFactory.getLogger(ExportTool.class);
    // 反射缓存
    private final Map<Class<?>, Method> pageSizeMethodCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Method> pageNumMethodCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Field> pageSizeFieldCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Field> pageNumFieldCache = new ConcurrentHashMap<>();

    @Autowired
    public ExportTool(ExportClient exportClient) {
        this.exportClient = exportClient;
    }

    /**
     * 创建智能导出处理器（自动推导导出类型，使用默认分页大小）
     */
    public <I, R, E> ExportHandler<I> createSmartExportHandler(
            Function<I, ?> queryMethod,
            Function<R, E> convertMethod,
            TaskPool es,
            String exportFileName) {

        return createSmartExportHandler(queryMethod, convertMethod, es, DEFAULT_PAGE_SIZE, exportFileName);
    }

    /**
     * 创建智能导出处理器（自动推导导出类型，自定义分页大小）
     */
    public <I, R, E> ExportHandler<I> createSmartExportHandler(
            Function<I, ?> queryMethod,
            Function<R, E> convertMethod,
            TaskPool es,
            int pageSize,
            String exportFileName) {

        Class<E> exportClass = getExportClass(convertMethod);
        return createSmartExportHandler(queryMethod, convertMethod, exportClass, es, pageSize, exportFileName);
    }

    /**
     * 创建智能导出处理器（显式指定导出类型，使用默认分页大小）
     */
    public <I, R, E> ExportHandler<I> createSmartExportHandler(
            Function<I, ?> queryMethod,
            Function<R, E> convertMethod,
            Class<E> exportClass,
            TaskPool es,
            String exportFileName) {

        return createSmartExportHandler(queryMethod, convertMethod, exportClass, es, DEFAULT_PAGE_SIZE, exportFileName);
    }

    /**
     * 创建智能导出处理器（显式指定导出类型和分页大小）
     * 这是最完整的配置方法，其他重载最终都会调用此方法
     */
    public <I, R, E> ExportHandler<I> createSmartExportHandler(
            Function<I, ?> queryMethod,
            Function<R, E> convertMethod,
            Class<E> exportClass,
            TaskPool es,
            int pageSize,
            String exportFileName) {

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
                        List<E> exportData = fetchDataIntelligently(
                                in, queryMethod, convertMethod, pageSize);

                        exportSingleSheet(taskId, exportData, exportClass, exportFileName);
                    } catch (Exception e) {
                        log.error(exportFileName + "导出失败", e);
                        exportClient.failedExportTask(taskId, e.getMessage());
                    }
                }
            });

            return com.jiuaoedu.common.Result.success(taskId);
        };
    }

    /**
     * 智能获取数据：自动判断是否分页并执行相应查询
     */
    @SuppressWarnings("unchecked")
    <I, R, E> List<E> fetchDataIntelligently(
            I in, Function<I, ?> queryMethod, Function<R, E> convertMethod, int pageSize) {

        List<E> exportData = new ArrayList<>();

        // 检查是否支持分页
        if (hasPaginationParams(in)) {
            // 分页模式
            int pageNum = 1;
            boolean hasNextPage = true;

            while (hasNextPage) {
                try {
                    // 设置分页参数
                    setPageParamsIfExists(in, pageSize, pageNum);

                    // 执行查询
                    Object result = queryMethod.apply(in);

                    // 处理查询结果
                    if (result instanceof Page) {
                        // 分页结果处理
                        Page<R> pageResult = (Page<R>) result;
                        List<R> results = pageResult.getList();

                        for (R item : results) {
                            exportData.add(convertMethod.apply(item));
                        }

                        hasNextPage = pageResult.isHasNextPage();
                        pageNum++;
                    } else if (result instanceof List) {
                        // 非分页结果（直接返回列表）
                        List<R> results = (List<R>) result;
                        for (R item : results) {
                            exportData.add(convertMethod.apply(item));
                        }
                        hasNextPage = false; // 列表结果视为单页数据
                    } else {
                        throw new IllegalArgumentException("查询方法返回类型不支持: " +
                                (result != null ? result.getClass().getName() : "null"));
                    }
                } catch (Exception e) {
                    throw new RuntimeException("分页查询失败: " + e.getMessage(), e);
                }
            }
        } else {
            // 非分页模式
            try {
                Object result = queryMethod.apply(in);

                if (result instanceof List) {
                    List<R> results = (List<R>) result;
                    for (R item : results) {
                        exportData.add(convertMethod.apply(item));
                    }
                } else if (result instanceof Page) {
                    // 处理用户传入分页方法但未提供分页参数的情况
                    Page<R> pageResult = (Page<R>) result;
                    List<R> results = pageResult.getList();
                    for (R item : results) {
                        exportData.add(convertMethod.apply(item));
                    }
                } else {
                    throw new IllegalArgumentException("查询方法返回类型不支持: " +
                            (result != null ? result.getClass().getName() : "null"));
                }
            } catch (Exception e) {
                throw new RuntimeException("非分页查询失败: " + e.getMessage(), e);
            }
        }

        return exportData;
    }

    /**
     * 检查对象是否包含分页参数
     */
    boolean hasPaginationParams(Object obj) {
        if (obj == null) return false;

        Class<?> clazz = obj.getClass();
        try {
            // 检查是否有pageSize和pageNum字段或setter方法
            boolean hasPageSize = findField(clazz, "pageSize") != null ||
                    findMethod(clazz, "setPageSize", int.class) != null;

            boolean hasPageNum = findField(clazz, "pageNum") != null ||
                    findMethod(clazz, "setPageNum", int.class) != null;

            return hasPageSize && hasPageNum;
        } catch (Exception e) {
            log.warn("检测分页参数失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 安全设置分页参数（如果存在）
     */
    private void setPageParamsIfExists(Object obj, int pageSize, int pageNum) {
        if (obj == null) return;

        try {
            Class<?> clazz = obj.getClass();

            // 设置pageSize
            Method setPageSizeMethod = findMethod(clazz, "setPageSize", int.class);
            if (setPageSizeMethod != null) {
                setPageSizeMethod.invoke(obj, pageSize);
            } else {
                Field pageSizeField = findField(clazz, "pageSize");
                if (pageSizeField != null) {
                    pageSizeField.setAccessible(true);
                    pageSizeField.set(obj, pageSize);
                }
            }

            // 设置pageNum
            Method setPageNumMethod = findMethod(clazz, "setPageNum", int.class);
            if (setPageNumMethod != null) {
                setPageNumMethod.invoke(obj, pageNum);
            } else {
                Field pageNumField = findField(clazz, "pageNum");
                if (pageNumField != null) {
                    pageNumField.setAccessible(true);
                    pageNumField.set(obj, pageNum);
                }
            }
        } catch (Exception e) {
            log.warn("设置分页参数失败: {}", e.getMessage());
        }
    }

    /**
     * 查找方法（包括父类），带缓存
     */
    private Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        Map<Class<?>, Method> cache = "setPageSize".equals(methodName) ? pageSizeMethodCache : pageNumMethodCache;

        return cache.computeIfAbsent(clazz, k -> {
            try {
                return clazz.getMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                Class<?> superClass = clazz.getSuperclass();
                if (superClass != null && superClass != Object.class) {
                    return findMethod(superClass, methodName, parameterTypes);
                }
                return null;
            }
        });
    }

    /**
     * 查找字段（包括父类），带缓存
     */
    private Field findField(Class<?> clazz, String fieldName) {
        Map<Class<?>, Field> cache = "pageSize".equals(fieldName) ? pageSizeFieldCache : pageNumFieldCache;

        return cache.computeIfAbsent(clazz, k -> {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                Class<?> superClass = clazz.getSuperclass();
                if (superClass != null && superClass != Object.class) {
                    return findField(superClass, fieldName);
                }
                return null;
            }
        });
    }

    /**
     * 通过转换方法反射获取导出类型
     */
    @SuppressWarnings("unchecked")
    <R, E> Class<E> getExportClass(Function<R, E> convertMethod) {
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

            // 无法自动推导类型，抛出异常
            String inferenceErrorMsg = "无法自动推导导出类型。转换方法：" + convertMethod.getClass().getName() + " 因Lambda/方法引用的泛型擦除。请使用重载方法并显式指定导出类型：createSmartExportHandler(queryMethod, convertMethod, ExportType.class, exportFileName)";
            throw new ExportTypeInferenceException(inferenceErrorMsg);
        } catch (SecurityException e) {
            String contextMsg = "反射获取导出类型时无权限（转换方法：" + convertMethod.getClass().getName() + "）";
            log.error(contextMsg, e);
            throw new SecurityException(contextMsg, e);
        } catch (NoClassDefFoundError e) {
            String contextMsg = "转换方法依赖的类未找到（转换方法：" + convertMethod.getClass().getName() + "）";
            log.error(contextMsg, e);
            throw new NoClassDefFoundError(contextMsg);
        }
    }

    /**
     * 导出单页数据
     */
    private void exportSingleSheet(Long taskId, List<?> dataList, Class<?> modelClass, String fileName) {
        Map<String, List<?>> datas = new HashMap<>(4);
        datas.put("sheet1", dataList);
        Map<String, Class<?>> models = new HashMap<>(4);
        models.put("sheet1", modelClass);
        exportClient.exportFileWithMultipleSheet(datas, models, fileName + "_" + UUID.randomUUID().toString().substring(0, 5) + ".xlsx", taskId);
    }

    /**
     * 导出接口处理函数：接收查询条件，返回导出任务ID
     */
    @FunctionalInterface
    public interface ExportHandler<I> {
        /**
         * 处理导出请求
         *
         * @param in 查询条件
         * @return 导出任务ID
         */
        com.jiuaoedu.common.Result<Long> handle(@RequestBody @Validated I in);
    }

    /**
     * 自定义异常类，用于类型推导失败的场景
     */
    public static class ExportTypeInferenceException extends RuntimeException {
        public ExportTypeInferenceException(String message) {
            super(message);
        }
    }
}