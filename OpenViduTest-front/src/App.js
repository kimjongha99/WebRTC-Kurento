import { OpenVidu } from 'openvidu-browser';
import axios from 'axios';
import React, { useState, useEffect, useCallback } from 'react';
import './App.css';
import UserVideoComponent from './UserVideoComponent';

const APPLICATION_SERVER_URL = 'http://localhost:8080/';

const App = () => {
    const [mySessionId, setMySessionId] = useState('SessionA');
    const [myUserName, setMyUserName] = useState('Participant' + Math.floor(Math.random() * 100));
    const [session, setSession] = useState(undefined);
    const [mainStreamManager, setMainStreamManager] = useState(undefined);
    const [publisher, setPublisher] = useState(undefined);
    const [subscribers, setSubscribers] = useState([]);
    const [currentVideoDevice, setCurrentVideoDevice] = useState(undefined);
    const [OV, setOV] = useState(undefined);

    useEffect(() => {
        window.addEventListener('beforeunload', onBeforeUnload);
        return () => {
            window.removeEventListener('beforeunload', onBeforeUnload);
        };
    }, []);

    const onBeforeUnload = () => {
        leaveSession();
    };

    const handleChangeSessionId = (e) => {
        setMySessionId(e.target.value);
    };

    const handleChangeUserName = (e) => {
        setMyUserName(e.target.value);
    };

    const handleMainVideoStream = (stream) => {
        if (mainStreamManager !== stream) {
            setMainStreamManager(stream);
        }
    };

    const deleteSubscriber = (streamManager) => {
        setSubscribers(prevSubscribers => prevSubscribers.filter(sub => sub !== streamManager));
    };

    const joinSession = async () => {
        const openVidu = new OpenVidu();
        setOV(openVidu);

        const newSession = openVidu.initSession();
        setSession(newSession);

        newSession.on('streamCreated', (event) => {
            const subscriber = newSession.subscribe(event.stream, undefined);
            setSubscribers(prevSubscribers => [...prevSubscribers, subscriber]);
        });

        newSession.on('streamDestroyed', (event) => {
            deleteSubscriber(event.stream.streamManager);
        });

        newSession.on('exception', (exception) => {
            console.warn(exception);
        });

        try {
            const token = await getToken();
            await newSession.connect(token, { clientData: myUserName });

            const mediaStream = await navigator.mediaDevices.getDisplayMedia({
                video: true,
                audio: true,
            });

            const videoTrack = mediaStream.getVideoTracks()[0];
            const audioTrack = mediaStream.getAudioTracks()[0];

            const newPublisher = await openVidu.initPublisherAsync(undefined, {
                audioSource: audioTrack,
                videoSource: videoTrack,
                publishAudio: true,
                publishVideo: true,
                resolution: '640x480',
                frameRate: 30,
                insertMode: 'APPEND',
                mirror: false,
            });

            newSession.publish(newPublisher);

            const devices = await openVidu.getDevices();
            const videoDevices = devices.filter(device => device.kind === 'videoinput');
            const currentVideoDevice = videoDevices.find(device => device.deviceId === newPublisher.stream.getMediaStream().getVideoTracks()[0].getSettings().deviceId);

            setCurrentVideoDevice(currentVideoDevice);
            setMainStreamManager(newPublisher);
            setPublisher(newPublisher);
        } catch (error) {
            console.log('Error connecting to session:', error);
        }
    };

    const leaveSession = () => {
        if (session) {
            session.disconnect();
        }

        setOV(undefined);
        setSession(undefined);
        setSubscribers([]);
        setMySessionId('SessionA');
        setMyUserName('Participant' + Math.floor(Math.random() * 100));
        setMainStreamManager(undefined);
        setPublisher(undefined);
    };

    const switchCamera = async () => {
        try {
            const devices = await OV.getDevices();
            const videoDevices = devices.filter(device => device.kind === 'videoinput');

            if (videoDevices.length > 1) {
                const newVideoDevice = videoDevices.find(device => device.deviceId !== currentVideoDevice.deviceId);

                if (newVideoDevice) {
                    const newPublisher = OV.initPublisher(undefined, {
                        videoSource: newVideoDevice.deviceId,
                        publishAudio: true,
                        publishVideo: true,
                        mirror: true,
                    });

                    await session.unpublish(mainStreamManager);
                    await session.publish(newPublisher);

                    setCurrentVideoDevice(newVideoDevice);
                    setMainStreamManager(newPublisher);
                    setPublisher(newPublisher);
                }
            }
        } catch (error) {
            console.error(error);
        }
    };

    const getToken = async () => {
        const sessionId = await createSession(mySessionId);
        return await createToken(sessionId);
    };

    const createSession = async (sessionId) => {
        const response = await axios.post(APPLICATION_SERVER_URL + 'api/sessions', { customSessionId: sessionId }, {
            headers: { 'Content-Type': 'application/json' },
        });
        return response.data;
    };

    const createToken = async (sessionId) => {
        const response = await axios.post(APPLICATION_SERVER_URL + 'api/sessions/' + sessionId + '/connections', {}, {
            headers: { 'Content-Type': 'application/json' },
        });
        return response.data;
    };

    return (
        <div className="container">
            {session === undefined ? (
                <div id="join">
                    <div id="img-div">
                        <img src="resources/images/openvidu_grey_bg_transp_cropped.png" alt="OpenVidu logo" />
                    </div>
                    <div id="join-dialog" className="jumbotron vertical-center">
                        <h1>Join a video session</h1>
                        <form className="form-group" onSubmit={(e) => { e.preventDefault(); joinSession(); }}>
                            <p>
                                <label>Participant: </label>
                                <input
                                    className="form-control"
                                    type="text"
                                    id="userName"
                                    value={myUserName}
                                    onChange={handleChangeUserName}
                                    required
                                />
                            </p>
                            <p>
                                <label>Session: </label>
                                <input
                                    className="form-control"
                                    type="text"
                                    id="sessionId"
                                    value={mySessionId}
                                    onChange={handleChangeSessionId}
                                    required
                                />
                            </p>
                            <p className="text-center">
                                <input className="btn btn-lg btn-success" name="commit" type="submit" value="JOIN" />
                            </p>
                        </form>
                    </div>
                </div>
            ) : null}

            {session !== undefined ? (
                <div id="session">
                    <div id="session-header">
                        <h1 id="session-title">{mySessionId}</h1>
                        <input
                            className="btn btn-large btn-danger"
                            type="button"
                            id="buttonLeaveSession"
                            onClick={leaveSession}
                            value="Leave session"
                        />
                        <input
                            className="btn btn-large btn-success"
                            type="button"
                            id="buttonSwitchCamera"
                            onClick={switchCamera}
                            value="Switch Camera"
                        />
                    </div>

                    {mainStreamManager !== undefined ? (
                        <div id="main-video" className="col-md-6">
                            <UserVideoComponent streamManager={mainStreamManager} />
                        </div>
                    ) : null}
                    <div id="video-container" className="col-md-6">
                        {publisher !== undefined ? (
                            <div className="stream-container col-md-6 col-xs-6" onClick={() => handleMainVideoStream(publisher)}>
                                <UserVideoComponent streamManager={publisher} />
                            </div>
                        ) : null}
                        {subscribers.map((sub, i) => (
                            <div key={sub.id} className="stream-container col-md-6 col-xs-6" onClick={() => handleMainVideoStream(sub)}>
                                <span>{sub.id}</span>
                                <UserVideoComponent streamManager={sub} />
                            </div>
                        ))}
                    </div>
                </div>
            ) : null}
        </div>
    );
};

export default App;
