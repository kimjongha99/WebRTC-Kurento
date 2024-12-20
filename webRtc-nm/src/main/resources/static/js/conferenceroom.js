const ws = new WebSocket('ws://' + location.host + '/webrtc');
const participants = new Map();
let name;

window.onbeforeunload = () => ws.close();

ws.onmessage = async ({ data }) => {
    const message = JSON.parse(data);
    console.info('Received message:', message);

    switch (message.id) {
        case 'existingParticipants':
            await handleExistingParticipants(message);
            break;

        case 'newParticipantArrived':
            await handleNewParticipant(message.name);
            break;

        case 'participantLeft':
            handleParticipantLeft(message.name);
            break;

        case 'receiveVideoAnswer':
            await handleVideoAnswer(message);
            break;

        case 'iceCandidate':
            await handleIceCandidate(message);
            break;

        default:
            console.error('Unrecognized message', message);
    }
};

async function handleExistingParticipants(message) {
    // Set up local participant
    const localParticipant = new Participant(name);
    participants.set(name, localParticipant);

    await localParticipant.initializeConnection(true);
    await localParticipant.createOffer();

    // Set up existing participants
    for (const participantName of message.data) {
        await handleNewParticipant(participantName);
    }
}

async function handleNewParticipant(participantName) {
    const participant = new Participant(participantName);
    participants.set(participantName, participant);

    await participant.initializeConnection();
    await participant.createOffer();
}

function handleParticipantLeft(participantName) {
    const participant = participants.get(participantName);
    if (participant) {
        participant.dispose();
        participants.delete(participantName);
    }
}

async function handleVideoAnswer(message) {
    const participant = participants.get(message.name);
    if (participant) {
        await participant.processAnswer(message.sdpAnswer);
    }
}

async function handleIceCandidate(message) {
    const participant = participants.get(message.name);
    if (participant) {
        try {
            await participant.peerConnection.addIceCandidate(message.candidate);
        } catch (e) {
            console.error('Error adding ice candidate:', e);
        }
    }
}

function register() {
    name = document.getElementById('name').value;
    const room = document.getElementById('roomName').value;

    document.getElementById('room-header').innerText = 'ROOM ' + room;
    document.getElementById('join').style.display = 'none';
    document.getElementById('room').style.display = 'block';

    sendMessage({
        id: 'joinRoom',
        name: name,
        room: room,
    });
}

function leaveRoom() {
    sendMessage({ id: 'leaveRoom' });

    participants.forEach(participant => participant.dispose());
    participants.clear();

    document.getElementById('join').style.display = 'block';
    document.getElementById('room').style.display = 'none';

    ws.close();
}

function sendMessage(message) {
    const jsonMessage = JSON.stringify(message);
    console.log('Sending message:', message);
    ws.send(jsonMessage);
}

// Make functions available globally for HTML events
window.register = register;
window.leaveRoom = leaveRoom;