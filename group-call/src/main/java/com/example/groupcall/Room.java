package com.example.groupcall;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class Room {
    private static final Logger log = LoggerFactory.getLogger(Room.class);

    // 정적 룸 관리(이전의 RoomManager)
    private static final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
    private static KurentoClient kurento;

    // 룸 인스턴스 필드
    private final String name;
    private final MediaPipeline pipeline;
    private final ConcurrentHashMap<String, User> participants = new ConcurrentHashMap<>();

    // Static methods for room management
    public static void setKurentoClient(KurentoClient kurentoClient) {
        kurento = kurentoClient;
    }

    public static Room getRoom(String roomName) {
        Room room = rooms.get(roomName);
        if (room == null) {
            log.info("새로운 방 생성: {}", roomName);
            room = new Room(roomName, kurento.createMediaPipeline());
            rooms.put(roomName, room);
        }
        return room;
    }


    public static Collection<Room> findRooms() {
        return rooms.values();
    }

    public MediaPipeline getPipeline() {
        return pipeline;
    }

    public JsonObject getRoomStats() {
        JsonObject stats = new JsonObject();
        stats.addProperty("roomName", name);
        stats.addProperty("participantCount", participants.size());
        stats.addProperty("pipelineId", pipeline.getId());

        JsonArray participantsArray = new JsonArray();
        participants.values().forEach(participant -> {
            JsonObject participantInfo = new JsonObject();
            participantInfo.addProperty("name", participant.getName());
            participantInfo.addProperty("webSocketSessionId", participant.getSession().getId());
            participantInfo.addProperty("outgoingEndpointId", participant.getOutgoingWebRtcPeer().getId());
            participantsArray.add(participantInfo);
        });
        stats.add("participants", participantsArray);

        return stats;
    }

    public static void removeRoom(String roomName) {
        Room room = rooms.remove(roomName);
        if (room != null) {
            try {
                log.info("방 {} 제거 시작", roomName);
                room.close();
                log.info("방 {} 제거 완료 - 현재 방 개수: {}", roomName, rooms.size());
            } catch (Exception e) {
                log.error("방 {} 제거 중 오류 발생: {}", roomName, e.getMessage());
                rooms.remove(roomName);
            }
        }
    }

    private Room(String name, MediaPipeline pipeline) {
        this.name = name;
        this.pipeline = pipeline;
        log.info(" Room : {}, 파이프라인 ID: {}", name, pipeline.getId());
    }

    public User join(String userName, WebSocketSession session) throws Exception {
        log.info("사용자 {}이 Room {}에 참여하고 있습니다", userName, name);
        User participant = new User(userName, name, session, pipeline);
        participants.put(userName, participant);

        // 새 참가자 입장을 다른 참가자들에게 알림
        sendNewParticipantNotification(participant);
        // 새 참가자에게 기존 참가자 목록 전송
        sendExistingParticipants(participant);

        return participant;
    }

    public void leave(String userName) throws Exception {
        log.info("사용자 {}이 Room {}을(를) 나가고 있습니다.", userName, name);
        User user = participants.remove(userName);

        if (user != null) {
            // 다른 참가자들에게 퇴장 알림
            JsonObject notification = new JsonObject();
            notification.addProperty("id", "participantLeft");
            notification.addProperty("name", userName);

            participants.values().forEach(participant -> {
                try {
                    participant.cancelVideoFrom(userName);
                    participant.sendMessage(notification);
                } catch (Exception e) {
                    log.error("Error sending leave notification", e);
                }
            });

            user.close();

            // 마지막 참가자가 나갔을 때 방 제거
            if (participants.isEmpty()) {
                pipeline.release();
                removeRoom(this.name);
            }
        }
    }

    private void sendNewParticipantNotification(User newParticipant) {
        JsonObject notification = new JsonObject();
        notification.addProperty("id", "newParticipantArrived");
        notification.addProperty("name", newParticipant.getName());

        participants.values().forEach(participant -> {
            if (!participant.equals(newParticipant)) {
                try {
                    participant.sendMessage(notification);
                } catch (Exception e) {
                    log.error("Error sending new participant notification", e);
                }
            }
        });
    }

    private void sendExistingParticipants(User user) throws Exception {
        JsonArray participantsArray = new JsonArray();
        participants.values().forEach(participant -> {
            if (!participant.equals(user)) {
                participantsArray.add(participant.getName());
            }
        });

        JsonObject existingParticipantsMsg = new JsonObject();
        existingParticipantsMsg.addProperty("id", "existingParticipants");
        existingParticipantsMsg.add("data", participantsArray);
        user.sendMessage(existingParticipantsMsg);
    }

    public void close() {
        participants.values().forEach(participant -> {
            try {
                participant.close();
            } catch (Exception e) {
                log.error("Error closing participant", e);
            }
        });
        participants.clear();
        pipeline.release();
        log.info("Room {}이 닫히고 모든 리소스가 해제되었습니다.", name);
    }

    // 모니터링용 조회 전용 메서드
    public static Room findRoom(String roomName) {
        return rooms.get(roomName);
    }
    // Getters
    public String getName() { return name; }
    public Collection<User> getParticipants() { return participants.values(); }
    public User getParticipant(String name) { return participants.get(name); }
}