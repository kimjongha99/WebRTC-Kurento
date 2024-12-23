package com.example.groupcall;


import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.PreDestroy;

import org.kurento.client.Continuation;
import org.kurento.client.MediaPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;


public class Room {
    private final String name;
    private final MediaPipeline pipeline;
    private final ConcurrentHashMap<String, UserSession> participants = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(Room.class);


    public Room(String name, MediaPipeline pipeline) {
        this.name = name;
        this.pipeline = pipeline;
        log.info("새 Room 생성: {}, 파이프라인 ID: {}", name,pipeline.getId());
    }


    public UserSession join(String userName, WebSocketSession session) throws Exception {

        log.info("사용자 {}이 Room {}에 참여하고 있습니다", userName, name);
        UserSession participant = new UserSession(userName, name, session, pipeline);
        participants.put(userName, participant);
        log.info("사용자 {}이 Room {}에 참여했습니다. - 현재 참가자: {}",
                userName, name, participants.keySet());

        // 새 참가자 입장을 다른 참가자들에게 알림
        sendNewParticipantNotification(participant);
        // 새 참가자에게 기존 참가자 목록 전송
        sendExistingParticipants(participant);

        return participant;
    }

    public void leave(String userName) throws Exception {
        log.info("사용자 {}이 Room {}을(를) 나가고 있습니다.", userName, name);
        UserSession user = participants.remove(userName);

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
                    // 오류 처리
                }
            });

            log.info("사용자 {}이 회의실 {}에서 나갔습니다 - 나머지 참가자: {}",
                    userName, name, participants.keySet());
            user.close();

            // 마지막 참가자가 나갔을 때 파이프라인과 방 정리
            if (participants.isEmpty()) {
                log.info("회의실 {} - 마지막 참가자 퇴장, 파이프라인 정리", name);
                pipeline.release();

                log.info("회의실 {} - 파이프라인 정리 완료", name);
            }
        }
    }

    private void sendNewParticipantNotification(UserSession newParticipant) {
        JsonObject notification = new JsonObject();
        notification.addProperty("id", "newParticipantArrived");
        notification.addProperty("name", newParticipant.getName());

        participants.values().forEach(participant -> {
            if (!participant.equals(newParticipant)) {
                try {
                    participant.sendMessage(notification);
                } catch (Exception e) {
                    // 오류 처리
                }
            }
        });
    }

    private void sendExistingParticipants(UserSession user) throws Exception {
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
                // 오류 처리
            }
        });
        participants.clear();
        pipeline.release();
        log.info("미디어 파이프라인 해제 - 룸: {}, 파이프라인 ID: {}", name,pipeline.getId());
        log.info("Room {}이 닫히고 모든 리소스가 해제되었습니다.", name);
    }

    // Getters
    public String getName() { return name; }
    public Collection<UserSession> getParticipants() { return participants.values(); }
    public UserSession getParticipant(String name) { return participants.get(name); }
}
