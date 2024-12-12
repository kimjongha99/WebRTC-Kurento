const ws = new WebSocket('ws://' + location.host + '/helloworld');
let webRtcPeer;
let uiLocalVideo;
let uiRemoteVideo;

window.onload = function() {
    uiLocalVideo = document.getElementById('uiLocalVideo');
    uiRemoteVideo = document.getElementById('uiRemoteVideo');
    document.getElementById('uiStartBtn').onclick = start;
    document.getElementById('uiStopBtn').onclick = stop;
    document.getElementById('uiStopBtn').disabled = true;
}

window.onbeforeunload = function() {
    ws.close();
}

ws.onmessage = function(message) {
    const jsonMessage = JSON.parse(message.data);
    switch (jsonMessage.id) {
        case 'PROCESS_SDP_ANSWER':
            webRtcPeer.processAnswer(jsonMessage.sdpAnswer);
            break;
        case 'ADD_ICE_CANDIDATE':
            webRtcPeer.addIceCandidate(jsonMessage.candidate);
            break;
        case 'ERROR':
            stop();
            break;
    }
}

function start() {
    document.getElementById('uiStartBtn').disabled = true;
    document.getElementById('uiStopBtn').disabled = false;

    const options = {
        localVideo: uiLocalVideo,
        remoteVideo: uiRemoteVideo,
        mediaConstraints: { audio: true, video: true },
        onicecandidate: (candidate) => sendMessage({
            id: 'ADD_ICE_CANDIDATE',
            candidate: candidate,
        })
    };

    webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
        function(error) {
            if (error) {
                stop();
                return;
            }
            this.generateOffer((error, sdpOffer) => {
                if (error) {
                    stop();
                    return;
                }
                sendMessage({
                    id: 'PROCESS_SDP_OFFER',
                    sdpOffer: sdpOffer
                });
            });
        });
}

function stop() {
    document.getElementById('uiStartBtn').disabled = false;
    document.getElementById('uiStopBtn').disabled = true;
    if (webRtcPeer) {
        webRtcPeer.dispose();
        webRtcPeer = null;
        sendMessage({ id: 'STOP' });
    }
}

function sendMessage(message) {
    if (ws.readyState === ws.OPEN) {
        ws.send(JSON.stringify(message));
    }
}