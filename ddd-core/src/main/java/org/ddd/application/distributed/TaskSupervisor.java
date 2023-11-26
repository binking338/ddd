package org.ddd.application.distributed;

/**
 * @author qiaohe
 * @date 2023/8/5
 */
public interface TaskSupervisor {
    /**
     * 即时任务
     * @param taskClass
     * @param param
     * @param <Param>
     * @param <Result>
     */
    <Param, Result> void run(Class<Task<Param, Result>> taskClass, Param param, java.time.Duration expire, int retryTimes);

    /**
     * 延时任务
     * @param taskClass
     * @param param
     * @param delay
     * @param expire
     * @param retryTimes
     * @param <Param>
     * @param <Result>
     */
    <Param, Result> void delay(Class<Task<Param, Result>> taskClass, Param param, java.time.Duration delay, java.time.Duration expire, int retryTimes);

}
