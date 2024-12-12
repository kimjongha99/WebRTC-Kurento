package com.example.webrtchelloworld;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// Kurento client
import org.kurento.client.BaseRtpEndpoint;
import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.jsonrpc.JsonUtils;

// Kurento events
import org.kurento.client.ConnectionStateChangedEvent;
import org.kurento.client.ErrorEvent;
import org.kurento.client.IceCandidateFoundEvent;
import org.kurento.client.IceComponentStateChangedEvent;
import org.kurento.client.IceGatheringDoneEvent;
import org.kurento.client.MediaFlowInStateChangedEvent;
import org.kurento.client.MediaFlowOutStateChangedEvent;
import org.kurento.client.MediaStateChangedEvent;
import org.kurento.client.MediaTranscodingStateChangedEvent;
import org.kurento.client.NewCandidatePairSelectedEvent;


/**
 * Kurento Java Tutorial - WebSocket message handler.
 */
public class HelloWorldHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(HelloWorldHandler.class);
    private static final Gson gson = new GsonBuilder().create();

    // 사용자 세션을 관리하는 동시성 해시맵
    private final ConcurrentHashMap<String, UserSession> users = new ConcurrentHashMap<>();

    @Autowired
    private KurentoClient kurento;

    /**
     * WebSocket 협상이 성공하고 WebSocket 연결이 완료된 후 호출됩니다.
     * 개봉하여 사용할 준비가 되었습니다.
     */
    // WebSocket 연결이 성공적으로 열렸을 때 호출
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("새로운 WebSocket 연결 생성됨, 세션ID: {}", session.getId());
    }

    // WebSocket 연결이 닫혔을 때 호출
    @Override
    public void afterConnectionClosed(final WebSocketSession session, CloseStatus status) throws Exception {
        if (!status.equalsCode(CloseStatus.NORMAL)) {
            log.warn("비정상적인 연결 종료, 상태: {}, 세션ID: {}", status, session.getId());
        }
        stop(session);
    }


    // 새로운 WebSocket 메시지가 도착했을 때 호출
    @Override
    protected void handleTextMessage(WebSocketSession session,
                                     TextMessage message) throws Exception
    {
        final String sessionId = session.getId();
        JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);

        log.info("-------------클라이언트에서 메시지 수신됨: {}, 세션ID: {}------------------", jsonMessage.get("id"), sessionId);


        try {
            final String messageId = jsonMessage.get("id").getAsString();
            switch (messageId) {


                case "PROCESS_SDP_OFFER":
                    //피어들을 연결하기 위해 가장 첫번째 단계.
                    // 사용자 세션 생성 및 SDP Offer 처리
                    handleProcessSdpOffer(session, jsonMessage);
                    break;


                case "ADD_ICE_CANDIDATE":
                    //sdp가 교환 성공적으로 이뤄자면 Ice Candidate
                    handleAddIceCandidate(session, jsonMessage);
                    break;

                    // 종료
                case "STOP":
                    handleStop(session, jsonMessage);
                    break;
                    // 에러
                case "ERROR":
                    handleError(session, jsonMessage);
                    break;

                default:
                    log.warn("잘못된 메시지 ID: {}", messageId);
                    break;
            }
        } catch (Throwable ex) {
            log.error("메시지 처리 중 오류 발생: {}, 세션ID: {}", ex, sessionId);
            sendError(session, "[Kurento] 오류: " + ex.getMessage());
        }
    }


    // WebSocket 메시지 전송 메서드
    private synchronized void sendMessage(final WebSocketSession session,
                                          String message)
    {
        log.debug("메시지 전송: {}", message);

        //세션 오픈 유효성
        if (!session.isOpen()) {
            log.warn("WebSocket 세션이 닫혀있어 메시지 전송 불가");
            return;
        }

        final String sessionId = session.getId();

        //유저가 존재하지않을떄.
        if (!users.containsKey(sessionId)) {
            log.warn("알 수 없는 사용자에게 메시지 전송 시도, ID: {}", sessionId);
            return;
        }

        try {
            session.sendMessage(new TextMessage(message)); //직접적으로 클라에게 메세지 전송하는부분.
        } catch (IOException ex) {
            log.error("메시지 전송 중 오류: {}", ex.getMessage());
        }
    }

    // 에러 메시지 전송 메서드
    private void sendError(final WebSocketSession session, String errMsg) {
        log.error(errMsg);

        if (users.containsKey(session.getId())) {
            JsonObject message = new JsonObject();
            message.addProperty("id", "ERROR");
            message.addProperty("message", errMsg);
            sendMessage(session, message.toString());
        }
    }

    // PROCESS_SDP_OFFER ---------------------------------------------------------

    // 기본 이벤트 리스너 초기화
    private void initBaseEventListeners(final WebSocketSession session,
                                        BaseRtpEndpoint baseRtpEp, final String className)
    {
        log.info("기본 이벤트 리스너 초기화 - 이름: {}, 클래스: {}, 세션ID: {}",
                baseRtpEp.getName(), className, session.getId());

        // 에러 이벤트 리스너
        baseRtpEp.addErrorListener(new EventListener<ErrorEvent>() {
            @Override
            public void onEvent(ErrorEvent ev) {
                log.error("에러 발생 - 코드: {}, 타입: {}, 소스: {}, 설명: {}",
                        className, ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
                        ev.getTimestampMillis(), ev.getTags(), ev.getDescription());

                sendError(session, "[Kurento] " + ev.getDescription());
                stop(session);
            }
        });


        // 미디어 입력 상태 변경 이벤트
        baseRtpEp.addMediaFlowInStateChangedListener(
                new EventListener<MediaFlowInStateChangedEvent>() {
                    @Override
                    public void onEvent(MediaFlowInStateChangedEvent ev) {
                        log.info("미디어 입력 상태 변경 - 상태: {}, 미디어타입: {}",
                                ev.getState(), ev.getMediaType());
                    }
                });

        // 미디어 출력 상태 변경 이벤트
        baseRtpEp.addMediaFlowOutStateChangedListener(
                new EventListener<MediaFlowOutStateChangedEvent>() {
                    @Override
                    public void onEvent(MediaFlowOutStateChangedEvent ev) {
                        log.info("미디어 출력 상태 변경 - 상태: {}, 미디어타입: {}",
                                ev.getState(), ev.getMediaType());
                    }
                });

        // 연결 상태 변경 이벤트
        baseRtpEp.addConnectionStateChangedListener(
                new EventListener<ConnectionStateChangedEvent>() {
                    @Override
                    public void onEvent(ConnectionStateChangedEvent ev) {
                        log.info("연결 상태 변경 - 이전상태: {}, 새상태: {}",
                                ev.getOldState(), ev.getNewState());
                    }
                });


        // 미디어 상태 변경 이벤트
        baseRtpEp.addMediaStateChangedListener(
                new EventListener<MediaStateChangedEvent>() {
                    @Override
                    public void onEvent(MediaStateChangedEvent ev) {
                        log.info("미디어 상태 변경 - 이전상태: {}, 새상태: {}",
                                ev.getOldState(), ev.getNewState());
                    }
                });

        // 미디어 트랜스코딩 상태 변경 이벤트
        baseRtpEp.addMediaTranscodingStateChangedListener(
                new EventListener<MediaTranscodingStateChangedEvent>() {
                    @Override
                    public void onEvent(MediaTranscodingStateChangedEvent ev) {
                        log.info("트랜스코딩 상태 변경 - 상태: {}, 미디어타입: {}",
                                ev.getState(), ev.getMediaType());
                    }
                });
    }


    // WebRTC 특화 이벤트 리스너 초기화
    private void initWebRtcEventListeners(final WebSocketSession session,
                                          final WebRtcEndpoint webRtcEp)
    {
        log.info("WebRTC 이벤트 리스너 초기화 - 이름: {}, 세션ID: {}",
                webRtcEp.getName(), session.getId());

        // ICE 후보 발견 이벤트
        webRtcEp.addIceCandidateFoundListener(
                new EventListener<IceCandidateFoundEvent>() {
                    @Override
                    public void onEvent(IceCandidateFoundEvent ev) {
                        log.debug("ICE 후보 발견: {}", JsonUtils.toJson(ev.getCandidate()));

                        JsonObject message = new JsonObject();
                        message.addProperty("id", "ADD_ICE_CANDIDATE");
                        message.add("candidate", JsonUtils.toJsonObject(ev.getCandidate()));
                        sendMessage(session, message.toString());
                    }
                });


        // ICE 상태 변경 이벤트
        webRtcEp.addIceComponentStateChangedListener(
                new EventListener<IceComponentStateChangedEvent>() {
                    @Override
                    public void onEvent(IceComponentStateChangedEvent ev) {
                        log.debug("ICE 상태 변경 - 스트림ID: {}, 상태: {}",
                                ev.getStreamId(), ev.getState());
                    }
                });

        // ICE 후보 수집 완료 이벤트
        webRtcEp.addIceGatheringDoneListener(
                new EventListener<IceGatheringDoneEvent>() {
                    @Override
                    public void onEvent(IceGatheringDoneEvent ev) {
                        log.info("ICE 후보 수집 완료");
                    }
                });

        // 새로운 ICE 후보 쌍 선택 이벤트
        webRtcEp.addNewCandidatePairSelectedListener(
                new EventListener<NewCandidatePairSelectedEvent>() {
                    @Override
                    public void onEvent(NewCandidatePairSelectedEvent ev) {
                        log.info("새로운 ICE 후보 쌍 선택 - 로컬: {}, 리모트: {}",
                                ev.getCandidatePair().getLocalCandidate(),
                                ev.getCandidatePair().getRemoteCandidate());
                    }
                });

    }




    // ICE 후보 처리
    private void handleAddIceCandidate(final WebSocketSession session,
                                       JsonObject jsonMessage)
    {
        final String sessionId = session.getId();
        if (!users.containsKey(sessionId)) {
            log.warn("알 수 없는 사용자의 ICE 후보 처리 시도, ID: {}", sessionId);
            return;
        }

        final UserSession user = users.get(sessionId);
        final JsonObject jsonCandidate =
                jsonMessage.get("candidate").getAsJsonObject();
        final IceCandidate candidate =
                new IceCandidate(jsonCandidate.get("candidate").getAsString(),
                        jsonCandidate.get("sdpMid").getAsString(),
                        jsonCandidate.get("sdpMLineIndex").getAsInt());

        WebRtcEndpoint webRtcEp = user.getWebRtcEndpoint();
        webRtcEp.addIceCandidate(candidate);
    }

    // STOP ----------------------------------------------------------------------
    // 세션 종료 처리
    private void stop(final WebSocketSession session) {
        // 사용자 세션 제거 및 모든 리소스 해제
        final UserSession user = users.remove(session.getId());
        if (user != null) {
            MediaPipeline mediaPipeline = user.getMediaPipeline();
            if (mediaPipeline != null) {
                log.info("미디어 파이프라인 해제");
                mediaPipeline.release();
            }
        }
    }

    private void handleStop(final WebSocketSession session,
                            JsonObject jsonMessage)
    {
        stop(session);
    }

    // ERROR ---------------------------------------------------------------------


    // 에러 처리
    private void handleError(final WebSocketSession session,
                             JsonObject jsonMessage) {
        final String errMsg = jsonMessage.get("message").getAsString();
        log.error("브라우저 에러: " + errMsg);

        log.info("에러 발생으로 인한 세션 종료");
        stop(session);
    }

    // ---------------------------------------------------------------------------



    /**
     * 기본 WebSocket 메시지 전송의 오류를 처리합니다.
     * */
    @Override
    public void handleTransportError(WebSocketSession session,
                                     Throwable exception) throws Exception
    {
        log.error("[HelloWorldHandler::handleTransportError] Exception: {}, sessionId: {}",
                exception, session.getId());

        session.close(CloseStatus.SERVER_ERROR);
    }


    private void initWebRtcEndpoint(final WebSocketSession session,
                                    final WebRtcEndpoint webRtcEp, String sdpOffer)
    {
        // 기본 이벤트 리스너와 WebRTC 이벤트 리스너 초기화
        initBaseEventListeners(session, webRtcEp, "WebRtcEndpoint");
        initWebRtcEventListeners(session, webRtcEp);


        final String sessionId = session.getId();
        final String name = "user" + sessionId + "_webrtcendpoint";
        webRtcEp.setName(name);


       /*
        선택사항: 애플리케이션별 STUN 서버를 강제로 사용합니다.
        일반적으로 이는 KMS WebRTC 설정 파일에서 전역적으로 구성됩니다.
        /etc/kurento/modules/kurento/WebRtcEndpoint.conf.ini

        그러나 다음과 같이 애플리케이션별로 구성할 수도 있습니다.

        log.info("[HelloWorldHandler::initWebRtcEndpoint] STUN 서버 사용: 193.147.51.12:3478");
        webRtcEp.setStunServerAddress("193.147.51.12");
        webRtcEp.setStunServerPort(3478);
        */

        // SDP 협상: SDP Answer 생성
        final String sdpAnswer = webRtcEp.processOffer(sdpOffer);
          log.info("------------------------------sdp-협상----------------------------------");
//        log.info("브라우저에서 KMS로의 SDP Offer:\n{}", sdpOffer);
//        log.info("KMS에서 브라우저로의 SDP Answer:\n{}", sdpAnswer);

        JsonObject message = new JsonObject();
        message.addProperty("id", "PROCESS_SDP_ANSWER");
        message.addProperty("sdpAnswer", sdpAnswer);
        sendMessage(session, message.toString());
    }

    private void startWebRtcEndpoint(WebRtcEndpoint webRtcEp) {
        // ICE 후보 수집 시작 - 실제 엔드포인트 작동 시작
        webRtcEp.gatherCandidates();
    }

    private void handleProcessSdpOffer(final WebSocketSession session,
                                       JsonObject jsonMessage) {
        // ---- Session handling

        final String sessionId = session.getId();

        log.info("현재 사용자 수: {}", users.size());
        log.info("새로운 사용자 접속, ID: {}", sessionId);

        // 새로운 사용자 세션 생성
        final UserSession user = new UserSession();
        users.put(sessionId, user);


        // 미디어 파이프라인 생성
        log.info("미디어 파이프라인 생성");
        final MediaPipeline pipeline = kurento.createMediaPipeline();
        user.setMediaPipeline(pipeline);

        // WebRTC 엔드포인트 생성 및 연결
        final WebRtcEndpoint webRtcEp = new WebRtcEndpoint.Builder(pipeline).build();
        user.setWebRtcEndpoint(webRtcEp);
        webRtcEp.connect(webRtcEp);


        // ---- Endpoint configuration
// SDP 처리 및 엔드포인트 시작
        String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
        initWebRtcEndpoint(session, webRtcEp, sdpOffer);
        startWebRtcEndpoint(webRtcEp);
    }

}