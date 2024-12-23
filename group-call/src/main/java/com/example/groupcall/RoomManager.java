package com.example.groupcall;


import java.util.concurrent.ConcurrentHashMap;
import org.kurento.client.KurentoClient;
import org.springframework.beans.factory.annotation.Autowired;


public class RoomManager {
    @Autowired
    private KurentoClient kurento;

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    public Room getRoom(String roomName) {
        return rooms.computeIfAbsent(roomName,
                key -> new Room(roomName, kurento.createMediaPipeline())
        );
    }

    public void removeRoom(String roomName) {
        Room room = rooms.remove(roomName);
        if (room != null) {
            room.close();
        }
    }
}