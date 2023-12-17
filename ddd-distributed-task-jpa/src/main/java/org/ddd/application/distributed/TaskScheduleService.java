package org.ddd.application.distributed;

import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.ddd.application.distributed.persistence.ArchivedTaskRecord;
import org.ddd.application.distributed.persistence.ArchivedTaskRecordJpaRepository;
import org.ddd.application.distributed.persistence.TaskRecord;
import org.ddd.application.distributed.persistence.TaskRecordJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.SystemPropertyUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.ddd.share.Constants.CONFIG_KEY_4_SVC_NAME;

/**
 * @author qiaohe
 * @date 2023/8/19
 */
@RequiredArgsConstructor
@Slf4j
public class TaskScheduleService {
    private static final String KEY_COMPENSATION_LOCKER = "task_compensation[" + CONFIG_KEY_4_SVC_NAME + "]";
    private static final String KEY_ARCHIVE_LOCKER = "task_archive[" + CONFIG_KEY_4_SVC_NAME + "]";

    private final Locker locker;
    private final TaskRecordJpaRepository taskRecordJpaRepository;
    private final ArchivedTaskRecordJpaRepository archivedTaskRecordJpaRepository;
    private final InternalTaskRunner internalTaskRunner;

    @Value(CONFIG_KEY_4_SVC_NAME)
    private String svcName = null;

    private String getSvcName() {
        return svcName;
    }

    @Value(KEY_COMPENSATION_LOCKER)
    private String compensationLockerKey = null;

    private String getCompensationLockerKey() {
        return compensationLockerKey;
    }

    private boolean compensationRunning = false;
    private int compensationDelayMillis = 0;

