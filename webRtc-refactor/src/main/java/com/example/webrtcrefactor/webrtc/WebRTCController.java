package com.example.webrtcrefactor.webrtc;

import com.example.webrtcrefactor.webrtc.dto.request.IceCandidateRequest;
import com.example.webrtcrefactor.webrtc.dto.request.SdpOfferRequest;
import com.example.webrtcrefactor.webrtc.dto.response.SdpAnswerResponse;
import com.example.webrtcrefactor.webrtc.service.WebRTCService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@RequiredArgsConstructor
public class WebRTCController extends TextWebSocketHandler {
    private final WebRTCService webRTCService;

    public SdpAnswerResponse processSdpOffer(WebSocketSession session, SdpOfferRequest request) {
        return webRTCService.processSdpOffer(session.getId(), request);
    }

    public void addIceCandidate(WebSocketSession session, IceCandidateRequest request) {
        webRTCService.addIceCandidate(session.getId(), request);
    }

    public void stopSession(WebSocketSession session) {
        webRTCService.stopSession(session.getId());
    }
}