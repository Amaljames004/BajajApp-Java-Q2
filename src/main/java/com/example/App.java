package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class App implements CommandLineRunner {

    private final WebClient http;
    private final SolutionRepo repo;

    @Value("${app.name}")  private String name;
    @Value("${app.regNo}") private String regNo;
    @Value("${app.email}") private String email;

    @Value("${bfhl.base}")     private String base;
    @Value("${bfhl.generate}") private String generatePath;
    @Value("${bfhl.submit}")   private String submitPath;

    public App(WebClient http, SolutionRepo repo) {
        this.http = http;
        this.repo = repo;
    }

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Override
    public void run(String... args) {
        System.out.println("== BFHL Q2 (JAVA) ==");

        var req = new GenerateWebhookRequest(name, regNo, email);

        // 1. Call generateWebhook
        GenerateWebhookResponse gw = http.post()
            .uri(base + generatePath)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(req)
            .retrieve()
            .bodyToMono(GenerateWebhookResponse.class)
            .onErrorResume(e -> { 
                System.err.println("GenerateWebhook error: " + e); 
                return Mono.empty(); 
            })
            .block();

        if (gw == null || !StringUtils.hasText(gw.accessToken())) {
            throw new IllegalStateException("Failed to obtain accessToken/webhook");
        }

        System.out.println("Got webhook: " + gw.webhook());
        System.out.println("Got accessToken: " + gw.accessToken());

        // 2. Always solve Q2
        String qType = "Q2";
        String finalSql = solveQuestion2();

        // 3. Save locally
        repo.save(new Solution(regNo, qType, finalSql));
        System.out.println("Stored query for " + qType + ": " + finalSql);

        // 4. Submit query
        String submitUrl = StringUtils.hasText(gw.webhook()) ? gw.webhook() : (base + submitPath);
        SubmitResponse sr = http.post()
            .uri(submitUrl)
            .header("Authorization", gw.accessToken())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new SubmitRequest(finalSql))
            .retrieve()
            .bodyToMono(SubmitResponse.class)
            .onErrorResume(e -> { 
                System.err.println("Submit error: " + e); 
                return Mono.empty(); 
            })
            .block();

        System.out.println("Submit response: " + (sr == null ? "null" : sr));
    }

    // ✅ Your final SQL for Q2
    private String solveQuestion2() {
        return """
            WITH high_salary_employees AS (
                SELECT DISTINCT
                       e.EMP_ID,
                       e.FIRST_NAME,
                       e.LAST_NAME,
                       e.DOB,
                       e.DEPARTMENT
                FROM EMPLOYEE e
                INNER JOIN PAYMENTS p
                        ON p.EMP_ID = e.EMP_ID
                WHERE p.AMOUNT > 70000
            ),
            ranked_employees AS (
                SELECT h.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY h.DEPARTMENT
                           ORDER BY h.FIRST_NAME, h.LAST_NAME, h.EMP_ID
                       ) AS rn
                FROM high_salary_employees h
            )
            SELECT d.DEPARTMENT_NAME,
                   AVG(TIMESTAMPDIFF(YEAR, r.DOB, CURRENT_DATE)) AS AVERAGE_AGE,
                   GROUP_CONCAT(
                       CASE 
                           WHEN r.rn <= 10 THEN CONCAT(r.FIRST_NAME, ' ', r.LAST_NAME)
                           ELSE NULL
                       END
                       ORDER BY r.FIRST_NAME, r.LAST_NAME, r.EMP_ID
                       SEPARATOR ', '
                   ) AS EMPLOYEE_LIST
            FROM DEPARTMENT d
            LEFT JOIN ranked_employees r
                   ON d.DEPARTMENT_ID = r.DEPARTMENT
            GROUP BY d.DEPARTMENT_ID, d.DEPARTMENT_NAME
            ORDER BY d.DEPARTMENT_ID DESC
            """;
    }
}
