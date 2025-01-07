package com.example.webrtcsfu;

import com.google.gson.JsonObject;
import lombok.Getter;
import org.kurento.client.*;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 한 명의 유저 세션을 나타내는 클래스
 * 유저의 WebRTC 연결과 미디어 스트림을 관리
 */
@Getter
public class UserSession implements Closeable  {
    private static final Logger log = LoggerFactory.getLogger(UserSession.class);

    private final MessageSender messageSender;  // MessageSender 추가

    // 유저 이름
    private final String name;
    // WebSocket 세션
    private final WebSocketSession session;

    private final MediaPipeline pipeline;

    // 참여중인 방 이름
    private final String roomName;


    // 유저가 송신하는 미디어를 위한 WebRTC 엔드포인트
    private final WebRtcEndpoint outgoingMedia;


    // 다른 참가자들로부터 수신하는 미디어를 위한 WebRTC 엔드포인트들
    // 키: 송신자의 이름, 값: WebRTC 엔드포인트
    private final ConcurrentHashMap<String, WebRtcEndpoint> incomingMedia = new ConcurrentHashMap<>();

    public UserSession(String name, String roomName,WebSocketSession session,
                       MediaPipeline pipeline, MessageSender messageSender) {

        this.pipeline = pipeline;
        this.name = name;
        this.session = session;
        this.roomName = roomName;
        this.outgoingMedia = new WebRtcEndpoint.Builder(pipeline).build();

        this.messageSender = messageSender;


        this.outgoingMedia.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {
            @Override
            public void onEvent(IceCandidateFoundEvent event) {
                try {
                    messageSender.sendIceCandidate(session, name, event.getCandidate());
                } catch (IOException e) {
                    log.error("Error sending ICE candidate: {}", e.getMessage());
                }
            }
        });
    }


    //비디오데이터 전송
    public void receiveVideoFrom(UserSession sender, String sdpOffer) throws IOException {
        String ipSdpAnswer = this.getEndpointForUser(sender).processOffer(sdpOffer);
        messageSender.sendVideoAnswer(this, sender.getName(), ipSdpAnswer);
        this.getEndpointForUser(sender).gatherCandidates();

    }



    //getEndpointForUser -> 해당유저의 엔드포인트를 가져온다.
    private WebRtcEndpoint getEndpointForUser(UserSession sender) {
        if (sender.getName().equals(name)) {
            return outgoingMedia; //전송자가있으면 바로 outgoingMedia 전송완료
        }
        WebRtcEndpoint incoming = incomingMedia.get(sender.getName());
        if (incoming == null) {
            incoming = new WebRtcEndpoint.Builder(pipeline).build();


            incoming.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

                @Override
                public void onEvent(IceCandidateFoundEvent event) {
                    try {
                        messageSender.sendIceCandidate(session, name, event.getCandidate());
                    } catch (IOException e) {
                        log.error("Error sending ICE candidate: {}", e.getMessage());
                    }
                }

            });
            incomingMedia.put(sender.getName(), incoming);
        }
        sender.getOutgoingWebRtcPeer().connect(incoming);

        return incoming;

    }

    public WebRtcEndpoint getOutgoingWebRtcPeer() {
        return outgoingMedia;
    }

    /**
     * 송신/수신 스트림에 대한 적절한 candidate 할당
     * @param candidate
     * @param name
     */
    public void addCandidate(IceCandidate candidate, String name) {
        if (this.name.compareTo(name) == 0) { //현재 사용자의 이름과 전달받은 이름이 같은지 비교
            outgoingMedia.addIceCandidate(candidate); //자신의 outgoing(송신) 미디어 엔드포인트에 candidate 추가 ,자신이 다른 사용자에게 보내는 스트림을 위한 연결 정보
        } else {
            WebRtcEndpoint webRtc = incomingMedia.get(name);
            if (webRtc != null) {
                webRtc.addIceCandidate(candidate);
            }
        }
    }


    @Override
    public void close() throws IOException {
        log.debug("PARTICIPANT {}: Releasing resources", this.name);

        // 1. incoming 미디어 엔드포인트 정리
        for (final String remoteParticipantName : incomingMedia.keySet()) {
            final WebRtcEndpoint ep = this.incomingMedia.get(remoteParticipantName);
            if (ep != null) {
                ep.release(new Continuation<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        log.trace("PARTICIPANT {}: Released successfully incoming EP for {}",
                                name, remoteParticipantName);
                    }

                    @Override
                    public void onError(Throwable cause) {
                        log.warn("PARTICIPANT {}: Could not release incoming EP for {}",
                                name, remoteParticipantName);
                    }
                });
            }
        }

        // 2. outgoing 미디어 엔드포인트 정리
        if (outgoingMedia != null) {
            outgoingMedia.release(new Continuation<Void>() {
                @Override
                public void onSuccess(Void result) {
                    log.trace("PARTICIPANT {}: Released outgoing EP", name);
                }

                @Override
                public void onError(Throwable cause) {
                    log.warn("PARTICIPANT {}: Could not release outgoing EP", name);
                }
            });
        }

        // 3. 모든 수신 미디어 맵 정리
        incomingMedia.clear();
    }

}


