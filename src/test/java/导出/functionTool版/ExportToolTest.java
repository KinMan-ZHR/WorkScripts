package 导出.functionTool版;

import com.jiuaoedu.common.Page;
import com.jiuaoedu.contract.edu.servicepublic.pojo.PageIn;
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
        exportTool = new ExportTool( exportClient, es,log);
    }


    @Test
    void testGetExportClassFromGenericInterface() {
        Function<String, Integer> func = Integer::valueOf;
        assertThrows(ExportTool.ExportTypeInferenceException.class, () -> exportTool.getExportClass(func));
    }

    @Test
    void testGetExportClassFromApplyMethod() throws Exception {
        class MyFunc implements Function<String, String> {
            public String apply(String s) { return s; }
        }
        Class<String> exportClass = exportTool.getExportClass(new MyFunc());
        assertEquals(String.class, exportClass);
    }

    @Test
    void testFetchAndConvertData_MultiPage() {
        ExportTool.ExportQueryFunction<MockPageIn, String> queryFunc = in -> {
            Page<String> page = new Page<>();
            if (in.getPageNum() == 1) {
                page.setList(Arrays.asList("A", "B"));
                page.setHasNextPage(true);
            } else {
                page.setList(Arrays.asList("C"));
                page.setHasNextPage(false);
            }
            return page;
        };
        Function<String, Integer> convertFunc = s -> {
            switch (s) {
                case "A": return 1;
                case "B": return 2;
                case "C": return 3;
                default: throw new IllegalArgumentException("Unknown value: " + s);
            }
        };

        MockPageIn in = new MockPageIn();
        List<Integer> result = exportTool.fetchAndConvertData(in, queryFunc, convertFunc);

        assertEquals(Arrays.asList(1, 2, 3), result);
    }

    @Test
    void testFetchAndConvertData_NoData() {
        ExportTool.ExportQueryFunction<MockPageIn, String> queryFunc = in -> {
            Page<String> page = new Page<>();
            page.setList(Collections.emptyList());
            page.setHasNextPage(false);
            return page;
        };
        Function<String, Integer> convertFunc = Integer::parseInt;

        MockPageIn in = new MockPageIn();
        List<Integer> result = exportTool.fetchAndConvertData(in, queryFunc, convertFunc);

        assertTrue(result.isEmpty());
    }

    @Test
    void testCreateExportHandler_Success() {
        // 1. 模拟查询方法（返回可转换的数据）
        ExportTool.ExportQueryFunction<MockPageIn, String> queryFunc = in -> {
            Page<String> page = new Page<>();
            page.setList(Collections.singletonList("123")); // 确保可转换为Integer
            page.setHasNextPage(false);
            return page;
        };

        // 2. 模拟转换方法（无异常）
        Function<String, Integer> convertFunc = s -> {
            try {
                return Integer.parseInt(s);
            } catch (Exception e) {
                fail("转换方法不应抛出异常");
                return null;
            }
        };

        try (MockedStatic<RequestContextHolderUtils> mocked = mockStatic(RequestContextHolderUtils.class)) {
            // 3. 模拟静态方法和任务ID
            mocked.when(RequestContextHolderUtils::getEmployeeId).thenReturn(123L);
            when(exportClient.createExportTask(any(CreateTaskIn.class))).thenReturn(999L);

            // 4. 拦截TaskPool的submit方法，手动执行任务
            doAnswer(invocation -> {
                ExecutorTask task = invocation.getArgument(0); // 获取提交的任务
                task.execute(); // 立即执行任务（关键：触发exportFileWithMultipleSheet）
                return null;
            }).when(es).submit(any(ExecutorTask.class));

            // 5. 创建处理器并执行
            ExportTool.ExportHandler<MockPageIn> handler = exportTool.createExportHandler(
                   queryFunc, convertFunc, Integer.class, "测试导出"
            );
            MockPageIn in = new MockPageIn();
            com.jiuaoedu.common.Result<Long> result = handler.handle(in);

            // 6. 验证结果
            assertNotNull(result.getData());
            assertEquals(999L, result.getData().longValue());
            verify(exportClient).createExportTask(any(CreateTaskIn.class));
            verify(exportClient, never()).failedExportTask(anyLong(), anyString());
            // 验证导出方法被调用
            verify(exportClient).exportFileWithMultipleSheet(
                    anyMap(), anyMap(), anyString(), eq(999L)
            );
        }
    }

    @Test
    void testCreateExportHandler_ExceptionHandling() {
        // 模拟查询方法抛出异常
        ExportTool.ExportQueryFunction<MockPageIn, String> queryFunc = in -> {
            throw new RuntimeException("模拟查询异常");
        };
        Function<String, Integer> convertFunc = Integer::parseInt;

        try (MockedStatic<RequestContextHolderUtils> mocked = mockStatic(RequestContextHolderUtils.class)) {
            mocked.when(RequestContextHolderUtils::getEmployeeId).thenReturn(123L);
            when(exportClient.createExportTask(any(CreateTaskIn.class))).thenReturn(999L);

            // 拦截submit并执行任务（触发异常处理）
            doAnswer(invocation -> {
                ExecutorTask task = invocation.getArgument(0);
                task.execute();
                return null;
            }).when(es).submit(any(ExecutorTask.class));

            // 执行测试
            ExportTool.ExportHandler<MockPageIn> handler = exportTool.createExportHandler(
                   10000, queryFunc, convertFunc, Integer.class, "测试导出"
            );
            MockPageIn in = new MockPageIn();
            com.jiuaoedu.common.Result<Long> result = handler.handle(in);

            // 验证异常处理
            verify(exportClient).failedExportTask(eq(999L), contains("模拟查询异常"));
        }

    }

    // Helper class for testing
    static class MockPageIn extends PageIn {
        private int pageNum = 1;
        private int pageSize = 10;

        public int getPageNum() { return pageNum; }
        public void setPageNum(int pageNum) { this.pageNum = pageNum; }
        public int getPageSize() { return pageSize; }
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    }
}
