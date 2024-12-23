package com.example.groupcall;



import org.kurento.client.IceCandidate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.google.gson.Gson;
import com.google.gson.JsonObject;


public class CallHandler extends TextWebSocketHandler {
    private final Gson gson = new Gson();

    @Autowired
    private RoomManager roomManager;

    @Autowired
    private UserRegistry userRegistry;

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
        UserSession user = userRegistry.getBySession(session);
        String messageId = jsonMessage.get("id").getAsString();

        switch (messageId) {
            case "joinRoom":
                joinRoom(jsonMessage, session);
                break;
            case "receiveVideoFrom":
                String senderName = jsonMessage.get("sender").getAsString();
                user.receiveVideoFrom(
                        userRegistry.getByName(senderName),
                        jsonMessage.get("sdpOffer").getAsString()
                );
                break;
            case "leaveRoom":
                leaveRoom(user);
                break;
            case "onIceCandidate":
                handleIceCandidate(jsonMessage, user);
                break;
        }
    }

    private void joinRoom(JsonObject params, WebSocketSession session) throws Exception {
        String roomName = params.get("room").getAsString();
        String userName = params.get("name").getAsString();

        Room room = roomManager.getRoom(roomName);
        UserSession user = room.join(userName, session);
        userRegistry.register(user);
    }

    private void leaveRoom(UserSession user) throws Exception {
        Room room = roomManager.getRoom(user.getRoomName());
        room.leave(user.getName());
        if (room.getParticipants().isEmpty()) {
            roomManager.removeRoom(room.getName());
        }
    }

    private void handleIceCandidate(JsonObject jsonMessage, UserSession user) {
        if (user != null) {
            JsonObject candidateJson = jsonMessage.get("candidate").getAsJsonObject();
            IceCandidate candidate = new IceCandidate(
                    candidateJson.get("candidate").getAsString(),
                    candidateJson.get("sdpMid").getAsString(),
                    candidateJson.get("sdpMLineIndex").getAsInt()
            );
            user.addCandidate(candidate, jsonMessage.get("name").getAsString());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        UserSession user = userRegistry.removeBySession(session);
        if (user != null) {
            Room room = roomManager.getRoom(user.getRoomName());
            room.leave(user.getName());
        }
    }
}