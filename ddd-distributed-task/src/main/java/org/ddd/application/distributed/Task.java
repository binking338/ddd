package org.ddd.application.distributed;

/**
 * @author qiaohe
 * @date 2023/8/5
 */
public interface Task<Param, Result> {
    /**
     * 任务处理
     * @param param
     */
    Result process(Param param);

    /**
     * 成功回调
     * @param param
     * @param result
     */
    default void onSuccess(Param param, Result result) {
    }

    /**
     * 失败回调
     * @param param
     * @param throwable
     */
    default void onFail(Param param, Throwable throwable){
    }
}
