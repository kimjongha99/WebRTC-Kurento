function Participant(name) {
    this.name = name;
    this.rtcPeer = null;

    // 기본 요소 생성
    const container = document.createElement('div');
    const video = document.createElement('video');
    const nameLabel = document.createElement('span');

    // 비디오 설정
    video.id = 'video-' + name;
    video.autoplay = true;
    video.controls = false;
    video.style.width = '300px';  // 기본 비디오 크기 설정

    // 이름 표시
    nameLabel.textContent = name;

    // 요소 조립 및 추가
    container.appendChild(video);
    container.appendChild(nameLabel);
    document.getElementById('participants').appendChild(container);

    // WebRTC 관련 필수 메서드들
    this.getVideoElement = function() {
        return video;
    };

    this.offerToReceiveVideo = function(error, offerSdp) {
        if (error) return;

        sendMessage({
            id: "receiveVideoFrom",
            sender: name,
            sdpOffer: offerSdp
        });
    };

    this.onIceCandidate = function(candidate) {
        sendMessage({
            id: 'onIceCandidate',
            candidate: candidate,
            name: name
        });
    };

    // 참가자 퇴장 처리
    this.dispose = function() {
        if (this.rtcPeer) {
            this.rtcPeer.dispose();
        }
        container.remove();
    };
}