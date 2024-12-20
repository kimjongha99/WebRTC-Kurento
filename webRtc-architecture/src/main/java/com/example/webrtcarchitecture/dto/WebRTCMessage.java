package com.example.webrtcarchitecture.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonObject;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebRTCMessage {
    private String id;
    private String sdpOffer;
    private String sdpAnswer;
    private JsonNode candidate;
}