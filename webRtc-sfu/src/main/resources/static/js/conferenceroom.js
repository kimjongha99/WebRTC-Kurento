var ws = new WebSocket('ws://' + location.host + '/webrtc');
var participants = {};
var userId;
let screenShareRtcPeer; // 발표자의 화면 공유용
let screenShareViewerRtcPeer; // 시청자의 화면 공유 시청용

window.onbeforeunload = function() {
    ws.close();
};

ws.onmessage = function(message) {
    var parsedMessage = JSON.parse(message.data);
    console.info('Received message: ' + message.data);

    switch (parsedMessage.id) {
        case 'existingParticipants':
            onExistingParticipants(parsedMessage);
            break;
        case 'newParticipantArrived':
            onNewParticipant(parsedMessage);
            break;
        case 'participantLeft':
            onParticipantLeft(parsedMessage);
            break;
        case 'receiveVideoAnswer':
            receiveVideoResponse(parsedMessage);
            break;
        case 'receiveIceCandidate':
            participants[parsedMessage.receiverId].rtcPeer.addIceCandidate(
                parsedMessage.candidate,
                function (error) {
                    if (error) {
                        console.error("ICE 후보 추가 중 오류:", error);
                        return;
                    }
                }
            );
            break;
        case 'sendIceCandidate':  // 추가된 부분
            participants[parsedMessage.senderId].rtcPeer.addIceCandidate(
                parsedMessage.candidate,
                function (error) {
                    if (error) {
                        console.error("ICE 후보 추가 중 오류:", error);
                        return;
                    }
                }
            );
            break;
        case 'screenShareStarted':
            handleScreenShareStarted(parsedMessage);
            break;
        case 'screenShareStopped':
            handleScreenShareStopped();
            break;
        case 'screenShareAnswer':
            handleScreenShareAnswer(parsedMessage);
            break;
        case 'screenIceCandidate':
            handleScreenIceCandidate(parsedMessage);
            break;
        default:
            console.error('Unrecognized message', parsedMessage);
    }
}


function onNewParticipant(request) {
    console.log('새로운 참가자 도착:', request.newUserId);

    // 새 참가자의 비디오 스트림을 받기 위한 WebRTC 연결 설정
    receiveVideo(request.newUserId);
}

function receiveVideoResponse(result) {
    if (!participants[result.userId]) {
        console.error("SDP 응답 처리 중 참가자를 찾을 수 없습니다:", result.userId);
        return;
    }

    participants[result.userId].rtcPeer.processAnswer(result.sdpAnswer, function (error) {
        if (error) {
            console.error("SDP 응답 처리 중 오류:", error);
            return;
        }
        console.log("SDP 응답 처리 완료:", result.userId);
    });
}

function register() {
    userId = document.getElementById('userId').value; // String으로 사용
    var roomId = document.getElementById('roomId').value; // String으로 사용

    document.getElementById('room-header').innerText = '방 ' + roomId;
    document.getElementById('join').style.display = 'none';
    document.getElementById('room').style.display = 'block';

    var message = {
        id: 'joinRoom',
        userId: userId,
        roomId: roomId
    };
    sendMessage(message);
}

function receiveVideo(senderId) {
    console.log('Receiving video from ' + senderId);
    var participant = new Participant(senderId);
    participants[senderId] = participant;
    var video = participant.getVideoElement();

    var options = {
        remoteVideo: video,
        onicecandidate: participant.onIceCandidate.bind(participant),
        onconnectionstatechange: (e) => {console.log(e)},
    }

    participant.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
        function (error) {
            if (error) {
                console.error("WebRTC 피어 생성 중 오류:", error);
                return;
            }
            this.generateOffer(participant.offerToReceiveVideo.bind(participant));
        });
}

function onExistingParticipants(msg) {
    var constraints = {
        audio: true,
        video: {
            mandatory: {
                maxWidth: 320,
                maxFrameRate: 15,
                minFrameRate: 15
            }
        }
    };

    var participant = new Participant(userId);
    participants[userId] = participant;
    var video = participant.getVideoElement();

    var options = {
        localVideo: video,
        mediaConstraints: constraints,
        onicecandidate: participant.onIceCandidate.bind(participant)
    }
    participant.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
        function (error) {
            if (error) console.error("WebRTC 연결 생성 중 오류:", error);
            this.generateOffer(participant.offerToReceiveVideo.bind(participant));
        });

    msg.attendees.forEach(receiveVideo);
}

