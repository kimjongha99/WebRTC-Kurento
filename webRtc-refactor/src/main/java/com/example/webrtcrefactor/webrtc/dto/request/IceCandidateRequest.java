package com.example.webrtcrefactor.webrtc.dto.request;

import lombok.Setter;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class IceCandidateRequest {
    private String candidate;
    private String sdpMid;
    private Integer sdpMLineIndex;
}