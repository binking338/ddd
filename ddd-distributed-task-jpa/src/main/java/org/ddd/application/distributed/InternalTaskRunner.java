package org.ddd.application.distributed;

import lombok.RequiredArgsConstructor;
import org.ddd.application.distributed.persistence.TaskRecord;
import org.ddd.application.distributed.persistence.TaskRecordJpaRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author qiaohe
 * @date 2023/8/19
 */
@RequiredArgsConstructor
public class InternalTaskRunner {
    private final TaskRecordJpaRepository taskRecordJpaRepository;
    private final List<Task> tasks;

    private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(4);
    private Map<Class, Task> taskMap = null;

    private Task resolveTask(Class<?> taskClass) {
        if (taskMap == null) {
            taskMap = new HashMap<>();
            for (Task task : tasks) {
                taskMap.put(task.getClass(), task);
            }
        }
        return taskMap.get(taskClass);
    }

    public void run(TaskRecord taskRecord, Duration delay) {
        executor.schedule(() -> {
            Task task = resolveTask(taskRecord.getTaskClass());
            Object result = null;
            try {
                result = task.process(taskRecord.getParam());
                task.onSuccess(taskRecord.getParam(), result);
                taskRecord.confirmedCompeleted(result, LocalDateTime.now());
                taskRecordJpaRepository.save(taskRecord);
            } catch (Exception ex) {
                task.onFail(taskRecord.getParam(), ex);
            }
            return result;
        }, delay.getSeconds(), TimeUnit.SECONDS);
    }
}
