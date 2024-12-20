package com.example.webrtcnm;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.kurento.client.KurentoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class RoomManager {
    @Autowired
    private KurentoClient kurento;
    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    public Room getRoom(String roomName) {
        return rooms.computeIfAbsent(roomName,
                k -> new Room(roomName, kurento.createMediaPipeline()));
    }

    public void removeRoom(Room room) {
        if (rooms.remove(room.getName()) != null) {
            room.close();
        }
    }
}