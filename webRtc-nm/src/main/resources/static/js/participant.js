class Participant {
    constructor(name) {
        this.name = name;
        this.peerConnection = null;

        // Create DOM elements
        this.container = document.createElement('div');
        this.container.className = document.getElementsByClassName('participant main').length === 0 ?
            'participant main' : 'participant';
        this.container.id = name;

        this.video = document.createElement('video');
        this.video.id = 'video-' + name;
        this.video.autoplay = true;
        this.video.playsInline = true;  // iOS 지원을 위해 추가

        const span = document.createElement('span');
        span.appendChild(document.createTextNode(name));

        this.container.appendChild(this.video);
        this.container.appendChild(span);
        this.container.onclick = this.switchContainerClass.bind(this);

        document.getElementById('participants').appendChild(this.container);
    }

    async initializeConnection(isLocal = false) {
        const configuration = {
            iceServers: [
                { urls: 'stun:stun.l.google.com:19302' },
                { urls: 'stun:stun1.l.google.com:19302' }
            ]
        };

        this.peerConnection = new RTCPeerConnection(configuration);

        // ICE 후보 이벤트 처리
        this.peerConnection.onicecandidate = ({candidate}) => {
            if (candidate) {
                sendMessage({
                    id: 'onIceCandidate',
                    candidate: candidate,
                    name: this.name
                });
            }
        };

        // 연결 상태 모니터링
        this.peerConnection.onconnectionstatechange = () => {
            console.log(`${this.name} connection state:`, this.peerConnection.connectionState);
        };

        if (isLocal) {
            try {
                const stream = await navigator.mediaDevices.getUserMedia({
                    audio: true,
                    video: {
                        width: { ideal: 320 },
                        height: { ideal: 240 },
                        frameRate: { ideal: 15, max: 15 }
                    }
                });
                this.video.srcObject = stream;
                stream.getTracks().forEach(track => {
                    console.log('Adding local track:', track.kind);
                    this.peerConnection.addTrack(track, stream);
                });
            } catch (e) {
                console.error('Error accessing media devices:', e);
            }
        } else {
            // 원격 스트림 처리 개선
            this.peerConnection.ontrack = (event) => {
                console.log('Received remote track:', event.track.kind);
                if (event.streams && event.streams[0]) {
                    this.video.srcObject = event.streams[0];
                } else {
                    // 스트림이 없는 경우 새로운 MediaStream 생성
                    let stream = this.video.srcObject;
                    if (!stream) {
                        stream = new MediaStream();
                        this.video.srcObject = stream;
                    }
                    stream.addTrack(event.track);
                }

                // 비디오가 로드되면 자동 재생
                this.video.onloadedmetadata = () => {
                    this.video.play().catch(e => console.error('Error auto-playing video:', e));
                };
            };
        }
    }

    async createOffer() {
        try {
            const offer = await this.peerConnection.createOffer();
            await this.peerConnection.setLocalDescription(offer);
            sendMessage({
                id: 'receiveVideoFrom',
                sender: this.name,
                sdpOffer: offer.sdp
            });
        } catch (e) {
            console.error('Error creating offer:', e);
        }
    }

    async processAnswer(sdpAnswer) {
        try {
            const answer = new RTCSessionDescription({
                type: 'answer',
                sdp: sdpAnswer
            });
            await this.peerConnection.setRemoteDescription(answer);
            console.log(`${this.name} processAnswer completed`);
        } catch (e) {
            console.error('Error processing answer:', e);
        }
    }

    async addIceCandidate(candidate) {
        if (this.peerConnection && this.peerConnection.remoteDescription) {
            try {
                await this.peerConnection.addIceCandidate(candidate);
            } catch (e) {
                console.error('Error adding ICE candidate:', e);
            }
        } else {
            console.warn('Delayed ICE candidate - no remote description');
        }
    }

    switchContainerClass() {
        if (this.container.className === 'participant') {
            document.querySelectorAll('.participant.main').forEach(item => {
                item.className = 'participant';
            });
            this.container.className = 'participant main';
        } else {
            this.container.className = 'participant';
        }
    }

    dispose() {
        if (this.peerConnection) {
            this.peerConnection.ontrack = null;
            this.peerConnection.onicecandidate = null;
            this.peerConnection.onconnectionstatechange = null;
            this.peerConnection.close();

            if (this.video.srcObject) {
                this.video.srcObject.getTracks().forEach(track => track.stop());
                this.video.srcObject = null;
            }
        }
        this.container.remove();
    }
}