import java.net.http.*;
import java.net.URI;

public class TestScreenshots {
    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/test/sessions"))
            .GET()
            .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        
        System.out.println("=== Response length: " + body.length());
        System.out.println("=== Contains screenshotBefore: " + body.contains("screenshotBefore"));
        System.out.println("=== Contains screenshotAfter: " + body.contains("screenshotAfter"));
        
        // Покажем первые 2000 символов
        if (body.length() > 2000) {
            System.out.println("=== First 2000 chars:");
            System.out.println(body.substring(0, 2000));
        }
    }
}
