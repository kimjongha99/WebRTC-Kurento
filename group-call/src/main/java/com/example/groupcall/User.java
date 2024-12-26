package com.example.groupcall;


import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.kurento.client.Continuation;
import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.IceCandidateFoundEvent;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonObject;

public class User {
    private static final Logger log = LoggerFactory.getLogger(User.class);

    // Static user registry (formerly UserRegistry)
    private static final ConcurrentHashMap<String, User> usersByName = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, User> usersBySessionId = new ConcurrentHashMap<>();

    // User instance fields
    private final String name;
    private final String roomName;
    private final WebSocketSession session;
    private final WebRtcEndpoint outgoingMedia;
    private final ConcurrentHashMap<String, WebRtcEndpoint> incomingMedia = new ConcurrentHashMap<>();

    // Static methods for user registry
    public static void register(User user) {
        usersByName.put(user.getName(), user);
        usersBySessionId.put(user.getSession().getId(), user);
    }

    public static User getByName(String name) {
        return usersByName.get(name);
    }

    public static User getBySession(WebSocketSession session) {
        return usersBySessionId.get(session.getId());
    }

    public static User removeBySession(WebSocketSession session) {
        User user = getBySession(session);
        if (user != null) {
            usersByName.remove(user.getName());
            usersBySessionId.remove(session.getId());
        }
        return user;
    }

    public User(String name, String roomName, WebSocketSession session, MediaPipeline pipeline) {
        this.name = name;
        this.roomName = roomName;
        this.session = session;

        log.info("WebRTC 엔드포인트 생성 - 사용자: {}, 룸: {}", name, roomName);
        this.outgoingMedia = new WebRtcEndpoint.Builder(pipeline).build();
        log.info("outgoing WebRTC 엔드포인트 생성됨 - 사용자: {}, 룸: {}, EndpointId: {}",
                name, roomName, outgoingMedia.getId());

        this.outgoingMedia.addIceCandidateFoundListener(event -> {
            log.debug("ICE 후보 발견 - 사용자: {}, 룸: {}, 후보: {}",
                    name, roomName, event.getCandidate());

            JsonObject response = new JsonObject();
            response.addProperty("id", "iceCandidate");
            response.addProperty("name", name);
            response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
            sendMessage(response);
        });

        // Register the user in the static registry
        register(this);
    }

    public void receiveVideoFrom(User sender, String sdpOffer) throws Exception {
        String senderName = sender.getName();
        WebRtcEndpoint incoming = getOrCreateWebRtcEndpoint(sender);
        String sdpAnswer = incoming.processOffer(sdpOffer);

        JsonObject response = new JsonObject();
        response.addProperty("id", "receiveVideoAnswer");
        response.addProperty("name", senderName);
        response.addProperty("sdpAnswer", sdpAnswer);
        sendMessage(response);

        incoming.gatherCandidates();
    }

    private WebRtcEndpoint getOrCreateWebRtcEndpoint(User sender) {
        String senderName = sender.getName();

        if (senderName.equals(this.name)) {
            return outgoingMedia;
        }

        WebRtcEndpoint incoming = incomingMedia.get(senderName);
        if (incoming == null) {
            incoming = createNewWebRtcEndpoint(sender);
        }

        return incoming;
    }

    private WebRtcEndpoint createNewWebRtcEndpoint(User sender) {
        log.info("수신 WebRTC 엔드포인트 생성 - 보낸 사람: {}, 받는 사람: {}, 방: {}",
                sender.getName(), this.name, this.roomName);

        WebRtcEndpoint incoming = new WebRtcEndpoint.Builder(outgoingMedia.getMediaPipeline()).build();
        log.info("수신 WebRTC incoming 생성 - 보낸 사람: {}, 받는 사람: {}, 방: {}, EndpointId: {}",
                sender.getName(), this.name, this.roomName, incoming.getId());

        incoming.addIceCandidateFoundListener(event -> {
            JsonObject response = new JsonObject();
            response.addProperty("id", "iceCandidate");
            response.addProperty("name", sender.getName());
            response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
            sendMessage(response);
        });

        incomingMedia.put(sender.getName(), incoming);
        sender.getOutgoingWebRtcPeer().connect(incoming);

        return incoming;
    }

    public void cancelVideoFrom(String senderName) {
        WebRtcEndpoint incoming = incomingMedia.remove(senderName);
        if (incoming != null) {
            log.info("수신 WebRTC 엔드포인트 해제 - 보낸 사람: {}, 받는 사람: {}, 방: {}, EndpointId: {}",
                    senderName, this.name, this.roomName, incoming.getId());
            incoming.release();
        }
    }

    public void addCandidate(IceCandidate candidate, String senderName) {
        if (this.name.equals(senderName)) {
            outgoingMedia.addIceCandidate(candidate);
        } else {
            WebRtcEndpoint webRtc = incomingMedia.get(senderName);
            if (webRtc != null) {
                webRtc.addIceCandidate(candidate);
            }
        }
    }

    public void sendMessage(JsonObject message) {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(message.toString()));
            }
        } catch (Exception e) {
            log.error("Error sending message", e);
        }
    }

    public void close() {
        log.info("UserSession 닫기 - 사용자: {}, 회의실: {}", name, roomName);

        // 수신 엔드포인트 정리
        incomingMedia.forEach((senderName, endpoint) -> {
            log.info("수신 WebRTC 엔드포인트 해제 - 보낸 사람: {}, 받는 사람: {}, 방: {}, EndpointId: {}",
                    senderName, this.name, this.roomName, endpoint.getId());
            endpoint.release();
        });
        incomingMedia.clear();

        // 송신 엔드포인트 정리
        log.info("발신 WebRTC 엔드포인트 해제 - 사용자: {}, Room: {}, EndpointId: {}",
                name, roomName, outgoingMedia.getId());
        outgoingMedia.release();

        // Remove from registry
        usersByName.remove(this.name);
        usersBySessionId.remove(this.session.getId());

        log.info("사용자: {}, 방: {}에 대한 모든 리소스를 닫았습니다.", name, roomName);
    }

    // Getters
    public String getName() { return name; }
    public WebSocketSession getSession() { return session; }
    public String getRoomName() { return roomName; }
    public WebRtcEndpoint getOutgoingWebRtcPeer() { return outgoingMedia; }
}