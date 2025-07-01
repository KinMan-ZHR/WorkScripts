package 导出;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.jiuaoedu.servicepublic.export.service.ExportClient;
import com.jiuaoedu.threadpool.ExecutorTask;
import org.slf4j.Logger;

public class ExportTask<T> extends ExecutorTask {
    private final Long taskId;
    private final Supplier<List<T>> dataSupplier;
    private final Class<T> modelClass;
    private final String fileName;
    private final ExportClient exportClient;
    private final Logger log;

    public ExportTask(Long taskId, Supplier<List<T>> dataSupplier, Class<T> modelClass, String fileName, ExportClient exportClient, Logger log) {
        this.taskId = taskId;
        this.dataSupplier = dataSupplier;
        this.modelClass = modelClass;
        this.fileName = fileName;
        this.exportClient = exportClient;
        this.log = log;
    }

    @Override
    public void execute() {
        try {
            List<T> exportData = dataSupplier.get();
            exportSingleSheet(taskId, exportData, modelClass, fileName);
        } catch (Exception e) {
            log.error("导出失败", e);
            exportClient.failedExportTask(taskId, e.getMessage());
        }
    }

    private void exportSingleSheet(Long taskId, List<?> dataList, Class<?> modelClass, String fileName) {
        Map<String, List<?>> datas = new HashMap<>(4);
        datas.put("sheet1", dataList);
        Map<String, Class<?>> models = new HashMap<>(4);
        models.put("sheet1", modelClass);
        exportClient.exportFileWithMultipleSheet(datas, models, fileName + UUID.randomUUID().toString().substring(0, 5) + ".xlsx", taskId);
    }
}