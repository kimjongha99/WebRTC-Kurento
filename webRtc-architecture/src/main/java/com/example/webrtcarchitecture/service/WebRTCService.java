package com.example.webrtcarchitecture.service;

import com.example.webrtcarchitecture.domain.WebRTCSession;
import com.example.webrtcarchitecture.repository.WebRTCSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
@Slf4j
@Service
public class WebRTCService {
    private final WebRTCSessionRepository sessionRepository;
    private final KurentoClient kurentoClient;

    public WebRTCService(KurentoClient kurentoClient) {
        this.sessionRepository = new WebRTCSessionRepository();
        this.kurentoClient = kurentoClient;
    }

    public String handleSdpOffer(String sessionId, String sdpOffer) {
        MediaPipeline pipeline = kurentoClient.createMediaPipeline();
        WebRtcEndpoint webRtcEp = new WebRtcEndpoint.Builder(pipeline).build();
        webRtcEp.connect(webRtcEp);

        WebRTCSession session = new WebRTCSession(sessionId, pipeline, webRtcEp);
        sessionRepository.save(sessionId, session);

        String sdpAnswer = webRtcEp.processOffer(sdpOffer);
        webRtcEp.gatherCandidates();

        return sdpAnswer;
    }

    public void handleIceCandidate(String sessionId, IceCandidate candidate) {
        WebRTCSession session = sessionRepository.findById(sessionId);
        if (session != null) {
            session.getWebRtcEndpoint().addIceCandidate(candidate);
        }
    }

    public void stopSession(String sessionId) {
        WebRTCSession session = sessionRepository.findById(sessionId);
        if (session != null) {
            session.getMediaPipeline().release();
            sessionRepository.deleteById(sessionId);
        }
    }
}