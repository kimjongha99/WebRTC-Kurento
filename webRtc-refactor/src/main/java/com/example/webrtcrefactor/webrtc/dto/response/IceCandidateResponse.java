package com.example.webrtcrefactor.webrtc.dto.response;


import lombok.AllArgsConstructor;
import lombok.Getter;
import org.kurento.client.IceCandidate;

@Getter
@AllArgsConstructor
public class IceCandidateResponse {
    private IceCandidate candidate;
}
