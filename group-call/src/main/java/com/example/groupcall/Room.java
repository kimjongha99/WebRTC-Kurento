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

    public Room(String name, MediaPipeline pipeline) {
        this.name = name;
        this.pipeline = pipeline;
    }

    public UserSession join(String userName, WebSocketSession session) throws Exception {
        UserSession participant = new UserSession(userName, name, session, pipeline);
        participants.put(userName, participant);

        // 새 참가자 입장을 다른 참가자들에게 알림
        sendNewParticipantNotification(participant);
        // 새 참가자에게 기존 참가자 목록 전송
        sendExistingParticipants(participant);

        return participant;
    }

    public void leave(String userName) throws Exception {
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

            user.close();
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
    }

    // Getters
    public String getName() { return name; }
    public Collection<UserSession> getParticipants() { return participants.values(); }
    public UserSession getParticipant(String name) { return participants.get(name); }
}
