import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static final String BASE_URL = "http://localhost:8080/api/polls";
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("====================================");
        System.out.println("   Poll System CLI Client (Java)    ");
        System.out.println("====================================");

        boolean running = true;
        while (running) {
            printMenu();
            System.out.print("Select an option (1-5): ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> listAllQuestions();
                case "2" -> submitNewQuestion();
                case "3" -> voteOnQuestion();
                case "4" -> seeDetailedResults();
                case "5" -> {
                    running = false;
                    System.out.println("\nExiting CLI client. Goodbye!");
                }
                default -> System.out.println("\n[!] Invalid selection. Please choose 1-5.\n");
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n--- MENU ---");
        System.out.println("1. List all questions");
        System.out.println("2. Submit a new question");
        System.out.println("3. Vote on a question");
        System.out.println("4. See detailed results");
        System.out.println("5. Exit");
    }

    // 1. List all questions (GET /api/polls)
    private static void listAllQuestions() {
        System.out.println("\n[+] Fetching all questions...");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .GET()
                .build();

        HttpResponse<String> response = sendRequest(request);
        if (response == null) return;

        if (response.statusCode() == 200) {
            printQuestionsList(response.body());
        } else {
            printErrorResponse(response.body());
        }
    }

    // 2. Submit a new question (POST /api/polls)
    private static void submitNewQuestion() {
        System.out.println("\n--- Submit a New Question ---");
        System.out.print("Enter question text: ");
        String questionText = scanner.nextLine().trim();

        if (questionText.isEmpty()) {
            System.out.println("[!] Question text cannot be empty.");
            return;
        }

        List<String> choices = new ArrayList<>();
        System.out.println("Enter choices one by one (type 'done' when finished, minimum 1):");
        while (true) {
            System.out.print("Choice " + (choices.size() + 1) + ": ");
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("done")) {
                if (choices.isEmpty()) {
                    System.out.println("[!] You must provide at least one choice.");
                    continue;
                }
                break;
            }
            if (!input.isEmpty()) {
                choices.add(input);
            }
        }

        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");
        jsonBuilder.append("\"text\":\"").append(escapeJson(questionText)).append("\",");
        jsonBuilder.append("\"choices\":[");
        for (int i = 0; i < choices.size(); i++) {
            jsonBuilder.append("\"").append(escapeJson(choices.get(i))).append("\"");
            if (i < choices.size() - 1) jsonBuilder.append(",");
        }
        jsonBuilder.append("]}");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBuilder.toString()))
                .build();

        HttpResponse<String> response = sendRequest(request);
        if (response == null) return;

        if (response.statusCode() == 201) {
            System.out.println("\n[OK] Poll Created Successfully!");
            printSingleQuestion(response.body());
        } else {
            printErrorResponse(response.body());
        }
    }

    // 3. Vote on a question (POST /api/polls/{questionId}/vote/{choiceId})
    private static void voteOnQuestion() {
        System.out.println("\n--- Vote on a Question ---");
        System.out.print("Enter Question ID: ");
        String questionId = scanner.nextLine().trim();

        if (questionId.isEmpty()) {
            System.out.println("[!] Question ID cannot be empty.");
            return;
        }

        // Fetch question details first to show the user the question and choices
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .GET()
                .build();

        HttpResponse<String> getResponse = sendRequest(getRequest);
        if (getResponse == null) return;

        String matchingQuestionJson = findQuestionById(getResponse.body(), questionId);
        if (matchingQuestionJson == null) {
            System.out.println("\n[!] Question #" + questionId + " not found.");
            return;
        }

        System.out.println("\n------------------------------------");
        printSingleQuestion(matchingQuestionJson);
        System.out.println("------------------------------------");

        System.out.print("Enter Choice ID to vote for: ");
        String choiceId = scanner.nextLine().trim();

        if (choiceId.isEmpty()) {
            System.out.println("[!] Choice ID cannot be empty.");
            return;
        }

        String targetUrl = BASE_URL + "/" + questionId + "/vote/" + choiceId;

        HttpRequest voteRequest = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> voteResponse = sendRequest(voteRequest);
        if (voteResponse == null) return;

        if (voteResponse.statusCode() == 200) {
            String message = extractJsonValue(voteResponse.body(), "message");
            System.out.println("\n[OK] " + (message.isEmpty() ? "Vote recorded successfully." : message));
            System.out.println("    Voted on Question #" + questionId + " (Choice #" + choiceId + ")");
        } else {
            printErrorResponse(voteResponse.body());
        }
    }

    private static String findQuestionById(String json, String targetId) {
        Pattern pattern = Pattern.compile("\\{[^{}]*\"choices\":\\s*\\[[^]]*]\\s*}");
        Matcher matcher = pattern.matcher(json);

        while (matcher.find()) {
            String qJson = matcher.group();
            String id = extractJsonValue(qJson, "id");
            if (id.equals(targetId)) {
                return qJson;
            }
        }
        return null;
    }

    // 4. See detailed results (GET /api/polls/{questionId}/results)
    private static void seeDetailedResults() {
        System.out.println("\n--- View Poll Results ---");
        System.out.print("Enter Question ID: ");
        String questionId = scanner.nextLine().trim();

        String targetUrl = BASE_URL + "/" + questionId + "/results";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .GET()
                .build();

        HttpResponse<String> response = sendRequest(request);
        if (response == null) return;

        if (response.statusCode() == 200) {
            printFormattedResults(response.body());
        } else {
            printErrorResponse(response.body());
        }
    }

    // Send request helper
    private static HttpResponse<String> sendRequest(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            System.err.println("\n[!] Network error: " + e.getMessage() + ". Is the Spring Boot server running?");
            return null;
        }
    }

    // Formatted Text Outputs (Client-Side Parsing Only)

    private static void printQuestionsList(String json) {
        if (json.trim().equals("[]")) {
            System.out.println("\nNo polls found.");
            return;
        }

        System.out.println("\n------------------------------------");
        System.out.println("          AVAILABLE POLLS           ");
        System.out.println("------------------------------------");

        // Split JSON array into individual question objects
        Pattern pattern = Pattern.compile("\\{[^{}]*\"choices\":\\s*\\[[^]]*]\\s*}");
        Matcher matcher = pattern.matcher(json);

        int count = 0;
        while (matcher.find()) {
            count++;
            printSingleQuestion(matcher.group());
            System.out.println("------------------------------------");
        }

        if (count == 0) {
            System.out.println("Unable to parse polls list.");
        }
    }

    private static void printSingleQuestion(String json) {
        String id = extractJsonValue(json, "id");
        String text = extractJsonValue(json, "text");

        System.out.println("Poll ID #" + id + ": " + text);
        System.out.println("Choices:");

        // Extract choices array content
        int choicesStart = json.indexOf("\"choices\":");
        if (choicesStart != -1) {
            String choicesJson = json.substring(choicesStart);
            Pattern choicePattern = Pattern.compile("\\{\"id\":\\s*(\\d+),\\s*\"text\":\\s*\"([^\"]+)\"");
            Matcher choiceMatcher = choicePattern.matcher(choicesJson);

            while (choiceMatcher.find()) {
                System.out.println("  [" + choiceMatcher.group(1) + "] " + choiceMatcher.group(2));
            }
        }
    }

    private static void printFormattedResults(String json) {
        String qId = extractJsonValue(json, "questionId");
        String qText = extractJsonValue(json, "questionText");
        String totalVotes = extractJsonValue(json, "totalVotes");

        System.out.println("\n====================================");
        System.out.println("POLL RESULTS (Question #" + qId + ")");
        System.out.println("Question: " + qText);
        System.out.println("Total Votes: " + totalVotes);
        System.out.println("====================================");

        // Extract each result object
        Pattern choiceResultPattern = Pattern.compile("\\{\"choiceId\":\\s*(\\d+),\\s*\"choiceText\":\\s*\"([^\"]+)\",\\s*\"votes\":\\s*(\\d+),\\s*\"percentage\":\\s*([\\d.]+)}");
        Matcher matcher = choiceResultPattern.matcher(json);

        boolean found = false;
        while (matcher.find()) {
            found = true;
            String choiceId = matcher.group(1);
            String choiceText = matcher.group(2);
            String votes = matcher.group(3);
            String percentage = matcher.group(4);

            System.out.printf("  Choice [%s] %-20s : %s votes (%s%%)\n", choiceId, choiceText, votes, percentage);
        }

        if (!found) {
            System.out.println("No choice results found.");
        }
        System.out.println("====================================");
    }

    private static void printErrorResponse(String json) {
        String errorMsg = extractJsonValue(json, "error");
        if (errorMsg.isEmpty()) {
            errorMsg = "An unexpected error occurred.";
        }
        System.out.println("\n[!] Error: " + errorMsg);
    }

    private static String extractJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\":\\s*(\"([^\"]*)\"|(\\d+(\\.\\d+)?)|(true|false))");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            if (matcher.group(2) != null) return matcher.group(2); // String value
            if (matcher.group(1) != null) return matcher.group(1); // Numeric/boolean value
        }
        return "";
    }

    private static String escapeJson(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}