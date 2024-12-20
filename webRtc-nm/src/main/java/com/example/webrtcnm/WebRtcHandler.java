package com.example.webrtcnm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class WebRtcHandler extends TextWebSocketHandler {
    private final Gson gson = new Gson();

    @Autowired
    private RoomManager roomManager;

    @Autowired
    private UserRegistry registry;

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
        UserSession user = registry.getBySession(session);

        switch (jsonMessage.get("id").getAsString()) {
            case "joinRoom":
                String roomName = jsonMessage.get("room").getAsString();
                String userName = jsonMessage.get("name").getAsString();
                Room room = roomManager.getRoom(roomName);
                registry.register(room.join(userName, session));
                break;

            case "receiveVideoFrom":
                UserSession sender = registry.getByName(jsonMessage.get("sender").getAsString());
                user.receiveVideoFrom(sender, jsonMessage.get("sdpOffer").getAsString());
                break;

            case "leaveRoom":
                leaveRoom(user);
                break;

            case "onIceCandidate":
                JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();
                if (user != null) {
                    IceCandidate cand = new IceCandidate(
                            candidate.get("candidate").getAsString(),
                            candidate.get("sdpMid").getAsString(),
                            candidate.get("sdpMLineIndex").getAsInt()
                    );
                    user.addCandidate(cand, jsonMessage.get("name").getAsString());
                }
                break;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        UserSession user = registry.removeBySession(session);
        if (user != null) {
            Room room = roomManager.getRoom(user.getRoomName());
            room.leave(user);
            if (room.getParticipants().isEmpty()) {
                roomManager.removeRoom(room);
            }
        }
    }

    private void leaveRoom(UserSession user) throws Exception {
        Room room = roomManager.getRoom(user.getRoomName());
        room.leave(user);
        if (room.getParticipants().isEmpty()) {
            roomManager.removeRoom(room);
        }
    }
}
