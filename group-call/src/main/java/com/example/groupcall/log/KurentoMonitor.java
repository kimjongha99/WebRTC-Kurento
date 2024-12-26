package com.example.groupcall.log;

import org.kurento.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;


//@Component
//public class KurentoMonitor {
//    private static final Logger log = LoggerFactory.getLogger(KurentoMonitor.class);
//
//    @Autowired
//    private KurentoClient kurento;
//
//    public void monitorKurentoServer() {
//        try {
//            ServerManager serverManager = kurento.getServerManager();
//            List<MediaPipeline> pipelines = serverManager.getPipelines();
//
//            log.info("=== Kurento 파이프라인 상태 ===");
//            log.info("활성 파이프라인 수: {}", pipelines.size());
//
//            int totalOutgoingEndpoints = 0;
//            int totalIncomingEndpoints = 0;
//
//            for (MediaPipeline pipeline : pipelines) {
//                List<MediaObject> elements = pipeline.getChildren();
//                List<WebRtcEndpoint> webrtcEndpoints = elements.stream()
//                        .filter(e -> e instanceof WebRtcEndpoint)
//                        .map(e -> (WebRtcEndpoint) e)
//                        .collect(Collectors.toList());
//
//                int outgoingCount = 0;
//                int incomingCount = 0;
//
//                for (WebRtcEndpoint endpoint : webrtcEndpoints) {
//                    // 송신 확인
//                    if (isOutgoingEndpoint(endpoint)) {
//                        outgoingCount++;
//                    }
//
//                    // 수신 확인
//                    if (isIncomingEndpoint(endpoint)) {
//                        incomingCount++;
//                    }
//                }
//
//                totalOutgoingEndpoints += outgoingCount;
//                totalIncomingEndpoints += incomingCount;
//
//                log.info("\n[파이프라인: {}]", pipeline.getId());
//                log.info("- 총 미디어 엘리먼트: {}", elements.size());
//                log.info("- WebRTC Endpoint 수: {}", webrtcEndpoints.size());
//                log.info("  * 송신 WebRtcEndpoint 수: {}", outgoingCount);
//                log.info("  * 수신 WebRtcEndpoint 수: {}", incomingCount);
//
//                webrtcEndpoints.forEach(endpoint -> {
//                    log.info("    - Endpoint ID: {}", endpoint.getId());
//                });
//            }
//
//            log.info("\n=== WebRTC Endpoint 구분 결과 ===");
//            log.info("- 송신 WebRtcEndpoint 총 사용량: {}", totalOutgoingEndpoints);
//            log.info("- 수신 WebRtcEndpoint 총 사용량: {}", totalIncomingEndpoints);
//
//        } catch (Exception e) {
//            log.error("Kurento 모니터링 실패: {}", e.getMessage(), e);
//        }
//    }
//
//    private boolean isOutgoingEndpoint(WebRtcEndpoint endpoint) {
//        try {
//            // ICE 후보가 생성되었는지 확인하여 송신 여부 판별
//            return endpoint.addIceCandidateFoundListener(event -> {
//                log.info("송신 Endpoint 감지: Candidate {}", event.getCandidate());
//            }) != null;
//        } catch (Exception e) {
//            log.warn("송신 Endpoint 판별 실패: {}", e.getMessage());
//            return false;
//        }
//    }
//
//    private boolean isIncomingEndpoint(WebRtcEndpoint endpoint) {
//        try {
//            // Endpoint가 연결된 상태인지 확인
//            endpoint.addConnectionStateChangedListener(event -> {
//                if (event.getNewState() == ConnectionState.CONNECTED) {
//                    log.info("수신 Endpoint 감지: Endpoint {}", endpoint.getId());
//                }
//            });
//            return true;
//        } catch (Exception e) {
//            log.warn("수신 Endpoint 판별 실패: {}", e.getMessage());
//            return false;
//        }
//    }
//}
