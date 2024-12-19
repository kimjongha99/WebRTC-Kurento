// OpenVidu 객체
var OV;
var session;

window.onload = () => {
    generateFormValues();
}

function generateFormValues() {
    document.getElementById("room-name").value = "Test Room";
    document.getElementById("participant-name").value = "Participant" + Math.floor(Math.random() * 100);
}

async function joinRoom() {
    document.getElementById("join-button").disabled = true;
    document.getElementById("join-button").innerText = "Joining...";

    const roomName = document.getElementById("room-name").value;
    const userName = document.getElementById("participant-name").value;

    try {
        // 1. OpenVidu 객체 생성
        OV = new OpenVidu();
        session = OV.initSession();

        // 2. 이벤트 핸들러 설정
        session.on('streamCreated', (event) => {
            const subscriber = session.subscribe(event.stream, 'layout-container');
            subscriber.on('videoElementCreated', (event) => {
                const videoContainer = createVideoContainer(event.element, event.stream.connection.data);
                event.element.parentElement.appendChild(videoContainer);
            });
        });

        // 3. 세션 연결
        const token = await getToken(roomName);
        await session.connect(token, { clientData: userName });

        // 4. 자신의 비디오 스트림 발행
        const publisher = await OV.initPublisher('layout-container', {
            audioSource: undefined,
            videoSource: undefined,
            publishAudio: true,
            publishVideo: true,
            resolution: '640x480',
            frameRate: 30,
            insertMode: 'APPEND',
        });

        session.publish(publisher);

        // UI 업데이트
        document.getElementById("room-title").innerText = roomName;
        document.getElementById("join").hidden = true;
        document.getElementById("room").hidden = false;

    } catch (error) {
        console.error('Error:', error);
        document.getElementById("join-button").disabled = false;
        document.getElementById("join-button").innerText = "Join!";
    }
}

function createVideoContainer(videoElement, userData) {
    const container = document.createElement('div');
    container.className = 'video-container';

    const name = document.createElement('div');
    name.className = 'participant-name';
    name.innerText = JSON.parse(userData).clientData;

    container.appendChild(name);
    container.appendChild(videoElement);

    return container;
}

async function getToken(roomName) {
    try {
        const sessionResponse = await fetch('http://localhost:8080/api/sessions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                customSessionId: roomName?.replace(/[^a-zA-Z0-9-_]/g, '_') ?? 'default-session'
            })
        });

        if (!sessionResponse.ok) {
            throw new Error('Failed to create session');
        }

        const sessionId = await sessionResponse.text();
        console.log('Session created:', sessionId);

        const tokenResponse = await fetch(`http://localhost:8080/api/sessions/${sessionId}/connections`, {
            method: 'POST'
        });

        if (!tokenResponse.ok) {
            throw new Error('Failed to generate token');
        }

        const token = await tokenResponse.text();
        console.log('Token received:', token);
        return token;

    } catch (error) {
        console.error('Error getting token:', error);
        return null;
    }
}


async function leaveRoom() {
    if (session) {
        session.disconnect();
    }

    document.getElementById("join").hidden = false;
    document.getElementById("room").hidden = true;
    document.getElementById("join-button").disabled = false;
    document.getElementById("join-button").innerText = "Join!";

    while (document.getElementById("layout-container").firstChild) {
        document.getElementById("layout-container").removeChild(
            document.getElementById("layout-container").firstChild
        );
    }
}

window.onbeforeunload = () => {
    if (session) session.disconnect();
};