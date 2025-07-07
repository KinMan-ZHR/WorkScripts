package 导出.functionTool版;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.ISelect;
import com.github.pagehelper.page.PageMethod;
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
import java.util.stream.Collectors;

/**
 * 智能导出工具类：自动识别分页需求（可部分解决PG瓶颈,依赖开发者指定一个合理的pageSize），支持灵活配置
 * 支持分批处理导出。
 * <p>
 * <strong>使用方式说明<strong>
 * <P>
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
        Class<R> queryResultClass = getQueryResultClass(convertMethod);
        Class<E> exportClass = getExportClass(convertMethod);
        return createSmartExportHandler(queryMethod, convertMethod, queryResultClass, exportClass, es, pageSize, exportFileName);
    }

    /**
     * 创建智能导出处理器（显式指定导出类型，使用默认分页大小）
     */
    public <I, R, E> ExportHandler<I> createSmartExportHandler(
            Function<I, ?> queryMethod,
            Function<R, E> convertMethod,
            Class<R> queryResultClass,
            Class<E> exportClass,
            TaskPool es,
            String exportFileName) {

        return createSmartExportHandler(queryMethod, convertMethod, queryResultClass, exportClass, es, DEFAULT_PAGE_SIZE, exportFileName);
    }

    /**
     * 创建智能导出处理器（显式指定导出类型和分页大小）
     * 这是最完整的配置方法，其他重载最终都会调用此方法
     */
    public <I, R, E> ExportHandler<I> createSmartExportHandler(
            Function<I, ?> queryMethod,
            Function<R, E> convertMethod,
            Class<R> queryResultClass,
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
                        // 获取智能查询结果（包含分页标志）
                        FetchResult<E> fetchResult = fetchDataIntelligently(
                                in, queryMethod, convertMethod, pageSize);

                        if (fetchResult.isNeedPagination()) {
                            // 大数据量：使用分页导出（调用exportBigDataFileBySQL）
                            exportByPagination(taskId, in, queryMethod, convertMethod, queryResultClass, exportClass, pageSize, exportFileName);
                        } else {
                            // 小数据量：使用原有单sheet导出
                            exportSingleSheet(taskId, fetchResult.getData(), exportClass, exportFileName);
                        }
                    } catch (Exception e) {
                        log.error(exportFileName + "导出失败", e);
                        exportClient.failedExportTask(taskId, e.getMessage());
                    }
                }
            });

            return com.jiuaoedu.common.Result.success(taskId);
        };
    }

    public static class FetchResult<E> {
        private final long total;
        private final boolean needPagination;
        private final List<E> data;

        public FetchResult(long total, boolean needPagination, List<E> data) {
            this.total = total;
            this.needPagination = needPagination;
            this.data = data;
        }

        // getters
        public long getTotal() { return total; }
        public boolean isNeedPagination() { return needPagination; }
        public List<E> getData() { return data; }
    }

    /**
     * 智能获取数据：自动判断是否分页并执行相应查询
     */
    @SuppressWarnings("unchecked")
    <I, R, E> FetchResult<E> fetchDataIntelligently(
            I in, Function<I, ?> queryMethod, Function<R, E> convertMethod, int pageSize) {

        int pageNum = 1;
        // 设置分页参数
        setPageParamsIfExists(in, pageSize, pageNum);

        // 执行查询
        Object result = queryMethod.apply(in);

        List<R> results;

        // 处理查询结果
        if (result instanceof Page) {
            // 分页结果处理
            Page<R> pageResult = (Page<R>) result;
            results = pageResult.getList();
        } else if (result instanceof List) {
            // 非分页结果（直接返回列表）
            results = (List<R>) result;
        } else {
            throw new IllegalArgumentException("查询方法返回类型不支持: " +
                    (result != null ? result.getClass().getName() : "null"));
        }
        log.info("智能查询：首页条数={}, 分页阈值={}",
                results.size(), pageSize);
        if(results.size() < pageSize) {
            // 如果结果集小于分页大小，则不需要进一步查询
            List<E> convertedData = results.stream()
                    .map(convertMethod)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            return new FetchResult<>(results.size(), false, convertedData);
        } else {
            return new FetchResult<>(results.size(), true, null);
        }
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
        } catch (NoClassDefFoundError e) {
            String contextMsg = "转换方法依赖的类未找到（转换方法：" + convertMethod.getClass().getName() + "）";
            log.error(contextMsg, e);
            throw new NoClassDefFoundError(contextMsg);
        }
    }

    // 新增：获取转换方法的输入类型（R的类型）
    private <R, E> Class<R> getQueryResultClass(Function<R, E> convertMethod) {
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
                        // 获取第一个泛型参数（即返回类型）
                        Type returnType = paramType.getActualTypeArguments()[0];
                        if (returnType instanceof Class) {
                            return (Class<R>) returnType;
                        }
                    }
                }
            }

            // 无法自动推导类型，抛出异常
            String inferenceErrorMsg = "无法自动推导导出类型。转换方法：" + convertMethod.getClass().getName() + " 因Lambda/方法引用的泛型擦除。请使用重载方法并显式指定导出类型：createSmartExportHandler(queryMethod, convertMethod, ExportType.class, exportFileName)";
            throw new ExportTypeInferenceException(inferenceErrorMsg);
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
     * 分页导出实现（调用exportBigDataFileBySQL）
     */
    private <I, R, E> void exportByPagination(
            Long taskId,
            I in,
            Function<I,?> queryMethod,
            Function<R, E> convertMethod,
            Class<R> queryResultClass,
            Class<E> exportClass,
            int pageSize,
            String exportFileName) {

        // 创建ISelect实现分页查询
        ISelect select = () -> {
            com.github.pagehelper.Page<R> page = PageMethod.getLocalPage();
            if (page == null) {
                page = new com.github.pagehelper.Page<>(1, pageSize);
            }
            // 设置分页参数
            setPageParamsIfExists(in, page.getPageSize(), page.getPageNum());

            // 执行分页查询
            Object resultPage = queryMethod.apply(in);
            if (resultPage instanceof Page){
                Page<R> pageResult = (Page<R>) resultPage;
                page.clear();
                page.addAll(pageResult.getList());
                page.setTotal(pageResult.getTotal());
            }

        };

        // 创建导出对象实例（用于反射）
        R queryResultInstance = getInstance(queryResultClass);
        E exportInstance = getInstance(exportClass);

        log.info("开始分页导出：任务ID={}, 文件名={}, 分页大小={}",
                taskId, exportFileName, pageSize);

        // 调用ExportClient的分页导出方法
        exportClient.exportBigDataFileBySQL(
                select,
                convertMethod,
                queryResultInstance,
                exportInstance,
                exportFileName,
                taskId
        );
    }

    private <T> T getInstance(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("无法实例化类型: " + clazz.getName(), e);
        }
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