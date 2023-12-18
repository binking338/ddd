package org.ddd.application.distributed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ddd.application.distributed.persistence.TaskRecord;
import org.ddd.application.distributed.persistence.TaskRecordJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.ddd.share.Constants.CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_THREADPOOLSIIZE;

/**
 * @author qiaohe
 * @date 2023/8/19
 */
@RequiredArgsConstructor
@Slf4j
public class InternalTaskRunner {
    private final TaskRecordJpaRepository taskRecordJpaRepository;
    private final List<Task> tasks;

    @Value(CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_THREADPOOLSIIZE)
    private int threadPoolsize;
    private ScheduledThreadPoolExecutor executor = null;
    @PostConstruct
    public void init(){
        executor = new ScheduledThreadPoolExecutor(threadPoolsize);
    }

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
            log.info("正在执行异步任务: {}", taskRecord.toString());
            Task task = resolveTask(taskRecord.getTaskClass());
            Object result = null;
            try {
                result = task.process(taskRecord.getParam());
                task.onSuccess(taskRecord.getParam(), result);
                taskRecord.confirmedCompeleted(result, LocalDateTime.now());
                taskRecordJpaRepository.save(taskRecord);
                log.info("结束执行异步任务: id={}", taskRecord.getId());
            } catch (Exception ex) {
                task.onFail(taskRecord.getParam(), ex);
                log.error("异步任务执行异常", ex);
            }
            return result;
        }, delay.getSeconds(), TimeUnit.SECONDS);
    }
}
