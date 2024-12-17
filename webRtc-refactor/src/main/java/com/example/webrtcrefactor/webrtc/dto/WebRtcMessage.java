package com.example.webrtcrefactor.webrtc.dto;

import lombok.Data;
import org.kurento.client.IceCandidate;

@Data
public class WebRtcMessage {
    private String id;
    private String sdpOffer;
    private IceCandidate candidate;
    private String message;
}