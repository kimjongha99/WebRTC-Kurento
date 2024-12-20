let peerConnection;
const ws = new WebSocket('ws://' + location.host + '/webrtc');
let localStream;

// Configuration for STUN/TURN servers if needed
const configuration = {
    iceServers: [
        { urls: 'stun:stun.l.google.com:19302' }
    ]
};

window.onload = () => {
    window.localVideo = document.getElementById('localVideo');
    window.remoteVideo = document.getElementById('remoteVideo');
    console.log('Page loaded');
};

window.onbeforeunload = () => {
    stop();
    ws.close();
};

ws.onmessage = async (message) => {
    const jsonMessage = JSON.parse(message.data);
    console.log('Received message:', jsonMessage);

    switch (jsonMessage.id) {
        case 'PROCESS_SDP_ANSWER':
            try {
                await peerConnection.setRemoteDescription(
                    new RTCSessionDescription({
                        type: 'answer',
                        sdp: jsonMessage.sdpAnswer
                    })
                );
            } catch (e) {
                console.error('Error setting remote description:', e);
            }
            break;

        case 'ADD_ICE_CANDIDATE':
            try {
                await peerConnection.addIceCandidate(jsonMessage.candidate);
            } catch (e) {
                console.error('Error adding ice candidate:', e);
            }
            break;

        case 'ERROR':
            console.error(jsonMessage.message);
            stop();
            break;
    }
};

async function start() {
    if (peerConnection) {
        console.warn('Already started');
        return;
    }

    try {
        // Get user media
        localStream = await navigator.mediaDevices.getUserMedia({
            audio: true,
            video: true
        });

        // Show local video
        window.localVideo.srcObject = localStream;

        // Create peer connection
        peerConnection = new RTCPeerConnection(configuration);

        // Add local stream tracks to peer connection
        localStream.getTracks().forEach(track => {
            peerConnection.addTrack(track, localStream);
        });

        // Handle remote stream
        peerConnection.ontrack = (event) => {
            if (event.streams && event.streams[0]) {
                window.remoteVideo.srcObject = event.streams[0];
            }
        };

        // Handle ICE candidates
        peerConnection.onicecandidate = (event) => {
            if (event.candidate) {
                send({
                    id: 'ADD_ICE_CANDIDATE',
                    candidate: event.candidate
                });
            }
        };

        // Create and send offer
        const offer = await peerConnection.createOffer();
        await peerConnection.setLocalDescription(offer);

        send({
            id: 'PROCESS_SDP_OFFER',
            sdpOffer: offer.sdp
        });

    } catch (e) {
        console.error('Error starting WebRTC:', e);
        stop();
    }
}

function stop() {
    if (localStream) {
        localStream.getTracks().forEach(track => track.stop());
        localStream = null;
    }

    if (peerConnection) {
        peerConnection.close();
        peerConnection = null;
    }

    if (window.localVideo) window.localVideo.srcObject = null;
    if (window.remoteVideo) window.remoteVideo.srcObject = null;

    send({ id: 'STOP' });
}

function send(message) {
    console.log('Sending message:', message);
    ws.send(JSON.stringify(message));
}