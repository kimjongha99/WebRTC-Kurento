

const ws = new WebSocket('ws://' + location.host + '/helloworld');

let webRtcPeer;

// UI

// UI 상태 관리
let uiLocalVideo;
let uiRemoteVideo;
let uiState = null;
const UI_IDLE = 0;      // 대기 상태
const UI_STARTING = 1;   // 시작 중
const UI_STARTED = 2;    // 실행 중

// head 태그에 추가할 CSS 스타일
const style = document.createElement('style');
style.textContent = `
@keyframes loading {
    0% { background-position: -50% 0; }
    100% { background-position: 150% 0; }
}
`;
document.head.appendChild(style);


class Console {
    constructor() {
        this.console = document.getElementById('console');
    }

    log(text) {
        this.console.innerHTML += text + '<br>';
        this.console.scrollTop = this.console.scrollHeight;
    }

    error(text) {
        this.console.innerHTML += '<span style="color: red;">' + text + '</span><br>';
        this.console.scrollTop = this.console.scrollHeight;
    }

    info(text) {
        this.console.innerHTML += '<span style="color: blue;">' + text + '</span><br>';
        this.console.scrollTop = this.console.scrollHeight;
    }
}



// 페이지 로드 시 초기화
window.onload = function() {
    console = new Console();
    console.log("페이지 로드 완료");
    uiLocalVideo = document.getElementById('uiLocalVideo');
    uiRemoteVideo = document.getElementById('uiRemoteVideo');
    uiSetState(UI_IDLE);
}


// 페이지 언로드 시 정리
window.onbeforeunload = function() {
    console.log("페이지 종료 - WebSocket 연결 종료");
    ws.close();
}



// 미디어 에러 메시지 설명
function explainUserMediaError(err) {
    const n = err.name;
    if (n === 'NotFoundError' || n === 'DevicesNotFoundError') {
        return "웹캠을 찾을 수 없습니다";
    }
    else if (n === 'NotReadableError' || n === 'TrackStartError') {
        return "웹캠이 이미 사용 중입니다";
    }
    else if (n === 'OverconstrainedError' || n === 'ConstraintNotSatisfiedError') {
        return "웹캠이 필요한 기능을 제공하지 않습니다";
    }
    else if (n === 'NotAllowedError' || n === 'PermissionDeniedError') {
        return "웹캠 접근 권한이 거부되었습니다";
    }
    else if (n === 'TypeError') {
        return "미디어 트랙이 요청되지 않았습니다";
    }
    else {
        return "알 수 없는 오류: " + err;
    }
}

// 메시지 전송 관련 함수
function sendError(message) {
    console.error(message);
    sendMessage({
        id: 'ERROR',
        message: message,
    });
}


function sendMessage(message) {
    if (ws.readyState !== ws.OPEN) {
        console.warn("WebSocket 연결이 열려있지 않아 메시지를 전송할 수 없습니다");
        return;
    }
    const jsonMessage = JSON.stringify(message);
    console.log("메시지 전송: " + jsonMessage);
    ws.send(jsonMessage);
}



/* ============================= */
/* ==== WebSocket signaling ==== */
/* ============================= */

// WebSocket 메시지 처리
ws.onmessage = function(message) {
    const jsonMessage = JSON.parse(message.data);
    console.log("메시지 수신: " + message.data);

    switch (jsonMessage.id) {
        case 'PROCESS_SDP_ANSWER':
            handleProcessSdpAnswer(jsonMessage);
            break;
        case 'ADD_ICE_CANDIDATE':
            handleAddIceCandidate(jsonMessage);
            break;
        case 'ERROR':
            handleError(jsonMessage);
            break;
        default:
            console.warn("잘못된 메시지 ID: " + jsonMessage.id);
            break;
    }
}

// SDP 응답 처리
function handleProcessSdpAnswer(jsonMessage) {
    console.log("Kurento로부터 SDP 응답 수신, WebRTC Peer에서 처리");

    if (webRtcPeer == null) {
        console.warn("WebRTC Peer가 없어 처리를 건너뜁니다");
        return;
    }

    webRtcPeer.processAnswer(jsonMessage.sdpAnswer, (err) => {
        if (err) {
            sendError("SDP 응답 처리 중 오류: " + err);
            stop();
            return;
        }

        console.log("SDP 응답 처리 완료; 원격 비디오 시작");
        startVideo(uiRemoteVideo);
        uiSetState(UI_STARTED);
    });
}


// ADD_ICE_CANDIDATE -----------------------------------------------------------

// ICE 후보 처리
function handleAddIceCandidate(jsonMessage) {
    if (webRtcPeer == null) {
        console.warn("WebRTC Peer가 없어 ICE 후보 추가를 건너뜁니다");
        return;
    }

    webRtcPeer.addIceCandidate(jsonMessage.candidate, (err) => {
        if (err) {
            console.error("ICE 후보 추가 중 오류: " + err);
            return;
        }
    });
}


