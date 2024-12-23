package com.example.groupcall.scheduler;

import org.kurento.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
@Component
public class KurentoMonitor {
    private static final Logger log = LoggerFactory.getLogger(KurentoMonitor.class);
    private static final int UDP_PORTS_START = 5000;
    private static final int UDP_PORTS_END = 5050;
    private static final int TOTAL_UDP_PORTS = UDP_PORTS_END - UDP_PORTS_START + 1;

    @Autowired
    private KurentoClient kurento;

    public void monitorKurentoServer() {
        try {
            ServerManager serverManager = kurento.getServerManager();
            List<MediaPipeline> pipelines = serverManager.getPipelines();

            log.info("=== Kurento 파이프라인 상태 ===");
            log.info("활성 파이프라인 수: {}", pipelines.size());

            int totalEndpoints = 0;
            for (MediaPipeline pipeline : pipelines) {
                List<MediaObject> elements = pipeline.getChildren();
                List<WebRtcEndpoint> webrtcEndpoints = elements.stream()
                        .filter(e -> e instanceof WebRtcEndpoint)
                        .map(e -> (WebRtcEndpoint) e)
                        .collect(Collectors.toList());

                totalEndpoints += webrtcEndpoints.size();

                log.info("\n[파이프라인: {}]", pipeline.getId());
                log.info("- 총 미디어 엘리먼트: {}", elements.size());
                log.info("- WebRTC Endpoint 수: {}", webrtcEndpoints.size());

                webrtcEndpoints.forEach(endpoint -> {
                    log.info("  * Endpoint ID: {}", endpoint.getId());
                });
            }

            // UDP 포트 사용량 계산
            int usedPorts = totalEndpoints * 2;  // 각 endpoint당 2개 포트
            int remainingPorts = TOTAL_UDP_PORTS - usedPorts;
            float portUsagePercent = (usedPorts * 100.0f) / TOTAL_UDP_PORTS;

            log.info("\n=== UDP 포트 사용량 ===");
            log.info("- 총 할당된 포트: {} ({}개)",
                    String.format("%d-%d", UDP_PORTS_START, UDP_PORTS_END),
                    TOTAL_UDP_PORTS);
            log.info("- 사용 중인 포트: {} (예상)", usedPorts);
            log.info("- 남은 포트: {} (예상)", remainingPorts);

            // 경고 표시
            if (portUsagePercent > 80) {
                log.warn("⚠️ UDP 포트 사용량이 80%를 초과했습니다!");
            }
            log.info("=== Kurento 파이프라인 끝 ===");

        } catch (Exception e) {
            log.error("Kurento 모니터링 실패: {}", e.getMessage());
        }
    }
}