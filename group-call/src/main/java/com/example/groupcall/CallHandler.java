package com.example.groupcall;



import jakarta.annotation.PostConstruct;
import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.google.gson.Gson;
import com.google.gson.JsonObject;



public class CallHandler extends TextWebSocketHandler  {
    private static final Logger log = LoggerFactory.getLogger(CallHandler.class);
    private final Gson gson = new Gson();

    @Autowired
    private KurentoClient kurentoClient;

    @PostConstruct
    public void init() {
        Room.setKurentoClient(kurentoClient);
        log.info("KurentoClient가 Room 클래스에 설정되었습니다.");
    }


    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
        User user = User.getBySession(session);
        String messageId = jsonMessage.get("id").getAsString();

        log.debug("메시지 수신: {} (세션: {})", messageId, session.getId());

        switch (messageId) {
            case "joinRoom":
                joinRoom(jsonMessage, session);
                break;
            case "receiveVideoFrom":
                handleReceiveVideoFrom(jsonMessage, user);
                break;
            case "leaveRoom":
                leaveRoom(user);
                break;
            case "onIceCandidate":
                handleIceCandidate(jsonMessage, user);
                break;
            default:
                log.warn("알 수 없는 메시지 ID: {}", messageId);
                break;
        }
    }

    private void joinRoom(JsonObject params, WebSocketSession session) throws Exception {
        String roomName = params.get("room").getAsString();
        String userName = params.get("name").getAsString();

        log.info("사용자 {} 이(가) 방 {}에 참여 요청", userName, roomName);

        Room room = Room.getRoom(roomName);
        room.join(userName, session);
    }

    private void handleReceiveVideoFrom(JsonObject jsonMessage, User user) throws Exception {
        if (user != null) {
            String senderName = jsonMessage.get("sender").getAsString();
            User sender = User.getByName(senderName);
            String sdpOffer = jsonMessage.get("sdpOffer").getAsString();

            user.receiveVideoFrom(sender, sdpOffer);
        }
    }

    private void leaveRoom(User user) throws Exception {
        if (user != null) {
            String roomName = user.getRoomName();
            log.info("사용자 {} 이(가) 방 {}에서 나가기 요청", user.getName(), roomName);

            Room room = Room.getRoom(roomName);
            room.leave(user.getName());
        }
    }

    private void handleIceCandidate(JsonObject jsonMessage, User user) {
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
        log.debug("WebSocket 연결 종료: {}", session.getId());
        User user = User.removeBySession(session);
        if (user != null) {
            Room room = Room.getRoom(user.getRoomName());
            room.leave(user.getName());
        }
    }
}