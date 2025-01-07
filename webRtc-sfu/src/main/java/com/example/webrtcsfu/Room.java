package com.example.webrtcsfu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import org.kurento.client.MediaPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 하나의 방을 나타내는 클래스
 * 방 참가자들과 미디어 파이프라인을 관리
 */
@Getter
public class Room {
    private static final Logger log = LoggerFactory.getLogger(Room.class);

    // 방 이름
    private final String roomName;
    // Getter 메서드 명시적 추가
    // 방의 미디어 파이프라인
    private final MediaPipeline pipeline;
    // 방 참가자들을 관리하는 맵 (유저 이름 -> 유저 세션)
    private final ConcurrentHashMap<String, UserSession> participants = new ConcurrentHashMap<>();

    private final MessageSender messageSender;

    public Room(String roomName, MediaPipeline pipeline, MessageSender messageSender) {
        this.roomName = roomName;
        this.pipeline = pipeline;
        this.messageSender = messageSender;
    }

    public UserSession join(String userName, WebSocketSession session) throws IOException {
        UserSession participant = new UserSession(userName, this.roomName, session, this.pipeline,messageSender);
        notifyNewParticipantToRoom(participant);
        participants.put(participant.getName(), participant); // room클래스에서 참가자 상태관리.추후 리소스 정리 필요.
        sendExistingParticipantsToUser(participant);
        return participant;
    }


    /**
     * 방의 기존 참가자들에게 새로운 참가자 입장을 알림
     */
    public void notifyNewParticipantToRoom(UserSession newParticipant) {
        log.debug("룸 {}: 다른 참가자에게 새 사용자 {}에 대해 알립니다.", roomName, newParticipant.getName());

        // 기존 참가자들에게 새 참가자 알림 전송
        participants.forEach((participantName, participant) -> {
            // 새로 들어온 참가자 제외
            if (!participantName.equals(newParticipant.getName())) {
                messageSender.sendNewParticipantArrived(
                        participant,  // UserSession 객체 전달
                        newParticipant.getName()
                );
            }
        });
    }

    /**
     * 새로 입장한 참가자에게 기존 참가자 목록 전송
     */
    public void sendExistingParticipantsToUser(UserSession newParticipant) {
        log.debug("Room {}: sending existing participants list to {}", roomName, newParticipant.getName());

        // 기존 참가자 목록 생성 (자신 제외)
        JsonArray participantsList = new JsonArray();
        participants.forEach((participantName, participant) -> {
            if (!participantName.equals(newParticipant.getName())) {
                participantsList.add(participantName);
            }
        });

        // 새 참가자에게 전송
        messageSender.sendExistingParticipants(
                newParticipant,  // UserSession 객체 전달
                participantsList
        );
    }



    public void removeParticipant(String userName) {
        log.debug("Room {}: removing participant {}", roomName, userName);
        participants.remove(userName);

        // 남아있는 참가자들에게 알림
        participants.forEach((participantName, participant) -> {
            messageSender.sendParticipantLeft(
                    participant.getSession(),
                    participant.getName(),
                    userName
            );
        });
    }

    public void leave(UserSession user) throws IOException {
        try {
            // 1. 다른 참가자들에게 알림
            removeParticipant(user.getName());

            // 2. 사용자의 WebRTC 리소스 정리
            user.close();

            // 3. participants 맵에서 제거
            participants.remove(user.getName());

            log.info("사용자 {} 방 {} 퇴장 처리 완료", user.getName(), this.roomName);
        } catch (Exception e) {
            log.error("사용자 퇴장 처리 중 오류 발생: {}", user.getName(), e);
            throw e;
        }
    }
}