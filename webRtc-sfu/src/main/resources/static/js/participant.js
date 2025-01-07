function Participant(userId) {
    this.userId = userId;
    this.rtcPeer = null;

    this.getVideoElement = function() {
        var video = document.createElement('video');
        video.autoplay = true;
        video.playsInline = true;
        var div = document.createElement('div');
        div.className = 'participant';
        div.setAttribute('id', 'participant_' + userId);
        div.appendChild(video);
        div.appendChild(document.createElement('br'));
        div.appendChild(document.createTextNode('참가자 ' + userId));
        document.getElementById('participants').appendChild(div);
        return video;
    }

    this.offerToReceiveVideo = function(error, offerSdp) {
        if (error) {
            console.error("SDP Offer 생성 중 오류:", error);
            return;
        }
        var msg = {
            id: "receiveVideoOffer",  // 서버 코드와 일치
            senderId: userId,         // 서버 코드와 일치
            sdpOffer: offerSdp
        };
        sendMessage(msg);
    }

    this.onIceCandidate = function(candidate) {
        var message = {
            id: 'sendIceCandidate',   // 서버 코드와 일치
            senderId: userId,         // 서버 코드와 일치
            candidate: candidate
        };
        sendMessage(message);
    }

    this.dispose = function() {
        console.log('Disposing participant ' + userId);
        this.rtcPeer.dispose();
        var div = document.getElementById('participant_' + userId);
        div.parentNode.removeChild(div);
    }
}