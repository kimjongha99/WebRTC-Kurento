package com.example.groupcall;


import java.util.concurrent.ConcurrentHashMap;
import org.kurento.client.KurentoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;


public class RoomManager {
    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

    @Autowired
    private KurentoClient kurento;

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    // 방 생성 또는 가져오기
    public Room getRoom(String roomName) {
        Room room = rooms.get(roomName);
        if (room == null) {
            log.info("새로운 방 생성: {}", roomName);
            room = new Room(roomName, kurento.createMediaPipeline());
            rooms.put(roomName, room);
        }
        return room;
    }

    // 방 제거
    public void removeRoom(String roomName) {
        Room room = rooms.remove(roomName);
        if (room != null) {
            try {
                log.info("방 {} 제거 시작", roomName);
                room.close();
                log.info("방 {} 제거 완료 - 현재 방 개수: {}", roomName, rooms.size());
            } catch (Exception e) {
                log.error("방 {} 제거 중 오류 발생: {}", roomName, e.getMessage());
                // 오류가 발생해도 rooms에서는 제거
                rooms.remove(roomName);
            }
        }
    }

    // 현재 활성화된 방 개수 확인
    public int getRoomCount() {
        return rooms.size();
    }
}