const ws = new WebSocket('ws://' + location.host + '/webrtc');
let webRtcPeer;

// Basic UI elements
let localVideo, remoteVideo;

window.onload = () => {
    localVideo = document.getElementById('localVideo');
    remoteVideo = document.getElementById('remoteVideo');
    console.log('Page loaded');
};

window.onbeforeunload = () => {
    ws.close();
};

// WebSocket message handling
ws.onmessage = (message) => {
    const jsonMessage = JSON.parse(message.data);
    console.log('Received:', jsonMessage);

    switch (jsonMessage.id) {
        case 'PROCESS_SDP_ANSWER':
            webRtcPeer.processAnswer(jsonMessage.sdpAnswer);
            break;
        case 'ADD_ICE_CANDIDATE':
            webRtcPeer.addIceCandidate(jsonMessage.candidate);
            break;
        case 'ERROR':
            stop();
            console.error(jsonMessage.message);
            break;
    }
};

// UI Actions
function start() {
    if (webRtcPeer) {
        return;
    }

    const options = {
        localVideo: localVideo,
        remoteVideo: remoteVideo,
        onicecandidate: (candidate) => {
            console.log('Local candidate:', candidate);
            send({
                id: 'ADD_ICE_CANDIDATE',
                candidate: candidate
            });
        },
        mediaConstraints: {
            audio: true,
            video: true
        }
    };

    webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options, function(error) {
        if (error) {
            stop();
            return console.error(error);
        }
        this.generateOffer((error, sdpOffer) => {
            if (error) {
                stop();
                return console.error(error);
            }
            send({
                id: 'PROCESS_SDP_OFFER',
                sdpOffer: sdpOffer
            });
        });
    });
}

function stop() {
    if (webRtcPeer) {
        webRtcPeer.dispose();
        webRtcPeer = null;
    }
    send({
        id: 'STOP'
    });
}

function send(message) {
    console.log('Sending id : => ', message.id);
    const jsonMessage = JSON.stringify(message);
    ws.send(jsonMessage);
}
