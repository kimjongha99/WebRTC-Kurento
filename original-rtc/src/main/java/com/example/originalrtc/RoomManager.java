package com.example.originalrtc;



import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.kurento.client.KurentoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Ivan Gracia (izanmail@gmail.com)
 * @since 4.3.1
 */
public class RoomManager {

    private final Logger log = LoggerFactory.getLogger(RoomManager.class);

    @Autowired
    private KurentoClient kurento;

    private final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<>();

/**
     * 활성방 목록에서 방을 찾습니다.
     *
     * @param roomName
     * 방 이름
     * @이미 생성된 방을 반환하고, 이 방이 처음 생성된 경우 새 방을 반환합니다.
     * 액세스됨
     */
    public Room getRoom(String roomName) {
        log.debug("Searching for room {}", roomName);
        Room room = rooms.get(roomName);

        if (room == null) {
            log.debug("Room {} not existent. Will create now!", roomName);
            room = new Room(roomName, kurento.createMediaPipeline());
            rooms.put(roomName, room);
        }
        log.debug("Room {} found!", roomName);
        return room;
    }

    /**
     * Removes a room from the list of available rooms.
     *
     * @param room
     *          the room to be removed
     */
    public void removeRoom(Room room) {
        this.rooms.remove(room.getName());
        room.close();
        log.info("Room {} removed and closed", room.getName());
    }

}