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


public class UserSession {
    private final String name;
    private final String roomName;
    private final WebSocketSession session;
    private final WebRtcEndpoint outgoingMedia;
    private final ConcurrentHashMap<String, WebRtcEndpoint> incomingMedia = new ConcurrentHashMap<>();

    public UserSession(String name, String roomName, WebSocketSession session, MediaPipeline pipeline) {
        this.name = name;
        this.roomName = roomName;
        this.session = session;
        this.outgoingMedia = new WebRtcEndpoint.Builder(pipeline).build();

        this.outgoingMedia.addIceCandidateFoundListener(event -> {
            JsonObject response = new JsonObject();
            response.addProperty("id", "iceCandidate");
            response.addProperty("name", name);
            response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
            sendMessage(response);
        });
    }

    public void receiveVideoFrom(UserSession sender, String sdpOffer) throws Exception {
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

    private WebRtcEndpoint getOrCreateWebRtcEndpoint(UserSession sender) {
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

    private WebRtcEndpoint createNewWebRtcEndpoint(UserSession sender) {
        WebRtcEndpoint incoming = new WebRtcEndpoint.Builder(outgoingMedia.getMediaPipeline()).build();

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
            // 오류 처리
        }
    }

    public void close() {
        incomingMedia.values().forEach(WebRtcEndpoint::release);
        outgoingMedia.release();
    }

    // Getters
    public String getName() { return name; }
    public WebSocketSession getSession() { return session; }
    public String getRoomName() { return roomName; }
    public WebRtcEndpoint getOutgoingWebRtcPeer() { return outgoingMedia; }
}