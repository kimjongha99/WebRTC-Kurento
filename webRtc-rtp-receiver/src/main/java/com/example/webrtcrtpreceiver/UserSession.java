package com.example.webrtcrtpreceiver;

import org.kurento.client.MediaPipeline;
import org.kurento.client.RtpEndpoint;
import org.kurento.client.WebRtcEndpoint;

public class UserSession {
    private MediaPipeline mediaPipeline;
    private RtpEndpoint rtpEp;
    private WebRtcEndpoint webRtcEp;

    public UserSession()
    {}

    public MediaPipeline getMediaPipeline()
    {
        return mediaPipeline;
    }

    public void setMediaPipeline(MediaPipeline mediaPipeline)
    {
        this.mediaPipeline = mediaPipeline;
    }

    public RtpEndpoint getRtpEndpoint()
    {
        return rtpEp;
    }

    public void setRtpEndpoint(RtpEndpoint rtpEp)
    {
        this.rtpEp = rtpEp;
    }

    public WebRtcEndpoint getWebRtcEndpoint()
    {
        return webRtcEp;
    }

    public void setWebRtcEndpoint(WebRtcEndpoint webRtcEp)
    {
        this.webRtcEp = webRtcEp;
    }
}