    public void compensation(int batchSize, int maxConcurrency, Duration interval, Duration maxLockDuration) {
        if (compensationRunning) {
            log.info("异步任务补偿:上次任务补偿仍未结束，跳过");
            return;
        }
        compensationRunning = true;
        trySleep(compensationDelayMillis);

        String pwd = RandomStringUtils.random(8, true, true);
        String svcName = getSvcName();
        String lockerKey = getCompensationLockerKey();
        try {
            boolean noneTask = false;
            while (!noneTask) {
                LocalDateTime now = LocalDateTime.now();
                try {
                    if (!locker.acquire(lockerKey, pwd, maxLockDuration)) {
                        trySleep(interval.getSeconds() * 1000 / maxConcurrency);
                        compensationDelayMillis = (int) ((compensationDelayMillis + (interval.getSeconds() * 1000 / maxConcurrency)) % (interval.getSeconds() * 1000));
                        return;
                    }
                    Page<TaskRecord> tasks = taskRecordJpaRepository.findAll((root, cq, cb) -> {
                        cq.where(cb.or(
                                cb.and(
                                        // 【初始状态】
                                        cb.equal(root.get(TaskRecord.F_TASK_STATE), TaskRecord.TaskState.INIT),
                                        cb.lessThan(root.get(TaskRecord.F_NEXT_TRY_TIME), now.plusSeconds(interval.getSeconds() / 2)),
                                        cb.equal(root.get(TaskRecord.F_SVC_NAME), svcName)
                                ), cb.and(
                                        // 【未知状态】
                                        cb.equal(root.get(TaskRecord.F_TASK_STATE), TaskRecord.TaskState.COMFIRMING),
                                        cb.lessThan(root.get(TaskRecord.F_NEXT_TRY_TIME), now.plusSeconds(interval.getSeconds() / 2)),
                                        cb.equal(root.get(TaskRecord.F_SVC_NAME), svcName)
                                )));
                        return null;
                    }, PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, TaskRecord.F_CREATE_AT)));
                    if (!tasks.hasContent()) {
                        noneTask = true;
                        continue;
                    }
                    for (TaskRecord taskRecord : tasks.getContent()) {
                        log.info("异步任务补偿: {}", taskRecord.toString());
                        LocalDateTime nextTryTime = taskRecord.getNextTryTime();
                        taskRecord.beginRun(nextTryTime);
                        taskRecord = taskRecordJpaRepository.saveAndFlush(taskRecord);
                        Duration delay = nextTryTime.isAfter(now) ? Duration.between(now, nextTryTime) : Duration.ZERO;
                        internalTaskRunner.run(taskRecord, delay);
                    }
                } catch (Exception ex) {
                    log.error("异步任务补偿:异常失败", ex);
                } finally {
                    locker.release(lockerKey, pwd);
                }
            }
        } finally {
            compensationRunning = false;
        }
    }

    private void trySleep(long mills) {
        try {
            if (mills > 0) {
                Thread.sleep(mills);
            }
        } catch (InterruptedException e) {
            /* ignore */
        }
    }

    @Value(KEY_ARCHIVE_LOCKER)
    private String archiveLockerKey = null;

    private String getArchiveLockerKey() {
        return archiveLockerKey;
    }

    /**
     * 异步任务归档
     */
    public void archive(int expireDays, int batchSize, Duration maxLockDuration) {
        String pwd = RandomStringUtils.random(8, true, true);
        String svcName = getSvcName();
        String lockerKey = getArchiveLockerKey();

        if (!locker.acquire(lockerKey, pwd, maxLockDuration)) {
            return;
        }
        log.info("异步任务归档");

        Date now = new Date();
        while (true) {
            try {
                Page<TaskRecord> taskRecords = taskRecordJpaRepository.findAll((root, cq, cb) -> {
                    cq.where(
                            cb.and(
                                    // 【状态】
                                    cb.or(
                                            cb.equal(root.get(TaskRecord.F_TASK_STATE), TaskRecord.TaskState.CANCEL),
                                            cb.equal(root.get(TaskRecord.F_TASK_STATE), TaskRecord.TaskState.EXPIRED),
                                            cb.equal(root.get(TaskRecord.F_TASK_STATE), TaskRecord.TaskState.FAILED),
                                            cb.equal(root.get(TaskRecord.F_TASK_STATE), TaskRecord.TaskState.DELIVERED)
                                    ),
                                    cb.lessThan(root.get(TaskRecord.F_EXPIRE_AT), DateUtils.addDays(now, expireDays)))
                    );
                    return null;
                }, PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, TaskRecord.F_CREATE_AT)));
                if (!taskRecords.hasContent()) {
                    break;
                }
                List<ArchivedTaskRecord> archivedSagas = taskRecords.stream().map(s -> ArchivedTaskRecord.builder()
                        .id(s.getId())
                        .svcName(s.getSvcName())
                        .taskType(s.getTaskType())
                        .taskUuid(s.getTaskUuid())
                        .taskState(s.getTaskState())
                        .data(s.getData())
                        .dataType(s.getDataType())
                        .result(s.getResult())
                        .resultType(s.getResultType())
                        .createAt(s.getCreateAt())
                        .expireAt(s.getExpireAt())
                        .nextTryTime(s.getNextTryTime())
                        .lastTryTime(s.getLastTryTime())
                        .tryTimes(s.getTryTimes())
                        .triedTimes(s.getTriedTimes())
                        .version(s.getVersion())
                        .build()
                ).collect(Collectors.toList());
                migrate(taskRecords.toList(), archivedSagas);
            } catch (Exception ex) {
                log.error("异步任务归档:异常失败", ex);
            }
        }
        locker.release(lockerKey, pwd);
    }
    @Transactional
    public void migrate(List<TaskRecord> taskRecords, List<ArchivedTaskRecord> archivedTaskRecords) {
        archivedTaskRecordJpaRepository.saveAll(archivedTaskRecords);
        taskRecordJpaRepository.deleteInBatch(taskRecords);
    }


    public void addPartition() {
        Date now = new Date();
        addPartition("__task", DateUtils.addMonths(now, 1));
        addPartition("__archived_task", DateUtils.addMonths(now, 1));
    }

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建date日期所在月下个月的分区
     * @param table
     * @param date
     */
    private void addPartition(String table, Date date) {
        String sql = "alter table `" + table + "` add partition (partition p" + DateFormatUtils.format(date, "yyyyMM") + " values less than (to_days('" + DateFormatUtils.format(DateUtils.addMonths(date, 1), "yyyy-MM") + "-01')) ENGINE=InnoDB)";
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            if (!ex.getMessage().contains("Duplicate partition")) {
                log.error("分区创建异常 table = " + table + " partition = p" + DateFormatUtils.format(date, "yyyyMM"), ex);
            }
        }
    }
}
