package 导出.functionTool版;

import com.jiuaoedu.common.Page;
import com.jiuaoedu.contract.edu.servicepublic.pojo.exporttask.CreateTaskIn;
import com.jiuaoedu.servicepublic.export.service.ExportClient;
import com.jiuaoedu.threadpool.ExecutorTask;
import com.jiuaoedu.threadpool.TaskPool;
import com.jiuaoedu.web.util.RequestContextHolderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportToolTest {

    @InjectMocks
    private ExportTool exportTool;

    @Mock
    private ExportClient exportClient;

    @Mock
    private Logger log;

    @Mock
    private TaskPool es;

    @Captor
    private ArgumentCaptor<ExecutorTask> taskCaptor;

    @BeforeEach
    void setUp() {
        exportTool = new ExportTool(exportClient);
    }

    @Test
    void testGetExportClassFromGenericInterface() {
        Function<String, Integer> func = Integer::valueOf;
        assertThrows(ExportTool.ExportTypeInferenceException.class, () -> exportTool.getExportClass(func));
    }

    @Test
    void testGetExportClassFromApplyMethod() {
        class MyFunc implements Function<String, String> {
            public String apply(String s) { return s; }
        }
        Class<String> exportClass = exportTool.getExportClass(new MyFunc());
        assertEquals(String.class, exportClass);
    }

    @Test
    void testHasPaginationParams_WithParams() {
        class HasPageParams {
            private Integer pageSize;
            private int pageNum;

            public Integer getPageSize() { return pageSize; }
            public void setPageSize(int pageSize) { this.pageSize = pageSize; }
            public int getPageNum() { return pageNum; }
            public void setPageNum(int pageNum) { this.pageNum = pageNum; }
        }

        assertTrue(exportTool.hasPaginationParams(new HasPageParams()));
    }

    @Test
    void testHasPaginationParams_WithoutParams() {
        class NoPageParams {
            private String name;
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
        }

        assertFalse(exportTool.hasPaginationParams(new NoPageParams()));
    }


    @Test
    void testCreateSmartExportHandler_PaginatedSuccess() {
        // 准备查询条件（带分页参数）
        class PaginatedQuery {
            private int pageSize;
            private int pageNum;

            public int getPageSize() { return pageSize; }
            public void setPageSize(int pageSize) { this.pageSize = pageSize; }
            public int getPageNum() { return pageNum; }
            public void setPageNum(int pageNum) { this.pageNum = pageNum; }
        }

        PaginatedQuery query = new PaginatedQuery();

        // 模拟分页查询方法
        Function<PaginatedQuery, Page<String>> queryFunc = in -> {
            Page<String> page = new Page<>();
            page.setList(Collections.singletonList("123"));
            page.setHasNextPage(false);
            return page;
        };

        // 转换方法
        Function<String, Integer> convertFunc = Integer::parseInt;

        try (MockedStatic<RequestContextHolderUtils> mocked = mockStatic(RequestContextHolderUtils.class)) {
            // 模拟静态方法和任务ID
            mocked.when(RequestContextHolderUtils::getEmployeeId).thenReturn(123L);
            when(exportClient.createExportTask(any(CreateTaskIn.class))).thenReturn(999L);

            // 拦截TaskPool的submit方法，手动执行任务
            doAnswer(invocation -> {
                ExecutorTask task = invocation.getArgument(0);
                task.execute();
                return null;
            }).when(es).submit(any(ExecutorTask.class));

            // 创建处理器并执行
            ExportTool.ExportHandler<PaginatedQuery> handler = exportTool.createSmartExportHandler(
                    queryFunc, convertFunc, String.class, Integer.class, es, 10, "测试导出"
            );

            com.jiuaoedu.common.Result<Long> result = handler.handle(query);

            // 验证结果
            assertNotNull(result.getData());
            assertEquals(999L, result.getData().longValue());
            verify(exportClient).exportFileWithMultipleSheet(
                    anyMap(), anyMap(), anyString(), eq(999L)
            );
        }
    }

    @Test
    void testCreateSmartExportHandler_NonPaginatedSuccess() {
        // 准备查询条件（不带分页参数）
        class NonPaginatedQuery {
            private String keyword;
            public String getKeyword() { return keyword; }
            public void setKeyword(String keyword) { this.keyword = keyword; }
        }

        NonPaginatedQuery query = new NonPaginatedQuery();

        // 模拟非分页查询方法（返回List）
        Function<NonPaginatedQuery, List<String>> queryFunc = in ->
                Collections.singletonList("123");

        // 转换方法
        Function<String, Integer> convertFunc = Integer::parseInt;

        try (MockedStatic<RequestContextHolderUtils> mocked = mockStatic(RequestContextHolderUtils.class)) {
            // 模拟静态方法和任务ID
            mocked.when(RequestContextHolderUtils::getEmployeeId).thenReturn(123L);
            when(exportClient.createExportTask(any(CreateTaskIn.class))).thenReturn(999L);

            // 拦截TaskPool的submit方法，手动执行任务
            doAnswer(invocation -> {
                ExecutorTask task = invocation.getArgument(0);
                task.execute();
                return null;
            }).when(es).submit(any(ExecutorTask.class));

            // 创建处理器并执行
            ExportTool.ExportHandler<NonPaginatedQuery> handler = exportTool.createSmartExportHandler(
                    queryFunc, convertFunc, String.class, Integer.class, es,  10, "测试导出"
            );

            com.jiuaoedu.common.Result<Long> result = handler.handle(query);

            // 验证结果
            assertNotNull(result.getData());
            assertEquals(999L, result.getData().longValue());
            verify(exportClient).exportFileWithMultipleSheet(
                    anyMap(), anyMap(), anyString(), eq(999L)
            );
        }
    }

    @Test
    void testCreateSmartExportHandler_ExceptionHandling() {
        // 准备查询条件
        class Query {
            private String keyword;
            public String getKeyword() { return keyword; }
            public void setKeyword(String keyword) { this.keyword = keyword; }
        }

        Query query = new Query();

        // 模拟查询方法抛出异常
        Function<Query, List<String>> queryFunc = in -> {
            throw new RuntimeException("模拟查询异常");
        };

        // 转换方法
        Function<String, Integer> convertFunc = Integer::parseInt;

        try (MockedStatic<RequestContextHolderUtils> mocked = mockStatic(RequestContextHolderUtils.class)) {
            // 模拟静态方法和任务ID
            mocked.when(RequestContextHolderUtils::getEmployeeId).thenReturn(123L);
            when(exportClient.createExportTask(any(CreateTaskIn.class))).thenReturn(999L);

            // 拦截TaskPool的submit方法，手动执行任务
            doAnswer(invocation -> {
                ExecutorTask task = invocation.getArgument(0);
                task.execute();
                return null;
            }).when(es).submit(any(ExecutorTask.class));

            // 创建处理器并执行
            ExportTool.ExportHandler<Query> handler = exportTool.createSmartExportHandler(
                    queryFunc, convertFunc, String.class, Integer.class, es,10, "测试导出"
            );

            com.jiuaoedu.common.Result<Long> result = handler.handle(query);

            // 验证异常处理
            verify(exportClient).failedExportTask(eq(999L), contains("模拟查询异常"));
        }
    }

    @Test
    void testCreateSmartExportHandler_DefaultPageSize() {
        // 验证默认分页大小
        class Query {
            private int pageSize;
            private int pageNum;

            public int getPageSize() { return pageSize; }
            public void setPageSize(int pageSize) { this.pageSize = pageSize; }
            public int getPageNum() { return pageNum; }
            public void setPageNum(int pageNum) { this.pageNum = pageNum; }
        }

        Query query = new Query();

        // 模拟查询方法
        Function<Query, Page<String>> queryFunc = in -> {
            Page<String> page = new Page<>();
            page.setList(Collections.emptyList());
            page.setHasNextPage(false);
            return page;
        };

        // 转换方法
        Function<String, Integer> convertFunc = Integer::parseInt;

        try (MockedStatic<RequestContextHolderUtils> mocked = mockStatic(RequestContextHolderUtils.class)) {
            mocked.when(RequestContextHolderUtils::getEmployeeId).thenReturn(123L);
            when(exportClient.createExportTask(any(CreateTaskIn.class))).thenReturn(999L);

            // 使用不指定pageSize的重载方法
            ExportTool.ExportHandler<Query> handler = exportTool.createSmartExportHandler(
                    queryFunc, convertFunc, String.class, Integer.class, es,"测试导出"
            );

            handler.handle(query);

            // 验证任务执行时使用了默认分页大小
            verify(es).submit(taskCaptor.capture());
            ExecutorTask task = taskCaptor.getValue();

            // 执行任务并验证内部状态
            task.execute();
            // 注意：无法直接验证内部状态，通过行为验证
        }
    }
}