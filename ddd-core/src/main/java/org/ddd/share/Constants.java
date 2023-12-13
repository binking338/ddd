package org.ddd.share;

import org.springframework.beans.factory.annotation.Value;

/**
 * @author qiaohe
 * @date 2023/11/2
 */
public class Constants {
    public static final String CONFIG_KEY_4_SVC_NAME = "${spring.application.name:default}";
    public static final String CONFIG_KEY_4_DOMAIN_EVENT_SUB_PACKAGE = "${ddd.domain.event.subscriber.scanPackage:}";

    public static final String CONFIG_KEY_4_DISTRIBUTED_EVENT_SCHEDULE_THREADPOOLSIIZE = "${ddd.domain.event.schedule.threadPoolSize:4}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_EVENT_SCHEDULE_BATCHSIZE = "${ddd.domain.event.schedule.batchSize:10}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_EVENT_SCHEDULE_MAXCONCURRENT = "${ddd.domain.eventschedule.maxConcurrency:10}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_EVENT_SCHEDULE_INTERVALSECONDS = "${ddd.domain.event.schedule.intervalSeconds:60}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_EVENT_SCHEDULE_MAXLOCKSECONDS = "${ddd.domain.event.schedule.maxLockSeconds:30}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_EVENT_SCHEDULE_CRON = "${ddd.domain.event.schedule.cron:0 */1 * * * ?}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_EVENT_SCHEDULE_ARCHIVE_BATCHSIZE =  "${ddd.distributed.event.schedule.archive.batchSize:100}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_EVENT_SCHEDULE_ARCHIVE_EXPIREDAYS = "${ddd.distributed.event.schedule.archive.expireDays:7}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_EVENT_SCHEDULE_ARCHIVE_MAXLOCKSECONDS = "${ddd.distributed.event.schedule.archive.maxLockSeconds:172800}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_EVENT_SCHEDULE_ARCHIVE_CRON =  "${ddd.distributed.event.schedule.archive.cron:0 0 2 * * ?}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_EVENT_SCHEDULE_ADDPARTITION_CRON = "${ddd.domain.event.schedule.addpartition.cron:0 0 0 * * ?}";


    public static final String CONFIG_KEY_4_DISTRIBUTED_LOCKER_JDBC_TABLE = "${ddd.distributed.locker.jdbc.table:__locker}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_LOCKER_JDBC_FIELD_NAME = "${ddd.distributed.locker.jdbc.fieldName:name}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_LOCKER_JDBC_FIELD_PWD = "${ddd.distributed.locker.jdbc.fieldPwd:pwd}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_LOCKER_JDBC_FIELD_LOCKAT = "${ddd.distributed.locker.jdbc.fieldLockAt:lock_at}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_LOCKER_JDBC_FIELD_UNLOCKAT = "${ddd.distributed.locker.jdbc.fieldUnlockAt:unlock_at}";

    public static final String CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_THREADPOOLSIIZE = "${ddd.distributed.task.schedule.threadPoolSize:4}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_BATCHSIZE = "${ddd.distributed.task.schedule.batchSize:10}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_MAXCONCURRENT = "${ddd.distributed.task.schedule.maxConcurrency:10}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_INTERVALSECONDS = "${ddd.distributed.task.schedule.intervalSeconds:60}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_MAXLOCKSECONDS = "${ddd.distributed.task.schedule.maxLockSeconds:300}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_CRON = "${ddd.distributed.task.schedule.cron:0 */1 * * * ?}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_ARCHIVE_BATCHSIZE =  "${ddd.distributed.task.schedule.archive.batchSize:100}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_ARCHIVE_EXPIREDAYS = "${ddd.distributed.task.schedule.archive.expireDays:7}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_ARCHIVE_MAXLOCKSECONDS = "${ddd.distributed.task.schedule.archive.maxLockSeconds:172800}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_ARCHIVE_CRON =  "${ddd.distributed.task.schedule.archive.cron:0 0 2 * * ?}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_ADDPARTITION_CRON =  "${ddd.distributed.task.schedule.addpartition.cron:0 0 0 * * ?}";

    public static final String CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_THREADPOOLSIIZE = "${ddd.distributed.saga.schedule.threadPoolSize:4}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_BATCHSIZE = "${ddd.distributed.saga.schedule.batchSize:10}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_MAXCONCURRENT = "${ddd.distributed.saga.schedule.maxConcurrency:10}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_INTERVALSECONDS = "${ddd.distributed.saga.schedule.intervalSeconds:60}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_MAXLOCKSECONDS = "${ddd.distributed.saga.schedule.maxLockSeconds:300}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_COMPENSATION_CRON =  "${ddd.distributed.saga.schedule.compensation.cron:0 */1 * * * ?}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_ROLLBACK_CRON =  "${ddd.distributed.saga.schedule.rollback.cron:0 */1 * * * ?}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_ARCHIVE_BATCHSIZE =  "${ddd.distributed.saga.schedule.archive.batchSize:100}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_ARCHIVE_EXPIREDAYS = "${ddd.distributed.saga.schedule.archive.expireDays:7}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_ARCHIVE_MAXLOCKSECONDS = "${ddd.distributed.saga.schedule.archive.maxLockSeconds:172800}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_ARCHIVE_CRON =  "${ddd.distributed.saga.schedule.archive.cron:0 0 2 * * ?}";
    public static final String CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_ADDPARTITION_CRON =  "${ddd.distributed.saga.schedule.addpartition.cron:0 0 0 * * ?}";

}
