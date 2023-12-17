package org.ddd.application.distributed;

import org.ddd.application.distributed.persistence.TaskRecord;

import java.time.Duration;

/**
 * @author qiaohe
 * @date 2023/8/5
 */
public interface TaskSupervisor {
    /**
     * 即时任务
     * @param taskClass
     * @param param
     * @param uuid
     * @param expire
     * @param retryTimes
     * @param <Param>
     * @param <Result>
     * @param <T>
     * @return 是否提交成功
     */
    <Param, Result, T extends Task<Param, Result>> boolean run(Class<T> taskClass, Param param, String uuid, Duration expire, int retryTimes);

    /**
     * 延时任务
     * @param taskClass
     * @param param
     * @param uuid
     * @param delay
     * @param expire
     * @param retryTimes
     * @param <Param>
     * @param <Result>
     * @return 是否提交成功
     */
    <Param, Result, T extends Task<Param, Result>> boolean delay(Class<T> taskClass, Param param, String uuid, java.time.Duration delay, java.time.Duration expire, int retryTimes);

    /**
     * 查询任务
     * @param uuid
     * @return
     */
    TaskRecord query(String uuid);
}
