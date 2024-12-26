// WebSocket 연결 설정
const ws = new WebSocket('ws://' + location.host + '/webrtc');
const participants = {};  // 참가자 목록
let myName;              // 내 이름
let roomName;           // 방 이름
const screenShares = {};       // 화면 공유 목록
let isScreenSharing = false;   // 화면 공유 상태


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
        case 'receiveScreenAnswer':
            screenShares[msg.name].rtcPeer.processAnswer(msg.sdpAnswer);
            break;
        case 'iceCandidate':
            if (msg.type === 'screen') {
                screenShares[msg.name].rtcPeer.addIceCandidate(msg.candidate);
            } else {
                participants[msg.name].rtcPeer.addIceCandidate(msg.candidate);
            }
            break;

        case 'newScreenShareStarted':
            handleNewScreenShare(msg.name);
            break;
        case 'screenShareEnded':
            handleScreenShareEnded(msg.name);
            break;




    }
}

  //화면공유
async function toggleScreenShare() {
    const shareButton = document.getElementById('button-share');

    if (!isScreenSharing) {
        try {
            const stream = await navigator.mediaDevices.getDisplayMedia({
                video: true,
                audio: {
                    echoCancellation: true,
                    noiseSuppression: true,
                    sampleRate: 44100
                }
            });

            // 화면 공유 중단 이벤트 처리
            stream.getVideoTracks()[0].addEventListener('ended', () => {
                stopScreenShare();
            });

            const screenParticipant = new Participant(myName, 'screen');
            screenShares[myName] = screenParticipant;

            const options = {
                localVideo: screenParticipant.getVideoElement(),
                videoStream: stream,  // 화면 공유 스트림 직접 전달
                onicecandidate: (candidate) => {
                    sendMessage({
                        id: 'onIceCandidate',
                        candidate: candidate,
                        name: myName,
                        type: 'screen'
                    });
                }
            };

            screenParticipant.rtcPeer = new WebRTCPeer(options);
            screenParticipant.rtcPeer.generateOffer((error, offerSdp) => {
                if (error) {
                    console.error(error);
                    return;
                }
                sendMessage({
                    id: 'presentScreen',
                    name: myName,
                    sdpOffer: offerSdp
                });
            });

            isScreenSharing = true;
            shareButton.textContent = 'Stop Sharing';

        } catch (e) {
            console.error('Error starting screen share:', e);
        }
    } else {
        stopScreenShare();
    }
}
function stopScreenShare() {
    if (screenShares[myName]) {
        sendMessage({
            id: 'stopScreenShare',
            name: myName
        });
        screenShares[myName].dispose();
        delete screenShares[myName];
    }
    isScreenSharing = false;
    document.getElementById('button-share').textContent = 'Share Screen';
}

function handleNewScreenShare(name) {
    console.log('New screen share from:', name);
    const screenParticipant = new Participant(name, 'screen');
    screenShares[name] = screenParticipant;

    const options = {
        remoteVideo: screenParticipant.getVideoElement(),
        onicecandidate: (candidate) => {
            sendMessage({
                id: 'onIceCandidate',
                candidate: candidate,
                name: name,
                type: 'screen'
            });
        }
    };

    screenParticipant.rtcPeer = new WebRTCPeer(options);
    screenParticipant.rtcPeer.generateOffer((error, offerSdp) => {
        if (error) {
            console.error(error);
            return;
        }
        console.log('Sending screen offer to:', name);
        sendMessage({
            id: 'receiveScreenFrom',
            sender: name,
            sdpOffer: offerSdp
        });
    });
}


function handleScreenShareEnded(name) {
    if (screenShares[name]) {
        screenShares[name].dispose();
        delete screenShares[name];
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
    console.log('New participant:', name);
    const participant = new Participant(name);
    participants[name] = participant;

    const options = {
        remoteVideo: participant.getVideoElement(),
        onicecandidate: (candidate) => {
            sendMessage({
                id: 'onIceCandidate',
                candidate: candidate,
                name: name,
                type: 'video'
            });
        }
    };

    participant.rtcPeer = new WebRTCPeer(options);
    participant.rtcPeer.generateOffer((error, offerSdp) => {
        if (error) {
            console.error(error);
            return;
        }
        console.log('Sending video offer to:', name);
        sendMessage({
            id: 'receiveVideoFrom',
            sender: name,
            sdpOffer: offerSdp
        });
    });
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