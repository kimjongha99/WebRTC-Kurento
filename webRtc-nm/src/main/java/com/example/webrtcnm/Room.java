package com.example.webrtcnm;
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





public class Room implements Closeable {
    private final Logger log = LoggerFactory.getLogger(Room.class);
    private final ConcurrentHashMap<String, UserSession> participants = new ConcurrentHashMap<>();
    private final MediaPipeline pipeline;
    private final String name;

    public Room(String name, MediaPipeline pipeline) {
        this.name = name;
        this.pipeline = pipeline;
    }

    public UserSession join(String userName, WebSocketSession session) throws IOException {
        UserSession participant = new UserSession(userName, name, session, pipeline);
        notifyParticipants("newParticipantArrived", userName);
        participants.put(userName, participant);
        sendParticipantsList(participant);
        return participant;
    }

    public void leave(UserSession user) throws IOException {
        participants.remove(user.getName());
        notifyParticipants("participantLeft", user.getName());
        user.close();
    }

    private void notifyParticipants(String eventId, String userName) {
        JsonObject msg = new JsonObject();
        msg.addProperty("id", eventId);
        msg.addProperty("name", userName);
        participants.values().forEach(participant -> {
            if (eventId.equals("participantLeft")) {
                participant.cancelVideoFrom(userName);
            }
            participant.sendMessage(msg);
        });
    }

    private void sendParticipantsList(UserSession user) throws IOException {
        JsonObject msg = new JsonObject();
        msg.addProperty("id", "existingParticipants");
        JsonArray participantsArray = new JsonArray();
        participants.values().stream()
                .filter(p -> !p.equals(user))
                .forEach(p -> participantsArray.add(p.getName()));
        msg.add("data", participantsArray);
        user.sendMessage(msg);
    }

    public Collection<UserSession> getParticipants() {
        return participants.values();
    }

    public UserSession getParticipant(String name) {
        return participants.get(name);
    }

    public String getName() {
        return name;
    }

    @Override
    public void close() {
        participants.values().forEach(user -> {
            try {
                user.close();
            } catch (IOException e) {
                log.warn("Error closing user: {}", user.getName(), e);
            }
        });
        participants.clear();
        pipeline.release();
    }
}