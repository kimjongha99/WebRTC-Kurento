package com.example.webrtcnm;


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

public class UserSession implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(UserSession.class);
    private final String name;
    private final WebSocketSession session;
    private final String roomName;
    private final WebRtcEndpoint outgoingMedia;
    private final ConcurrentHashMap<String, WebRtcEndpoint> incomingMedia = new ConcurrentHashMap<>();

    public UserSession(String name, String roomName, WebSocketSession session, MediaPipeline pipeline) {
        this.name = name;
        this.session = session;
        this.roomName = roomName;
        this.outgoingMedia = new WebRtcEndpoint.Builder(pipeline).build();
        this.outgoingMedia.addIceCandidateFoundListener(event -> {
            JsonObject response = new JsonObject();
            response.addProperty("id", "iceCandidate");
            response.addProperty("name", name);
            response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
            sendMessage(response);
        });
    }

    public void receiveVideoFrom(UserSession sender, String sdpOffer) throws IOException {
        String ipSdpAnswer = getEndpointForUser(sender).processOffer(sdpOffer);
        JsonObject answer = new JsonObject();
        answer.addProperty("id", "receiveVideoAnswer");
        answer.addProperty("name", sender.getName());
        answer.addProperty("sdpAnswer", ipSdpAnswer);
        sendMessage(answer);
        getEndpointForUser(sender).gatherCandidates();
    }

    private WebRtcEndpoint getEndpointForUser(UserSession sender) {
        if (sender.getName().equals(name)) {
            return outgoingMedia;
        }

        WebRtcEndpoint incoming = incomingMedia.get(sender.getName());
        if (incoming == null) {
            incoming = new WebRtcEndpoint.Builder(sender.outgoingMedia.getMediaPipeline()).build();
            incoming.addIceCandidateFoundListener(event -> {
                JsonObject response = new JsonObject();
                response.addProperty("id", "iceCandidate");
                response.addProperty("name", sender.getName());
                response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
                sendMessage(response);
            });
            incomingMedia.put(sender.getName(), incoming);
            sender.getOutgoingWebRtcPeer().connect(incoming);
        }
        return incoming;
    }

    public void cancelVideoFrom(String senderName) {
        WebRtcEndpoint incoming = incomingMedia.remove(senderName);
        if (incoming != null) {
            incoming.release();
        }
    }

    public void addCandidate(IceCandidate candidate, String name) {
        if (this.name.equals(name)) {
            outgoingMedia.addIceCandidate(candidate);
        } else {
            WebRtcEndpoint endpoint = incomingMedia.get(name);
            if (endpoint != null) {
                endpoint.addIceCandidate(candidate);
            }
        }
    }

    public void sendMessage(JsonObject message) {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(message.toString()));
            }
        } catch (IOException e) {
            log.error("Error sending message to user {}: {}", name, e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        incomingMedia.values().forEach(WebRtcEndpoint::release);
        outgoingMedia.release();
    }

    public String getName() { return name; }
    public WebSocketSession getSession() { return session; }
    public String getRoomName() { return roomName; }
    public WebRtcEndpoint getOutgoingWebRtcPeer() { return outgoingMedia; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof UserSession)) return false;
        UserSession other = (UserSession) obj;
        return name.equals(other.name) && roomName.equals(other.roomName);
    }

    @Override
    public int hashCode() {
        return 31 * name.hashCode() + roomName.hashCode();
    }
}