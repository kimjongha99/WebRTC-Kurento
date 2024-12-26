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

    // Static room management
    private static final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
    private static KurentoClient kurento;

    // Room instance fields
    private final String name;
    private final MediaPipeline pipeline;
    private final ConcurrentHashMap<String, User> participants = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Screen> screenShares = new ConcurrentHashMap<>();

    // Static methods
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

    private Room(String name, MediaPipeline pipeline) {
        this.name = name;
        this.pipeline = pipeline;
        log.info(" Room : {}, 파이프라인 ID: {}", name, pipeline.getId());
    }

    public Screen startScreenShare(String userName) {
        Screen screen = screenShares.get(userName);
        if (screen == null) {
            User user = participants.get(userName);
            if (user != null) {
                log.info("새로운 화면 공유 시작 - 사용자: {}, 방: {}", userName, name);
                screen = new Screen(userName, name, user.getSession(), pipeline);
                screenShares.put(userName, screen);
            }
        }
        return screen;
    }

    public void stopScreenShare(String userName) {
        Screen screen = screenShares.remove(userName);
        if (screen != null) {
            log.info("화면 공유 종료 - 사용자: {}, 방: {}", userName, name);
            screen.close();
            notifyScreenShareEnded(userName);
        }
    }

    public Screen getScreenShare(String userName) {
        return screenShares.get(userName);
    }

    public void notifyScreenShare(String userName) {
        JsonObject notification = new JsonObject();
        notification.addProperty("id", "newScreenShareStarted");
        notification.addProperty("name", userName);

        participants.values().forEach(participant -> {
            if (!participant.getName().equals(userName)) {
                try {
                    participant.sendMessage(notification);
                } catch (Exception e) {
                    log.error("화면 공유 알림 전송 실패", e);
                }
            }
        });
    }

    public void notifyScreenShareEnded(String userName) {
        JsonObject notification = new JsonObject();
        notification.addProperty("id", "screenShareEnded");
        notification.addProperty("name", userName);

        participants.values().forEach(participant -> {
            if (!participant.getName().equals(userName)) {
                try {
                    participant.sendMessage(notification);
                } catch (Exception e) {
                    log.error("화면 공유 종료 알림 전송 실패", e);
                }
            }
        });
    }

    // Existing methods
    public User join(String userName, WebSocketSession session) throws Exception {
        log.info("사용자 {}이 Room {}에 참여하고 있습니다", userName, name);
        User participant = new User(userName, name, session, pipeline);
        participants.put(userName, participant);

        sendNewParticipantNotification(participant);
        sendExistingParticipants(participant);

        return participant;
    }

    public void leave(String userName) throws Exception {
        log.info("사용자 {}이 Room {}을(를) 나가고 있습니다.", userName, name);
        User user = participants.remove(userName);

        if (user != null) {
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

            if (participants.isEmpty()) {
                close();
                rooms.remove(this.name);
            }
        }
    }

    public void close() {
        participants.values().forEach(participant -> {
            try {
                participant.close();
            } catch (Exception e) {
                log.error("Error closing participant", e);
            }
        });

        screenShares.values().forEach(screen -> {
            try {
                screen.close();
            } catch (Exception e) {
                log.error("Error closing screen share", e);
            }
        });

        participants.clear();
        screenShares.clear();
        pipeline.release();
        log.info("Room {}이 닫히고 모든 리소스가 해제되었습니다.", name);
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

    // Getters
    public String getName() { return name; }
    public MediaPipeline getPipeline() { return pipeline; }
    public Collection<User> getParticipants() { return participants.values(); }

    public static Collection<Room> findRooms() {
        return rooms.values();
    }

    public static Room findRoom(String roomName) {
        return rooms.get(roomName);
    }
    public Collection<Screen> getScreenShares() { return screenShares.values(); }
}