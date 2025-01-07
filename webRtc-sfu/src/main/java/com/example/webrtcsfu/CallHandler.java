package com.example.webrtcsfu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.kurento.client.IceCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

public class CallHandler extends TextWebSocketHandler {


    private static final Logger log = LoggerFactory.getLogger(CallHandler.class);
    private static final Gson gson = new GsonBuilder().create();

    @Autowired
    private RoomRegister roomRegister;

    @Autowired
    private UserRegister userRegister;


    @Autowired
    private MessageSender messageSender;  // MessageSender 주입


    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);


        switch (jsonMessage.get("id").getAsString()) {

            case "joinRoom":
                joinRoom(jsonMessage, session);
                break;
            case "receiveVideoFrom":
                String senderName = jsonMessage.get("sender").getAsString();
                UserSession sender = userRegister.getByName(senderName);
                String sdpOffer = jsonMessage.get("sdpOffer").getAsString();


                UserSession sdpOfferUser = userRegister.getBySession(session);


                sdpOfferUser.receiveVideoFrom(sender, sdpOffer);
                break;
            case "onIceCandidate":
                JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();
                UserSession icecandidateUser = userRegister.getBySession(session);

                if (icecandidateUser != null) {
                    IceCandidate cand = new IceCandidate(candidate.get("candidate").getAsString(),
                            candidate.get("sdpMid").getAsString(), candidate.get("sdpMLineIndex").getAsInt());
                    icecandidateUser.addCandidate(cand, jsonMessage.get("name").getAsString());
                }
        }


    }

    private void joinRoom(JsonObject params, WebSocketSession session) throws IOException {
        String roomName = params.get("room").getAsString();
        String name = params.get("name").getAsString();

        Room room;
        if (!roomRegister.getRoom(roomName)) {
            // 방이 없으면 새로 생성
            log.info("새로운 방 생성: {}", roomName);
            room = roomRegister.createRoom(roomName);
        } else {
            // 방이 있으면 기존 방 가져오기
            room = roomRegister.getRoomByName(roomName);
        }


        UserSession user = room.join(name, session); // join하면서 유저도 생성
        userRegister.register(user);  // 추후 정리 해야함.
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket 연결 종료 감지: {}", session.getId());
        // 1. UserRegister에서 사용자 정보 조회
        UserSession disconnectedUser = userRegister.getBySession(session);
        if (disconnectedUser == null) {
            log.warn("연결 종료된 사용자를 찾을 수 없음: {}", session.getId());
            return;
        }
        try {
            // 2. Room에서 사용자 제거 및 다른 참가자들에게 알림
            Room room = roomRegister.getRoomByName(disconnectedUser.getRoomName());
            if (room != null) {
                // Room의 leave 메서드 호출 - 다른 참가자들에게 알림 전송 및 room에서 참가자 제거
                room.leave(disconnectedUser);

                // 3. 방이 비었는지 확인하고 비었다면 방 제거
                if (room.getParticipants().isEmpty()) {
                    log.info("방 {} 비었음 - 방 제거", room.getRoomName());
                    roomRegister.removeRoom(room);
                }
            }
            // 4. UserRegister에서 사용자 정보 제거
            userRegister.removeBySession(session);

            log.info("사용자 {} 연결 종료 처리 완료", disconnectedUser.getName());
        } catch (Exception e) {
            log.error("사용자 연결 종료 처리 중 오류 발생: {}", disconnectedUser.getName(), e);
        }


    }

}
