let ws = null;
let currentUser = null;
let currentRoom = null;
let localStream = null;

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
    }
}

function onExistingParticipants(userList) {
    // UI 전환
    document.getElementById('roomTitle').textContent = currentRoom;
    document.getElementById('userTitle').textContent = currentUser;
    hideLoginContainer();
    showVideoRoom();

    // 로컬 비디오 추가
    addVideoElement(currentUser, true);

    // 기존 참가자들의 비디오 컨테이너 추가
    userList.forEach(userName => {
        if (userName !== currentUser) {
            addVideoElement(userName, false);
        }
    });
}

function onNewParticipant(userName) {
    console.log('New participant:', userName);
    addVideoElement(userName, false);
}

function onParticipantLeft(userName) {
    console.log('Participant left:', userName);
    removeVideoElement(userName);
}

function addVideoElement(userName, isLocal) {
    const videoContainer = document.getElementById('videoContainer');

    const videoBox = document.createElement('div');
    videoBox.className = 'video-box';
    videoBox.id = `video-box-${userName}`;

    const video = document.createElement('video');
    video.id = `video-${userName}`;
    video.autoplay = true;
    video.playsInline = true;
    if (isLocal) {
        video.muted = true;
        video.srcObject = localStream;
    }

    const label = document.createElement('div');
    label.className = 'video-label';
    label.textContent = userName + (isLocal ? ' (You)' : '');

    videoBox.appendChild(video);
    videoBox.appendChild(label);
    videoContainer.appendChild(videoBox);
}

function removeVideoElement(userName) {
    const videoBox = document.getElementById(`video-box-${userName}`);
    if (videoBox) {
        videoBox.remove();
    }
}

function cleanup() {
    if (localStream) {
        localStream.getTracks().forEach(track => track.stop());
        localStream = null;
    }
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