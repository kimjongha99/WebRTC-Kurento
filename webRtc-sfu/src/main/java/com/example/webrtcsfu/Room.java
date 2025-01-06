package com.example.webrtcsfu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Getter;
import org.kurento.client.MediaPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

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
    // participants에 새로운 참가자를 추가하는 메서드
    public void addParticipant(String userName, UserSession user) {
        participants.put(userName, user);
    }


    /**
     * 방의 기존 참가자들에게 새로운 참가자 입장을 알림
     */
    public void notifyNewParticipantToRoom(String newUserName) {
        log.debug("Room {}: notifying other participants about new user {}", roomName, newUserName);

        // 기존 참가자들에게 새 참가자 알림 전송
        participants.forEach((participantName, participant) -> {
            // 새로 들어온 참가자 제외
            if (!participantName.equals(newUserName)) {
                messageSender.sendNewParticipantArrived(
                        participant.getSession(),
                        participant.getName(),
                        newUserName
                );
            }
        });
    }


    // 클라이언트로 보내는 소켓메세지 전송( 비동기적적으로 처리되어야함) , 새로입장한 유저에게 전송으로 처리되어야함) , 새로입장한 유저에게 전송
    /**
     * 새로 입장한 참가자에게 기존 참가자 목록 전송
     */
    public void sendExistingParticipantsToUser(String userName) {
        log.debug("Room {}: sending existing participants list to {}", roomName, userName);

        UserSession newUser = participants.get(userName);
        if (newUser != null) {
            // 기존 참가자 목록 생성 (자신 제외)
            JsonArray participantsList = new JsonArray();
            participants.forEach((participantName, participant) -> {
                if (!participantName.equals(userName)) {
                    participantsList.add(participantName);
                }
            });

            // 새 참가자에게 전송
            messageSender.sendExistingParticipants(
                    newUser.getSession(),
                    userName,
                    participantsList
            );
        }
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

}