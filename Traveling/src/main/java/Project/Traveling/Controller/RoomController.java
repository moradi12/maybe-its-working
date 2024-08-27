package Project.Traveling.Controller;

import Project.Traveling.Exceptions.PhotoRetrievalException;
import Project.Traveling.Exceptions.ResourceNotFoundException;
import Project.Traveling.Exceptions.InvalidBookingsRequestException;
import Project.Traveling.Model.BookedRoom;
import Project.Traveling.Model.Room;
import Project.Traveling.Response.RoomResponse;
import Project.Traveling.Service.BookingService;
import Project.Traveling.Service.IRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/rooms")
@CrossOrigin(origins = "http://localhost:5173")
public class RoomController {

    private final IRoomService roomService;
    private final BookingService bookingService;

    // Add a new room
    @PostMapping("/add/new-room")
    public ResponseEntity<RoomResponse> addNewRoom(
            @RequestParam("photo") MultipartFile photo,
            @RequestParam("roomType") String roomType,
            @RequestParam("roomPrice") BigDecimal roomPrice) throws SQLException, IOException {
        System.out.println("Adding new room with type: " + roomType + " and price: " + roomPrice); // Logging
        System.out.println("Photo: " + photo.getOriginalFilename()); // Logging

        Room savedRoom = roomService.addNewRoom(photo, roomType, roomPrice);
        RoomResponse response = new RoomResponse(savedRoom.getId(), savedRoom.getRoomType(), savedRoom.getRoomPrice());
        return ResponseEntity.ok(response);
    }

    // Get all room types
    @GetMapping("/room/types")
    public List<String> getRoomTypes() {
        return roomService.getAllRoomTypes();
    }

    // Update an existing room
    @PutMapping("/update/{roomId}")
    public ResponseEntity<?> updateRoom(@PathVariable Long roomId,
                                        @RequestParam(required = false) String roomType,
                                        @RequestParam(required = false) BigDecimal roomPrice,
                                        @RequestParam(required = false) MultipartFile photo) {

        byte[] photoBytes = null;
        try {
            if (photo != null && !photo.isEmpty()) {
                System.out.println("Updating photo for room ID: " + roomId); // Logging
                photoBytes = photo.getBytes();
            } else {
                System.out.println("No photo provided, retrieving existing photo for room ID: " + roomId); // Logging
                photoBytes = roomService.getRoomPhotoByRoomId(roomId);
            }
        } catch (IOException | SQLException e) {
            System.err.println("Error processing the photo or retrieving existing photo: " + e.getMessage()); // Logging
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing the photo or retrieving existing photo: " + e.getMessage());
        }

        Room theRoom = roomService.updateRoom(roomId, roomType, roomPrice, photoBytes);
        RoomResponse roomResponse = getRoomResponse(theRoom);
        return ResponseEntity.ok(roomResponse);
    }

    // Edit an existing room
    @PutMapping("/edit/{roomId}")
    public ResponseEntity<RoomResponse> editRoom(
            @PathVariable Long roomId,
            @RequestParam(value = "photo", required = false) MultipartFile photo,
            @RequestParam("roomType") String roomType,
            @RequestParam("roomPrice") BigDecimal roomPrice) throws SQLException, IOException {
        System.out.println("Editing room with ID: " + roomId); // Logging
        System.out.println("New room type: " + roomType); // Logging
        System.out.println("New room price: " + roomPrice); // Logging
        if (photo != null) {
            System.out.println("New photo: " + photo.getOriginalFilename()); // Logging
        } else {
            System.out.println("No new photo provided."); // Logging
        }

        Room updatedRoom = roomService.editRoom(roomId, photo, roomType, roomPrice);
        RoomResponse response = new RoomResponse(updatedRoom.getId(), updatedRoom.getRoomType(), updatedRoom.getRoomPrice());
        return ResponseEntity.ok(response);
    }

    // Delete a room by ID
    @DeleteMapping("/delete/{roomId}")
    public ResponseEntity<String> deleteRoom(@PathVariable Long roomId) {
        try {
            roomService.deleteRoom(roomId);
            return ResponseEntity.ok("Room deleted successfully.");
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred while deleting the room.");
        }
    }

    // Get all rooms
    @GetMapping("/all")
    public ResponseEntity<List<Room>> getAllRooms() throws SQLException {
        List<Room> rooms = roomService.getAllRooms();
        System.out.println("Fetched rooms from DB: " + rooms); // Logging
        return ResponseEntity.ok(rooms);
    }

    // Get a room by ID
    @GetMapping("/room/{roomId}")
    public ResponseEntity<Optional<RoomResponse>> getRoomById(@PathVariable Long roomId) {
        Optional<Room> theRoom = roomService.getRoomById(roomId);
        return theRoom.map(room -> {
            RoomResponse roomResponse = getRoomResponse(room);
            return ResponseEntity.ok(Optional.of(roomResponse));
        }).orElseThrow(() -> new ResourceNotFoundException("Room not found"));
    }

    // Helper method to create RoomResponse
    private RoomResponse getRoomResponse(Room room) throws PhotoRetrievalException {
        List<BookedRoom> bookings = bookingService.getAllBookingsByRoomId(room.getId()); // Directly get the List<BookedRoom>
        return new RoomResponse(room.getId(), room.getRoomType(), room.getRoomPrice(), room.isBooked());
    }


    // Get all bookings
    @GetMapping("/bookings")
    public ResponseEntity<List<BookedRoom>> getAllBookings() {
        List<BookedRoom> bookings = bookingService.getAllBookings();
        return ResponseEntity.ok(bookings);
    }

    // Get all bookings by room ID
    @GetMapping("/bookings/room/{roomId}")
    public ResponseEntity<List<BookedRoom>> getAllBookingsByRoomId(@PathVariable Long roomId) {
        List<BookedRoom> bookings = bookingService.getAllBookingsByRoomId(roomId);
        return ResponseEntity.ok(bookings);
    }

    // Cancel a booking by ID
    @DeleteMapping("/bookings/{bookingId}")
    public ResponseEntity<Void> cancelBooking(@PathVariable Long bookingId) {
        try {
            bookingService.cancelBooking(bookingId);
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // Save a new booking
    @PostMapping("/bookings/room/{roomId}")
    public ResponseEntity<String> saveBooking(@PathVariable Long roomId, @RequestBody BookedRoom bookingRequest) {
        try {
            String confirmationCode = bookingService.saveBooking(roomId, bookingRequest);
            return ResponseEntity.ok(confirmationCode);
        } catch (InvalidBookingsRequestException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // Find a booking by confirmation code
    @GetMapping("/bookings/{confirmationCode}")
    public ResponseEntity<BookedRoom> findBookingByConfirmationCode(@PathVariable String confirmationCode) {
        try {
            BookedRoom bookedRoom = bookingService.findBookingByConfirmationCode(confirmationCode);
            return ResponseEntity.ok(bookedRoom);
        } catch (InvalidBookingsRequestException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