function leaveRoom() {
    sendMessage({ id: 'leaveRoom' });

    for (var key in participants) {
        participants[key].dispose();
    }

    document.getElementById('join').style.display = 'block';
    document.getElementById('room').style.display = 'none';

    ws.close();
}

function onParticipantLeft(request) {
    var participant = participants[request.leftUserId];
    participant.dispose();
    delete participants[request.leftUserId];
}

function sendMessage(message) {
    var jsonMessage = JSON.stringify(message);
    console.info('송신된 JSON 메시지:', jsonMessage);
    ws.send(jsonMessage);
}




function startScreenShare() {
    if (screenShareRtcPeer) {
        return;
    }

    var container = document.createElement('div');
    container.className = 'participant';
    container.id = 'screen-share';

    var video = document.createElement('video');
    video.id = 'video-screen-share';
    video.autoplay = true;
    container.appendChild(video);

    document.getElementById('screen-share-container').appendChild(container);

    navigator.mediaDevices.getDisplayMedia()
        .then(stream => {
            var options = {
                videoStream: stream,
                localVideo: video,
                onicecandidate: function(candidate) {
                    var message = {
                        id: 'screenIceCandidate',
                        candidate: candidate,
                        name: name
                    };
                    sendMessage(message);
                }
            }

            screenShareRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options, function(error) {
                if (error) return console.error(error);
                this.generateOffer((error, offerSdp) => {
                    if (error) return console.error(error);
                    var message = {
                        id: 'startScreenShare',
                        name: name,
                        sdpOffer: offerSdp
                    };
                    sendMessage(message);
                });
            });

            document.getElementById('button-share').style.display = 'none';
            document.getElementById('button-stop-share').style.display = 'inline';

            stream.getVideoTracks()[0].onended = () => {
                stopScreenShare();
            };
        })
        .catch(error => console.log('Could not get screen sharing'));
}

function stopScreenShare() {
    if (screenShareRtcPeer) {
        var message = {
            id: 'stopScreenShare'
        };
        sendMessage(message);
        screenShareRtcPeer.dispose();
        screenShareRtcPeer = null;

        var container = document.getElementById('screen-share');
        if (container) {
            container.remove();
        }

        document.getElementById('button-share').style.display = 'inline';
        document.getElementById('button-stop-share').style.display = 'none';
    }
}

function handleScreenShareStarted(message) {
    receiveScreenShare(message.presenterName);
}

function handleScreenShareStopped() {
    if (screenShareViewerRtcPeer) {
        screenShareViewerRtcPeer.dispose();
        screenShareViewerRtcPeer = null;
    }
    var screenVideo = document.querySelector('.screen-share');
    if (screenVideo) {
        screenVideo.remove();
    }
}

function handleScreenShareAnswer(message) {
    if (screenShareRtcPeer) {
        screenShareRtcPeer.processAnswer(message.sdpAnswer);
    } else if (screenShareViewerRtcPeer) {
        screenShareViewerRtcPeer.processAnswer(message.sdpAnswer);
    }
}

function handleScreenIceCandidate(message) {
    if (screenShareRtcPeer) {
        screenShareRtcPeer.addIceCandidate(message.candidate);
    } else if (screenShareViewerRtcPeer) {
        screenShareViewerRtcPeer.addIceCandidate(message.candidate);
    }
}

function receiveScreenShare(presenterName) {
    var container = document.createElement('div');
    container.className = 'participant screen-share';

    var video = document.createElement('video');
    video.id = 'video-screen-share';
    video.autoplay = true;
    container.appendChild(video);

    document.getElementById('participants').appendChild(container);

    var options = {
        remoteVideo: video,
        onicecandidate: function(candidate) {
            var message = {
                id: 'screenIceCandidate',
                candidate: candidate,
                name: presenterName
            };
            sendMessage(message);
        }
    }

    screenShareViewerRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
        function(error) {
            if (error) return console.error(error);
            this.generateOffer((error, offerSdp) => {
                if (error) return console.error(error);
                var message = {
                    id: 'receiveScreenShare',
                    name: presenterName,
                    sdpOffer: offerSdp
                };
                sendMessage(message);
            });
        });
}
