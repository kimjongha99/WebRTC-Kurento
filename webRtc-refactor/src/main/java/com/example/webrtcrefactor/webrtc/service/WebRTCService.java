package com.example.webrtcrefactor.webrtc.service;


import com.example.webrtcrefactor.webrtc.dto.request.IceCandidateRequest;
import com.example.webrtcrefactor.webrtc.dto.request.SdpOfferRequest;
import com.example.webrtcrefactor.webrtc.dto.response.IceCandidateResponse;
import com.example.webrtcrefactor.webrtc.dto.response.SdpAnswerResponse;
import com.example.webrtcrefactor.webrtc.entity.UserSession;
import com.example.webrtcrefactor.webrtc.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WebRTCService {
    private final KurentoClient kurentoClient;
    private final UserSessionRepository userSessionRepository;
    private final WebSocketService webSocketService;

    public SdpAnswerResponse processSdpOffer(String sessionId, SdpOfferRequest request) {
        UserSession userSession = createUserSession(sessionId);
        setupWebRTCEndpoint(userSession, sessionId);
        String sdpAnswer = userSession.getWebRtcEndpoint().processOffer(request.getSdpOffer());
        userSession.getWebRtcEndpoint().gatherCandidates();

        return new SdpAnswerResponse(sdpAnswer);
    }

    public void addIceCandidate(String sessionId, IceCandidateRequest request) {
        UserSession userSession = userSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        IceCandidate candidate = new IceCandidate(
                request.getCandidate(),
                request.getSdpMid(),
                request.getSdpMLineIndex());

        userSession.getWebRtcEndpoint().addIceCandidate(candidate);
    }

    public void stopSession(String sessionId) {
        userSessionRepository.findById(sessionId).ifPresent(userSession -> {
            if (userSession.getMediaPipeline() != null) {
                userSession.getMediaPipeline().release();
            }
            userSessionRepository.deleteById(sessionId);
        });
    }

    private UserSession createUserSession(String sessionId) {
        MediaPipeline pipeline = kurentoClient.createMediaPipeline();
        WebRtcEndpoint webRtcEndpoint = new WebRtcEndpoint.Builder(pipeline).build();

        UserSession userSession = new UserSession();
        userSession.setMediaPipeline(pipeline);
        userSession.setWebRtcEndpoint(webRtcEndpoint);

        userSessionRepository.save(sessionId, userSession);
        return userSession;
    }

    private void setupWebRTCEndpoint(UserSession userSession, String sessionId) {
        WebRtcEndpoint endpoint = userSession.getWebRtcEndpoint();
        endpoint.connect(endpoint);

        endpoint.addIceCandidateFoundListener(event -> {
            IceCandidateResponse response = new IceCandidateResponse(event.getCandidate());
            webSocketService.sendIceCandidate(sessionId, response);
        });
    }
}
