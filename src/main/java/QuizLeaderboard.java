import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class QuizLeaderboard {

    private static final String BASE_URL = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
    private static final String REG_NO = "RA2311043010114";
    private static final int TOTAL_POLLS = 10;
    private static final int DELAY_MS = 5000; // 5 seconds between polls

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        // Key: "roundId|participant" → score (deduplication using a Set)
        Set<String> seen = new HashSet<>();
        Map<String, Integer> scoreMap = new TreeMap<>();

        System.out.println("Starting to poll the quiz API...\n");

        for (int poll = 0; poll < TOTAL_POLLS; poll++) {
            String url = BASE_URL + "/quiz/messages?regNo=" + REG_NO + "&poll=" + poll;
            System.out.println("Poll " + poll + ": GET " + url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("  Status: " + response.statusCode());

            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                JsonNode events = root.get("events");

                if (events != null && events.isArray()) {
                    for (JsonNode event : events) {
                        String roundId    = event.get("roundId").asText();
                        String participant = event.get("participant").asText();
                        int score         = event.get("score").asInt();

                        // Deduplicate using roundId + participant as composite key
                        String key = roundId + "|" + participant;
                        if (seen.contains(key)) {
                            System.out.println("  [DUPLICATE] Skipping: " + key);
                        } else {
                            seen.add(key);
                            scoreMap.merge(participant, score, Integer::sum);
                            System.out.println("  [NEW] " + participant + " +" + score + " (round: " + roundId + ")");
                        }
                    }
                }
            } else {
                System.out.println("  ERROR: " + response.body());
            }

            // 5-second mandatory delay (skip after last poll)
            if (poll < TOTAL_POLLS - 1) {
                System.out.println("  Waiting 5 seconds...\n");
                Thread.sleep(DELAY_MS);
            }
        }

        // Sort leaderboard by totalScore descending
        List<Map.Entry<String, Integer>> leaderboard = new ArrayList<>(scoreMap.entrySet());
        leaderboard.sort((a, b) -> b.getValue() - a.getValue());

        System.out.println("\n=== LEADERBOARD ===");
        int totalScore = 0;
        for (Map.Entry<String, Integer> entry : leaderboard) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
            totalScore += entry.getValue();
        }
        System.out.println("Total Score (all participants): " + totalScore);

        // Build submission payload
        ObjectNode payload = mapper.createObjectNode();
        payload.put("regNo", REG_NO);
        ArrayNode lb = payload.putArray("leaderboard");
        for (Map.Entry<String, Integer> entry : leaderboard) {
            ObjectNode item = mapper.createObjectNode();
            item.put("participant", entry.getKey());
            item.put("totalScore", entry.getValue());
            lb.add(item);
        }

        String payloadStr = mapper.writeValueAsString(payload);
        System.out.println("\nSubmitting leaderboard...");
        System.out.println("Payload: " + payloadStr);

        HttpRequest submitRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/quiz/submit"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payloadStr))
                .build();

        HttpResponse<String> submitResponse = client.send(submitRequest, HttpResponse.BodyHandlers.ofString());
        System.out.println("\n=== SUBMISSION RESPONSE ===");
        System.out.println("Status: " + submitResponse.statusCode());
        System.out.println("Body: " + submitResponse.body());
    }
}
