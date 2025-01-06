package com.example.webrtcsfu;

import org.kurento.client.KurentoClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 모든 방을 관리하는 클래스
 * 방 생성, 조회, 삭제 등을 담당
 */
public class RoomRegister {
    // 방 이름을 키로 하여 방 객체를 저장하는 맵
    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    @Autowired
    private KurentoClient kurento;
    @Autowired
    private MessageSender messageSender;


    /**
     * getRoom 메서드
     * @param roomName 확인할 방 이름
     * rooms에서 해당 방이 있는지 없는지 조회하는 메서드
     * @return 방 존재 여부 (있으면 true, 없으면 false)
     */
    public boolean getRoom(String roomName) {
        return rooms.containsKey(roomName);
    }



    /**
     * createRoom 메서드
     * @param roomName 생성할 방 이름
     * 룸 생성
     * @return 생성된 Room 객체
     */
    public Room createRoom(String roomName) {
        Room room = new Room(roomName, kurento.createMediaPipeline(), messageSender);
        rooms.put(roomName, room);
        return room;
    }
    /**
     * 방 이름으로 Room 객체를 가져오는 메서드
     * @param roomName 가져올 방 이름
     * @return Room 객체
     */
    public Room getRoomByName(String roomName) {
        return rooms.get(roomName);
    }

    /**
     * 룸 삭제.
     * @param roomName
     */
    public void removeRoom(String roomName) {
        Room room = rooms.remove(roomName);
        if (room != null) {
            room.getPipeline().release();
        }
    }

}