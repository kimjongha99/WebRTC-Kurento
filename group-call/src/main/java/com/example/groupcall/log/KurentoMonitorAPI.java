package com.example.groupcall.log;

import com.example.groupcall.Room;
import com.example.groupcall.RoomManager;
import com.example.groupcall.UserRegistry;
import com.example.groupcall.UserSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import org.kurento.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.kurento.client.StatsType.session;


@RestController
@RequestMapping("/kurento")
public class KurentoMonitorAPI {
    private static final Logger log = LoggerFactory.getLogger(KurentoMonitorAPI.class);

    @Autowired
    private KurentoClient kurento;
    @Autowired
    private UserRegistry userRegistry; // WebSocket 세션과 UserSession 매핑을 위해 필요

    @Autowired
    private RoomManager roomManager;

    @GetMapping("/monitor/server")
    public void monitorServer() {
        try {
            ServerManager serverManager = kurento.getServerManager();

            // 서버 정보
            ServerInfo info = serverManager.getInfo();
            log.info("=== Kurento 서버 정보 ===");
            log.info("버전: {}", info.getVersion());
            log.info("Capabilities: {}", info.getCapabilities());

            // CPU 정보
            int cpuCount = serverManager.getCpuCount();
            float cpuUsage = serverManager.getUsedCpu(1000); // 1초 간격으로 측정
            log.info("=== CPU 정보 ===");
            log.info("CPU 코어 수: {}", cpuCount);
            log.info("CPU 사용률: {}%", cpuUsage);

            // 메모리 정보
            long usedMemory = serverManager.getUsedMemory();
            log.info("=== 메모리 정보 ===");
            log.info("사용 중인 메모리: {} KiB", usedMemory);

            // 파이프라인 정보
            List<MediaPipeline> pipelines = serverManager.getPipelines();
            log.info("=== 파이프라인 정보 ===");
            log.info("활성 파이프라인 수: {}", pipelines.size());
            for (MediaPipeline pipeline : pipelines) {
                log.info("파이프라인 ID: {}", pipeline.getId());
            }

            // 세션 정보
            List<String> sessions = serverManager.getSessions();
            log.info("=== 세션 정보 ===");
            log.info("활성 세션 수: {}", sessions.size());
            sessions.forEach(session -> {
                if (session != null && !session.isEmpty()) {
                    log.info("유효한 세션 ID: {}", session);
                    try {
                        // 세션과 관련된 파이프라인 정보 조회
                        JsonObject params = new JsonObject();
                        params.addProperty("sessionId", session);

                        // ServerManager를 통해 세션 상태 조회
                        MediaPipeline pipeline = serverManager.getPipelines().stream()
                                .filter(p -> session.equals(p.getId()))
                                .findFirst()
                                .orElse(null);

                        if (pipeline != null) {
                            log.info("- 파이프라인 상태: 활성");
                            log.info("- 파이프라인 ID: {}", pipeline.getId());
                        } else {
                            log.info("- 제어/시스템 세션");
                        }
                    } catch (Exception e) {
                        log.debug("세션 상세 정보 조회 실패: {}", e.getMessage());
                    }
                } else {
                    log.info("제어 세션 (ID 없음)");
                }
            });


        } catch (Exception e) {
            log.error("서버 모니터링 실패: {}", e.getMessage(), e);
        }
    }

    @GetMapping("/monitor/user/session")
    public void monitorUserSession() {
        try {
            log.info("=== 세션 상세 정보 ===");
            // 활성화된 모든 룸 조회
            for (Room room : roomManager.getRooms()) {
                log.info("=== 룸 [{}] 세션 정보 ===", room.getName());

                // 룸의 모든 참가자에 대해
                for (UserSession userSession : room.getParticipants()) {
                    WebSocketSession wsSession = userSession.getSession();
                    String userName = userSession.getName();
                    String wsSessionId = wsSession.getId();

                    log.info("참가자: {}", userName);
                    log.info("- WebSocket 세션 ID: {}", wsSessionId);
                    log.info("- Kurento 엔드포인트 ID: {}",
                            userSession.getOutgoingWebRtcPeer().getId());
                }
            }
        } catch (Exception e) {
            log.error("서버 모니터링 실패: {}", e.getMessage(), e);
        }
    }

    @GetMapping("/monitor/room/{roomId}")
    public void monitorRoom(@PathVariable String roomId) {
        try {
            Room room = roomManager.getRoom(roomId);
            if (room == null) {
                log.error("Room not found: {}", roomId);
                return;
            }

            MediaPipeline pipeline = room.getPipeline();

            log.info("=== 방 상세 정보 ===");
            log.info("방 ID: {}", roomId);
            log.info("파이프라인 ID: {}", pipeline.getId());
            log.info("참가자 수: {}", room.getParticipants().size());

        } catch (Exception e) {
            log.error("Room monitoring failed: {}", e.getMessage(), e);
        }
    }

    @GetMapping("/{roomName}/stats")
    public ResponseEntity<Object> getRoomStats(@PathVariable String roomName) {
        try {
            JsonObject roomStats = roomManager.getRoomStats(roomName);

            // Gson JsonObject를 Jackson Object로 변환
            ObjectMapper mapper = new ObjectMapper();
            Object jsonObject = mapper.readValue(roomStats.toString(), Object.class);

            return ResponseEntity.ok(jsonObject);
        } catch (Exception e) {
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("error", "Failed to retrieve room stats");
            errorResponse.addProperty("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}