function Participant(name, type = 'video') {
    this.name = name;
    this.rtcPeer = null;

    // 기본 요소 생성
    const container = document.createElement('div');
    container.className = type === 'screen' ? 'screen-share-container' : 'video-container';
    const video = document.createElement('video');
    const nameLabel = document.createElement('span');

    // 비디오 설정
    video.id = `${type}-${name}`;  // ID를 타입별로 구분
    video.autoplay = true;
    video.controls = false;

    // 타입에 따른 스타일 설정
    if (type === 'screen') {
        video.style.width = '640px';
        container.style.backgroundColor = '#f0f0f0';
        nameLabel.textContent = `${name}'s Screen`;
    } else {
        video.style.width = '300px';
        nameLabel.textContent = name;
    }

    // 요소 조립 및 추가
    container.appendChild(video);
    container.appendChild(nameLabel);

    // 타입에 따라 다른 컨테이너에 추가
    const targetContainer = type === 'screen' ?
        document.getElementById('screen-shares') :
        document.getElementById('participants');
    targetContainer.appendChild(container);

    this.getVideoElement = function() {
        return video;
    };

    this.offerToReceiveVideo = function(error, offerSdp) {
        if (error) {
            console.error(error);
            return;
        }

        console.log(`Sending ${type} offer for ${name}`);
        sendMessage({
            id: type === 'screen' ? 'receiveScreenFrom' : 'receiveVideoFrom',
            sender: name,
            sdpOffer: offerSdp
        });
    };

    this.onIceCandidate = function(candidate) {
        console.log(`Sending ICE candidate for ${type} - ${name}`);
        sendMessage({
            id: 'onIceCandidate',
            candidate: candidate,
            name: name,
            type: type
        });
    };

    this.dispose = function() {
        console.log(`Disposing ${type} for ${name}`);
        if (this.rtcPeer) {
            this.rtcPeer.dispose();
        }
        container.remove();
    };
}