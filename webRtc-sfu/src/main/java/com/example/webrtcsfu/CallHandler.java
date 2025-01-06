package com.example.webrtcsfu;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.kurento.client.IceCandidate;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

public class CallHandler extends TextWebSocketHandler  {
    private static final Logger log = LoggerFactory.getLogger(CallHandler.class);
    private final Gson gson = new Gson();

    @Autowired
    private RoomRegister roomRegister;

    @Autowired
    private UserRegister userRegister;


    @Autowired
    private MessageSender messageSender;  // MessageSender 주입


    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);

        if ("joinRoom".equals(jsonMessage.get("id").getAsString())) {
            String roomName = jsonMessage.get("roomName").getAsString();
            String userName = jsonMessage.get("userName").getAsString();

            log.info("사용자 {} 가 방 {} 에 참가 요청", userName, roomName);
            Room room;
            // 방이 존재하는지 확인
            if (!roomRegister.getRoom(roomName)) {
                // 방이 없으면 새로 생성
                log.info("새로운 방 생성: {}", roomName);
                room = roomRegister.createRoom(roomName);
            } else {
                // 방이 있으면 기존 방 가져오기
                room = roomRegister.getRoomByName(roomName);
            }

            // 세션 ID로 사용자 존재 여부 확인
            if (!userRegister.exists(session.getId())) {
                // 사용자가 없으면 새로 생성
                log.info("새로운 사용자 생성: {}", userName);

                WebRtcEndpoint outgoingMedia = new WebRtcEndpoint.Builder(room.getPipeline()).build();
                UserSession user = new UserSession(userName, session, roomName, outgoingMedia, messageSender);
                userRegister.register(user);
                // Room의 addParticipant 메서드를 사용하여 유저 추가 //Room클래스에서 해당방 유저 상태 관리
                room.addParticipant(userName, user);
                // 기존 참가자들에게 알림
                room.notifyNewParticipantToRoom(userName);
                // 새 참가자에게 기존 참가자 목록 전송
                room.sendExistingParticipantsToUser(userName);

            } else {
                // 기존 사용자가 있는 경우
                log.info("기존 사용자 조회: {}", userName);
                userRegister.getBySession(session.getId());
            }
        }






    }
        




    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        UserSession user = userRegister.getBySession(session.getId());
        if (user != null) {
            log.info("사용자 {} 연결 종료", user.getName());

            // 방에서 참가자 제거 및 다른 참가자들에게 알림
            Room room = roomRegister.getRoomByName(user.getRoomName());
            if (room != null) {
                room.removeParticipant(user.getName());

                // 방이 비었으면 방도 제거
                if (room.getParticipants().isEmpty()) {
                    roomRegister.removeRoom(room.getRoomName());
                }
            }

            // WebRTC 리소스 정리
            if (user.getOutgoingMedia() != null) {
                user.getOutgoingMedia().release();
            }
            user.getIncomingMedia().values().forEach(WebRtcEndpoint::release);

            // UserRegister에서 사용자 제거
            userRegister.removeBySession(session.getId());
        }
    }

}


