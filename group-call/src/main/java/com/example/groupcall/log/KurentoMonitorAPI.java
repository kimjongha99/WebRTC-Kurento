package com.example.groupcall.log;

import com.example.groupcall.Room;
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

import java.util.List;
import java.util.Collection;

@RestController
@RequestMapping("/kurento")
public class KurentoMonitorAPI {
    private static final Logger log = LoggerFactory.getLogger(KurentoMonitorAPI.class);

    @Autowired
    private KurentoClient kurento;



    @GetMapping("/monitor/server")
    public ResponseEntity<Object> monitorServer() {
        try {
            ServerManager serverManager = kurento.getServerManager();
            JsonObject response = new JsonObject();
            // 서버 정보
            ServerInfo info = serverManager.getInfo();
            response.addProperty("version", info.getVersion());
            response.add("capabilities", new com.google.gson.JsonParser().parse(info.getCapabilities().toString()));

            // CPU 정보
            response.addProperty("cpuCount", serverManager.getCpuCount());
            response.addProperty("cpuUsage", serverManager.getUsedCpu(1000));

            // 메모리 정보
            response.addProperty("usedMemory", serverManager.getUsedMemory());

            // 파이프라인 정보
            List<MediaPipeline> pipelines = serverManager.getPipelines();
            response.addProperty("activePipelines", pipelines.size());

            // 세션 정보
            List<String> sessions = serverManager.getSessions();
            response.addProperty("activeSessions", sessions.size());

            log.info("서버 모니터링 완료");
            return ResponseEntity.ok(new ObjectMapper().readValue(response.toString(), Object.class));
        } catch (Exception e) {
            log.error("서버 모니터링 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("서버 모니터링 실패: " + e.getMessage());
        }
    }

    @GetMapping("/monitor/user/session")
    public ResponseEntity<Object> monitorUserSession() {
        try {
            JsonObject response = new JsonObject();
            Collection<Room> rooms = Room.findRooms();

            rooms.forEach(room -> {
                JsonObject roomInfo = new JsonObject();
                room.getParticipants().forEach(user -> {
                    JsonObject userInfo = new JsonObject();
                    userInfo.addProperty("userName", user.getName());
                    userInfo.addProperty("webSocketSessionId", user.getSession().getId());
                    userInfo.addProperty("endpointId", user.getOutgoingWebRtcPeer().getId());
                    roomInfo.add(user.getName(), userInfo);
                });
                response.add(room.getName(), roomInfo);
            });

            log.info("세션 모니터링 완료");
            return ResponseEntity.ok(new ObjectMapper().readValue(response.toString(), Object.class));
        } catch (Exception e) {
            log.error("세션 모니터링 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("세션 모니터링 실패: " + e.getMessage());
        }
    }

    @GetMapping("/monitor/room/{roomId}")
    public ResponseEntity<Object> monitorRoom(@PathVariable String roomId) {
        try {
            Room room = Room.findRoom(roomId);
            if (room == null) {
                return ResponseEntity.notFound().build();
            }

            JsonObject response = room.getRoomStats();
            log.info("방 {} 모니터링 완료", roomId);
            return ResponseEntity.ok(new ObjectMapper().readValue(response.toString(), Object.class));
        } catch (Exception e) {
            log.error("방 모니터링 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("방 모니터링 실패: " + e.getMessage());
        }
    }

    @GetMapping("/{roomName}/stats")
    public ResponseEntity<Object> getRoomStats(@PathVariable String roomName) {
        try {
            Room room = Room.findRoom(roomName);
            if (room == null) {
                return ResponseEntity.notFound().build();
            }

            JsonObject roomStats = room.getRoomStats();
            return ResponseEntity.ok(new ObjectMapper().readValue(roomStats.toString(), Object.class));
        } catch (Exception e) {
            log.error("방 통계 조회 실패: {}", e.getMessage(), e);
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("error", "Failed to retrieve room stats");
            errorResponse.addProperty("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}