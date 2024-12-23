// WebSocket 연결 설정
const ws = new WebSocket('ws://' + location.host + '/webrtc');
const participants = {};  // 참가자 목록
let myName;              // 내 이름
let roomName;           // 방 이름

// 웹소켓 메시지 처리
ws.onmessage = function(message) {
    const msg = JSON.parse(message.data);

    switch (msg.id) {
        case 'existingParticipants':    // 방에 입장했을 때
            handleExistingParticipants(msg);
            break;
        case 'newParticipantArrived':   // 새 참가자가 들어왔을 때
            handleNewParticipant(msg.name);
            break;
        case 'participantLeft':         // 참가자가 나갔을 때
            handleParticipantLeft(msg.name);
            break;
        case 'receiveVideoAnswer':      // 비디오 응답 받았을 때
            participants[msg.name].rtcPeer.processAnswer(msg.sdpAnswer);
            break;
        case 'iceCandidate':           // ICE candidate 받았을 때
            participants[msg.name].rtcPeer.addIceCandidate(msg.candidate);
            break;
    }
}

// 방 입장
function register() {
    myName = document.getElementById('name').value;
    roomName = document.getElementById('roomName').value;

    // UI 업데이트
    document.getElementById('room-header').innerText = 'ROOM: ' + roomName;
    document.getElementById('join').style.display = 'none';
    document.getElementById('room').style.display = 'block';

    // 서버에 입장 메시지 전송
    sendMessage({
        id: 'joinRoom',
        name: myName,
        room: roomName,
    });
}

// 기존 참가자 처리
function handleExistingParticipants(msg) {
    // 비디오 설정
    const constraints = {
        audio: true,
        video: {
            maxWidth: 320,
            frameRate: { max: 15, min: 15 }
        }
    };

    // 내 비디오 설정
    const participant = new Participant(myName);
    participants[myName] = participant;

    const options = {
        localVideo: participant.getVideoElement(),
        mediaConstraints: constraints,
        onicecandidate: participant.onIceCandidate.bind(participant)
    }

    // WebRTC 연결 설정
    participant.rtcPeer = new WebRTCPeer(options);
    participant.rtcPeer.generateOffer(participant.offerToReceiveVideo.bind(participant));

    // 기존 참가자들의 비디오 받기
    msg.data.forEach(handleNewParticipant);
}

// 새 참가자 처리
function handleNewParticipant(name) {
    const participant = new Participant(name);
    participants[name] = participant;

    const options = {
        remoteVideo: participant.getVideoElement(),
        onicecandidate: participant.onIceCandidate.bind(participant)
    }

    participant.rtcPeer = new WebRTCPeer(options);
    participant.rtcPeer.generateOffer(participant.offerToReceiveVideo.bind(participant));
}

// 참가자 퇴장 처리
function handleParticipantLeft(name) {
    participants[name].dispose();
    delete participants[name];
}

// 방 나가기
function leaveRoom() {
    sendMessage({ id: 'leaveRoom' });

    // 모든 참가자 정리
    Object.values(participants).forEach(p => p.dispose());

    // UI 초기화
    document.getElementById('join').style.display = 'block';
    document.getElementById('room').style.display = 'none';

    ws.close();
}

// 메시지 전송 헬퍼 함수
function sendMessage(message) {
    ws.send(JSON.stringify(message));
}

// 페이지 닫을 때 웹소켓 정리
window.onbeforeunload = () => ws.close();