//  ------------------------------------------------------------------------
// 중지 처리
function stop() {
    if (uiState == UI_IDLE) {
        console.log("이미 중지된 상태입니다");
        return;
    }

    console.log("중지");

    if (webRtcPeer) {
        webRtcPeer.dispose();
        webRtcPeer = null;
    }

    uiSetState(UI_IDLE);
    hideSpinner(uiLocalVideo, uiRemoteVideo);

    sendMessage({
        id: 'STOP'
    });
}

// 에러 처리
function handleError(jsonMessage) {
    const errMessage = jsonMessage.message;
    console.error("Kurento 오류: " + errMessage);
    console.log("오류 발생으로 인한 중지...");
    stop();
}


/* ==================== */
/* ==== UI actions ==== */
/* ==================== */

// Start -----------------------------------------------------------------------
// UI 동작 처리
function uiStart() {
    console.log("WebRTC Peer 생성 시작");
    uiSetState(UI_STARTING);
    showSpinner(uiLocalVideo, uiRemoteVideo);

    const options = {
        localVideo: uiLocalVideo,
        remoteVideo: uiRemoteVideo,
        mediaConstraints: { audio: true, video: true },
        onicecandidate: (candidate) => sendMessage({
            id: 'ADD_ICE_CANDIDATE',
            candidate: candidate,
        }),
    };

    webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
        function(err) {
            if (err) {
                sendError("WebRTC Peer 생성 중 오류: " + explainUserMediaError(err));
                stop();
                return;
            }

            console.log("WebRTC Peer 생성됨; 로컬 비디오 시작");
            startVideo(uiLocalVideo);

            console.log("SDP Offer 생성");
            webRtcPeer.generateOffer((err, sdpOffer) => {
                if (err) {
                    sendError("SDP Offer 생성 중 오류: " + err);
                    stop();
                    return;
                }

                sendMessage({
                    id: 'PROCESS_SDP_OFFER',
                    sdpOffer: sdpOffer,
                });

                console.log("SDP Offer 생성 완료!");
                uiSetState(UI_STARTED);
            });
        });
}

function uiStop() {
    stop();
}

// -----------------------------------------------------------------------------



/* ================== */
/* ==== UI state ==== */
/* ================== */
// UI 상태 관리
function uiSetState(newState) {
    switch (newState) {
        case UI_IDLE:
            uiEnableElement('#uiStartBtn', 'uiStart()');
            uiDisableElement('#uiStopBtn');
            break;
        case UI_STARTING:
            uiDisableElement('#uiStartBtn');
            uiDisableElement('#uiStopBtn');
            break;
        case UI_STARTED:
            uiDisableElement('#uiStartBtn');
            uiEnableElement('#uiStopBtn', 'uiStop()');
            break;
        default:
            console.warn("잘못된 상태값: " + newState);
            return;
    }
    uiState = newState;
}



// UI 요소 제어
function uiEnableElement(id, onclickHandler) {
    $(id).attr('disabled', false);
    if (onclickHandler) {
        $(id).attr('onclick', onclickHandler);
    }
}

function uiDisableElement(id) {
    $(id).attr('disabled', true);
    $(id).removeAttr('onclick');
}



// 로딩 스피너 처리
function showSpinner() {
    for (let i = 0; i < arguments.length; i++) {
        arguments[i].poster = '';
        arguments[i].style.background = `
           #f0f0f0 
           linear-gradient(
               90deg,
               transparent,
               rgba(0,0,0,0.1),
               transparent
           ) 
           center/20% 100% 
           no-repeat`;
        arguments[i].style.animation = 'loading 1.5s infinite';
    }
}


function hideSpinner() {
    for (let i = 0; i < arguments.length; i++) {
        arguments[i].src = '';
        arguments[i].poster = 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIiB2aWV3Qm94PSIwIDAgMTAwIDEwMCI+PHJlY3Qgd2lkdGg9IjEwMCIgaGVpZ2h0PSIxMDAiIGZpbGw9IiNlMGUwZTAiLz48dGV4dCB4PSI1MCIgeT0iNTAiIGZvbnQtZmFtaWx5PSJBcmlhbCIgZm9udC1zaXplPSIxNCIgZmlsbD0iIzY2NiIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZHk9Ii4zZW0iPldlYlJUQzwvdGV4dD48L3N2Zz4=';
        arguments[i].style.background = '';
        arguments[i].style.animation = '';
    }
}

// 비디오 시작
function startVideo(video) {
    video.play().catch((err) => {
        if (err.name === 'NotAllowedError') {
            console.error("브라우저가 비디오 재생을 허용하지 않음: " + err);
        }
        else {
            console.error("비디오 재생 중 오류: " + err);
        }
    });
}

/**
 * Lightbox utility (to display media pipeline image in a modal dialog)
 */
$(document).delegate('*[data-toggle="lightbox"]', 'click', function(event) {
    event.preventDefault();
    $(this).ekkoLightbox();
});