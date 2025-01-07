package com.example.webrtcsfu;


import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.kurento.client.IceCandidate;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

public class MessageSender {
    private static final Logger log = LoggerFactory.getLogger(MessageSender.class);

    /**
     * 기본 메시지 전송 메서드
     */
    public void sendMessage(WebSocketSession session, String userName, JsonObject message) throws IOException {
        log.debug("USER {}: Sending message {}", userName, message);
        synchronized (session) {
            session.sendMessage(new TextMessage(message.toString()));
        }
    }

    /**
     * 새 참가자 입장 메시지 (기존 유저에게 전송)
     */
    public void sendNewParticipantArrived(UserSession participant, String newParticipantName) {
        JsonObject message = new JsonObject();
        message.addProperty("id", "newParticipantArrived");
        message.addProperty("newUserId", newParticipantName);
        try {
            sendMessage(participant.getSession(), participant.getName(), message);
        } catch (IOException e) {
            log.error("Error sending new participant message to user: " + participant.getName(), e);
        }
    }

    /**
     * 기존 참가자 목록 메시지 ( 새로들어온 유저에게 전송)
     */
    public void sendExistingParticipants(UserSession participant, JsonArray attendees) {
        JsonObject message = new JsonObject();
        message.addProperty("id", "existingParticipants");
        message.add("attendees", attendees);
        try {
            sendMessage(participant.getSession(), participant.getName(), message);
        } catch (IOException e) {
            log.error("Error sending existing participants message to user: " + participant.getName(), e);
        }
    }


    public void sendParticipantLeft(WebSocketSession session, String userName, String leftUserId) {
        JsonObject message = new JsonObject();
        message.addProperty("id", "participantLeft");
        message.addProperty("leftUserId", leftUserId);
        try {
            sendMessage(session, userName, message);
        } catch (IOException e) {
            log.error("Error sending participant left message to user: " + userName, e);
        }
    }
    /**
     * ICE Candidate 메시지 전송 메서드
     */
    public void sendIceCandidate(WebSocketSession session, String userName, IceCandidate candidate) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("id", "receiveIceCandidate");
        response.addProperty("receiverId", userName);
        response.add("candidate", JsonUtils.toJsonObject(candidate));
        sendMessage(session, userName, response);
    }
    /**
     * 비디오 응답 메시지 전송 메서드
     */
    public void sendVideoAnswer(UserSession participant, String senderName, String sdpAnswer) {
        JsonObject message = new JsonObject();
        message.addProperty("id", "receiveVideoAnswer");
        message.addProperty("userId", senderName);
        message.addProperty("sdpAnswer", sdpAnswer);

        try {
            sendMessage(participant.getSession(), participant.getName(), message);
        } catch (IOException e) {
            log.error("Error sending video answer message to user: " + participant.getName(), e);
        }
    }
    public void sendScreenShareStarted(UserSession viewer, String presenterName) {
        JsonObject message = new JsonObject();
        message.addProperty("id", "screenShareStarted");
        message.addProperty("presenterName", presenterName);
        try {
            sendMessage(viewer.getSession(), viewer.getName(), message);
        } catch (IOException e) {
            log.error("Error sending screen share started message", e);
        }
    }

    public void sendScreenShareAnswer(UserSession viewer, String sdpAnswer) {
        JsonObject message = new JsonObject();
        message.addProperty("id", "screenShareAnswer");
        message.addProperty("sdpAnswer", sdpAnswer);
        try {
            sendMessage(viewer.getSession(), viewer.getName(), message);
        } catch (IOException e) {
            log.error("Error sending screen share answer", e);
        }
    }
    public void sendScreenIceCandidate(WebSocketSession session, IceCandidate candidate) throws IOException {
        JsonObject message = new JsonObject();
        message.addProperty("id", "screenIceCandidate");
        message.add("candidate", JsonUtils.toJsonObject(candidate));
        sendMessage(session, "screen", message);
    }
    public void sendScreenShareStopped(UserSession viewer) {
        JsonObject message = new JsonObject();
        message.addProperty("id", "screenShareStopped");
        try {
            sendMessage(viewer.getSession(), viewer.getName(), message);
        } catch (IOException e) {
            log.error("Error sending screen share stopped message", e);
        }
    }

}