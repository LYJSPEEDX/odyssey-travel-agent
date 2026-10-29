package com.example.odyssey;

import static org.junit.Assert.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.FutureTask;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TripPreviewClientTest {
    @Test public void postsJsonAndUnwrapsItinerary() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            FutureTask<String> request = serve(server, 200, "{\"itinerary\":{\"schemaVersion\":\"1.0\"}}", "");
            String raw = new TripPreviewClient("http://127.0.0.1:" + server.getLocalPort()).preview(new JSONObject().put("adults", 2));
            assertEquals("1.0", new JSONObject(raw).getString("schemaVersion"));
            assertTrue(request.get().startsWith("POST /trips/preview HTTP/1.1"));
        }
    }
    @Test public void serverErrorIncludesCorrelationIdAndDoesNotReturnMock() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            FutureTask<String> request = serve(server, 503, "{\"detail\":{\"message\":\"Check server configuration.\"}}", "X-Research-Id: test-reference\r\n");
            try {
                new TripPreviewClient("http://127.0.0.1:" + server.getLocalPort()).preview(new JSONObject()); fail();
            } catch (java.io.IOException error) {
                assertTrue(error.getMessage().contains("503"));
                assertTrue(error.getMessage().contains("test-reference"));
            }
            request.get();
        }
    }
    private static FutureTask<String> serve(ServerSocket server, int code, String body, String headers) {
        FutureTask<String> task = new FutureTask<>(() -> {
            try (Socket client = server.accept()) {
                client.setSoTimeout(5000);
                java.io.BufferedReader input = new java.io.BufferedReader(new java.io.InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                String first = input.readLine(), line; int length = 0;
                while ((line = input.readLine()) != null && !line.isEmpty()) {
                    if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
                }
                char[] requestBody = new char[length]; int offset = 0, count;
                while (offset < length && (count = input.read(requestBody, offset, length - offset)) > 0) offset += count;
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                String response = "HTTP/1.1 " + code + " Test\r\nContent-Type: application/json\r\nContent-Length: " + bytes.length + "\r\n" + headers + "Connection: close\r\n\r\n";
                client.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8)); client.getOutputStream().write(bytes);
                return first;
            }
        });
        new Thread(task).start(); return task;
    }
}
