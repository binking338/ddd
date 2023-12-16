CREATE TABLE `__task` (
                          `id` bigint(20) NOT NULL AUTO_INCREMENT,
                          `task_uuid` varchar(64) NOT NULL DEFAULT '',
                          `svc_name` varchar(255) NOT NULL DEFAULT '',
                          `task_type` varchar(255) NOT NULL DEFAULT '',
                          `data` text,
                          `data_type` varchar(255) NOT NULL DEFAULT '',
                          `result` text,
                          `result_type` varchar(255) NOT NULL DEFAULT '',
                          `task_state` int(11) NOT NULL DEFAULT '0',
                          `expire_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          `create_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          `last_try_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          `next_try_time` datetime NOT NULL DEFAULT '0001-01-01 00:00:00',
                          `tried_times` int(11) NOT NULL DEFAULT '0',
                          `try_times` int(11) NOT NULL DEFAULT '0',
                          `version` int(11) NOT NULL DEFAULT '0',
                          `db_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `db_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          PRIMARY KEY (
                                       `id`
                              -- , `db_created_at`
                              ),
                          UNIQUE KEY `uniq_task_uuid` (`task_uuid`
                              -- , `db_created_at`
                              ),
                          KEY `idx_next_try_time` (`next_try_time`,`task_state`,`svc_name`,`task_type`),
                          KEY `idx_expire_at` (`expire_at`),
                          KEY `idx_create_at` (`create_at`),
                          KEY `idx_db_created_at` (`db_created_at`),
                          KEY `idx_db_updated_at` (`db_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='延时任务\n@I;'
-- partition by range(to_days(db_created_at))
-- (partition p202201 values less than (to_days('2022-02-01')) ENGINE=InnoDB)
;

CREATE TABLE `__archived_task` (
                          `id` bigint(20) NOT NULL AUTO_INCREMENT,
                          `task_uuid` varchar(64) NOT NULL DEFAULT '',
                          `svc_name` varchar(255) NOT NULL DEFAULT '',
                          `task_type` varchar(255) NOT NULL DEFAULT '',
                          `data` text,
                          `data_type` varchar(255) NOT NULL DEFAULT '',
                          `result` text,
                          `result_type` varchar(255) NOT NULL DEFAULT '',
                          `task_state` int(11) NOT NULL DEFAULT '0',
                          `expire_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          `create_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          `last_try_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          `next_try_time` datetime NOT NULL DEFAULT '0001-01-01 00:00:00',
                          `tried_times` int(11) NOT NULL DEFAULT '0',
                          `try_times` int(11) NOT NULL DEFAULT '0',
                          `version` int(11) NOT NULL DEFAULT '0',
                          `db_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `db_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          PRIMARY KEY (
                                       `id`
                              -- , `db_created_at`
                              ),
                          UNIQUE KEY `uniq_task_uuid` (`task_uuid`
                              -- , `db_created_at`
                              ),
                          KEY `idx_next_try_time` (`next_try_time`,`task_state`,`svc_name`,`task_type`),
                          KEY `idx_expire_at` (`expire_at`),
                          KEY `idx_create_at` (`create_at`),
                          KEY `idx_db_created_at` (`db_created_at`),
                          KEY `idx_db_updated_at` (`db_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='延时任务\n@I;'
-- partition by range(to_days(db_created_at))
-- (partition p202201 values less than (to_days('2022-02-01')) ENGINE=InnoDB)
;