let ws = null;
let currentUser = null;
let currentRoom = null;
let localStream = null;
let participants = {};

async function joinRoom() {
    const userName = document.getElementById('userName').value;
    const roomName = document.getElementById('roomName').value;

    if (!userName || !roomName) {
        alert('이름과 방 이름을 모두 입력하세요.');
        return;
    }

    currentUser = userName;
    currentRoom = roomName;

    try {
        // 로컬 미디어 스트림 가져오기
        localStream = await navigator.mediaDevices.getUserMedia({
            audio: true,
            video: true
        });

        // WebSocket 연결
        ws = new WebSocket('ws://' + window.location.host + '/webrtc');

        ws.onopen = () => {
            console.log('WebSocket 연결됨');
            sendMessage({
                id: 'joinRoom',
                userName: userName,
                roomName: roomName
            });
        };

        ws.onmessage = (event) => {
            const message = JSON.parse(event.data);
            console.log('서버로부터 메시지 수신:', message);
            handleMessage(message);
        };

        ws.onclose = () => {
            console.log('WebSocket 연결 종료');
            cleanup();
        };

        ws.onerror = (error) => {
            console.error('WebSocket 에러:', error);
            cleanup();
        };

    } catch (error) {
        console.error('미디어 스트림 또는 WebSocket 연결 오류:', error);
        alert('카메라/마이크 접근 권한을 확인해주세요.');
    }
}

function handleMessage(message) {
    switch (message.id) {
        case 'existingParticipants':
            onExistingParticipants(message.attendees);
            break;
        case 'newParticipantArrived':
            onNewParticipant(message.newUserId);
            break;
        case 'participantLeft':
            onParticipantLeft(message.leftUserId);
            break;
        case 'receiveVideoAnswer':
            onReceiveVideoAnswer(message);
            break;
        case 'iceCandidate':
            onIceCandidate(message);
            break;
    }
}

function onIceCandidate(message) {
    const participant = participants[message.name];
    if (participant) {
        participant.addIceCandidate(message.candidate)
            .catch(error => console.error('Error adding ICE candidate:', error));
    }
}

function onReceiveVideoAnswer(message) {
    console.log('Received video answer from:', message.userId);
    const webRtcPeer = participants[message.userId];

    if (webRtcPeer) {
        webRtcPeer.processAnswer(message.sdpAnswer)
            .catch(error => console.error('Error processing answer:', error));
    }
}

function onExistingParticipants(userIds) {
    console.log('Existing participants:', userIds);

    // 로컬 비디오 설정
    const videoContainer = document.getElementById('videoContainer');
    const localVideo = document.createElement('video');
    localVideo.id = 'local-video';
    localVideo.autoplay = true;
    localVideo.playsInline = true;
    videoContainer.appendChild(localVideo);

    // 로컬 WebRTC Peer 생성
    const localPeer = new WebRTCPeer({
        localVideo: localVideo,
        onicecandidate: candidate => {
            sendMessage({
                id: 'onIceCandidate',
                candidate: candidate,
                name: currentUser
            });
        }
    });

    participants[currentUser] = localPeer;

    // 로컬 peer에 대한 offer 생성
    localPeer.generateOffer()
        .then(sdpOffer => {
            console.log('Sending local video offer');
            sendMessage({
                id: 'receiveVideoOffer',
                sender: currentUser,
                sdpOffer: sdpOffer
            });
        })
        .catch(error => console.error('Error generating local offer:', error));

    // 기존 참가자들에 대한 WebRTC 연결 설정
    userIds.forEach(userId => {
        if (userId !== currentUser) {
            receiveVideo(userId);
        }
    });

    hideLoginContainer();
    showVideoRoom();
}

function receiveVideo(userId) {
    console.log('Receiving video from:', userId);

    // 원격 비디오 엘리먼트 생성
    const videoContainer = document.getElementById('videoContainer');
    const remoteVideo = document.createElement('video');
    remoteVideo.id = `video-${userId}`;
    remoteVideo.autoplay = true;
    remoteVideo.playsInline = true;
    videoContainer.appendChild(remoteVideo);

    // WebRTC Peer 생성
    const webRtcPeer = new WebRTCPeer({
        remoteVideo: remoteVideo,
        onicecandidate: candidate => {
            sendMessage({
                id: 'onIceCandidate',
                candidate: candidate,
                name: userId
            });
        }
    });

    participants[userId] = webRtcPeer;

    // Offer 생성 및 전송
    webRtcPeer.generateOffer()
        .then(sdpOffer => {
            console.log('Sending video offer to:', userId);
            sendMessage({
                id: 'receiveVideoOffer',
                sender: currentUser,
                sdpOffer: sdpOffer
            });
        })
        .catch(error => console.error('Error generating offer:', error));
}

function onNewParticipant(userId) {
    console.log('New participant arrived:', userId);
    receiveVideo(userId);
}

function onParticipantLeft(userId) {
    console.log('Participant left:', userId);
    if (participants[userId]) {
        participants[userId].dispose();
        delete participants[userId];
    }
    removeVideoElement(userId);
}

function removeVideoElement(userId) {
    const video = document.getElementById(`video-${userId}`);
    if (video) {
        video.parentElement.removeChild(video);
    }
}

function cleanup() {
    if (localStream) {
        localStream.getTracks().forEach(track => track.stop());
        localStream = null;
    }

    Object.values(participants).forEach(participant => {
        if (participant) {
            participant.dispose();
        }
    });
    participants = {};

    showLoginContainer();
    hideVideoRoom();
    document.getElementById('videoContainer').innerHTML = '';
    currentUser = null;
    currentRoom = null;
}

function leaveRoom() {
    if (ws) {
        ws.close();
    }
    cleanup();
}

function sendMessage(message) {
    const jsonMessage = JSON.stringify(message);
    console.log('서버로 메시지 전송:', jsonMessage);
    ws.send(jsonMessage);
}

function hideLoginContainer() {
    document.getElementById('loginContainer').classList.add('hidden');
}

function showLoginContainer() {
    document.getElementById('loginContainer').classList.remove('hidden');
}

function showVideoRoom() {
    document.getElementById('videoRoom').classList.remove('hidden');
}

function hideVideoRoom() {
    document.getElementById('videoRoom').classList.add('hidden');
}