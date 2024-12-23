package com.example.groupcall.scheduler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;


@Configuration
@EnableScheduling
public class KurentoSchedulerConfig {
    @Autowired
    private KurentoMonitor monitor;

    @Scheduled(initialDelay = 20000, fixedRate = 30000)  // 20초 후에 시작, 30초마다 실행
    public void scheduleKurentoMonitoring() {
        monitor.monitorKurentoServer();
    }
}