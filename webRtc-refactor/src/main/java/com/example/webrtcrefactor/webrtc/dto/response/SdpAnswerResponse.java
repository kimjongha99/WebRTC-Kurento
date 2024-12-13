package com.example.webrtcrefactor.webrtc.dto.response;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SdpAnswerResponse {
    private String sdpAnswer;
}