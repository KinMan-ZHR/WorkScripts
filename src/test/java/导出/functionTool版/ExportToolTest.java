package 导出.functionTool版;

import com.jiuaoedu.common.Page;
import com.jiuaoedu.contract.edu.servicepublic.pojo.PageIn;
import com.jiuaoedu.contract.edu.servicepublic.pojo.exporttask.CreateTaskIn;
import com.jiuaoedu.servicepublic.export.service.ExportClient;
import com.jiuaoedu.threadpool.TaskPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExportToolTest {

    @InjectMocks
    private ExportTool exportTool;

    @Mock
    private ExportClient exportClient;

    @Mock
    private Logger log;

    @Mock
    private TaskPool taskPool;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this); // 必须加这一行

        // 替换线程池为同步执行器以便测试
        ThreadPoolExecutor syncExecutor = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        TaskPool syncTaskPool = new TaskPool(syncExecutor);

        exportTool = new ExportTool( exportClient,syncTaskPool,log);
    }


    @Test
    void testGetExportClassFromGenericInterface() throws Exception {
        Function<String, Integer> func = Integer::valueOf;
        Class<Integer> exportClass = exportTool.getExportClass(func);
        assertEquals(Integer.class, exportClass);
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
        ExportTool.ExportQueryFunction<MockPageIn, String> queryFunc = in -> {
            Page<String> page = new Page<>();
            page.setList(Collections.singletonList("test"));
            page.setHasNextPage(false);
            return page;
        };
        Function<String, Integer> convertFunc = Integer::parseInt;

//        when(RequestContextHolderUtils.getEmployeeId()).thenReturn(123L);
        when(exportClient.createExportTask(any(CreateTaskIn.class))).thenReturn(999L);

        ExportTool.ExportHandler<MockPageIn> handler = exportTool.createExportHandler(queryFunc, convertFunc, "测试导出");

        MockPageIn in = new MockPageIn();
        com.jiuaoedu.common.Result<Long> result = handler.handle(in);

        assertNotNull(result.getData());
        verify(exportClient).createExportTask(any(CreateTaskIn.class));
        verify(exportClient).exportFileWithMultipleSheet(anyMap(), anyMap(), anyString(), eq(999L));
    }

    @Test
    void testCreateExportHandler_ExceptionHandling() {
        ExportTool.ExportQueryFunction<MockPageIn, String> queryFunc = in -> {
            throw new RuntimeException("模拟异常");
        };
        Function<String, Integer> convertFunc = Integer::parseInt;

//        when(RequestContextHolderUtils.getEmployeeId()).thenReturn(123L);
        when(exportClient.createExportTask(any(CreateTaskIn.class))).thenReturn(999L);

        ExportTool.ExportHandler<MockPageIn> handler = exportTool.createExportHandler(queryFunc, convertFunc, "测试导出");

        MockPageIn in = new MockPageIn();
        com.jiuaoedu.common.Result<Long> result = handler.handle(in);

        assertNotNull(result.getData());
        verify(exportClient).failedExportTask(eq(999L), contains("模拟异常"));
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
