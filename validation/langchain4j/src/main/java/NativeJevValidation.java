import dev.langchain4j.internal.Json;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.listener.*;
import dev.langchain4j.model.typesafe.TypeSafeDecisionModel;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real released LC4J transport, same frozen holdout payloads and application policy. No agent or writes. */
public final class NativeJevValidation {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        var records = (List<Map<String, Object>>) Json.fromJson(Files.readString(Path.of(args[0])), Map.class).get("results");
        var requests = new AtomicInteger(); var responses = new AtomicInteger(); var errors = new AtomicInteger();
        var model = TypeSafeDecisionModel.builder().apiKey(System.getenv("TYPESAFE_API_KEY"))
                .modelName("jev-1.13.0").timeout(Duration.ofSeconds(8)).maxRetries(2)
                .listeners(new DecisionModelListener() {
                    public void onRequest(DecisionModelRequestContext context) { requests.incrementAndGet(); }
                    public void onResponse(DecisionModelResponseContext context) { responses.incrementAndGet(); }
                    public void onError(DecisionModelErrorContext context) { errors.incrementAndGet(); }
                }).build();
        var results = new ArrayList<Map<String, Object>>();
        for (var record : records) {
            if (((Number) record.get("repetition")).intValue() != 1) continue;
            var payload = (Map<String, Object>) record.get("request");
            var builder = DecisionRequest.builder().input((Map<String, ?>) payload.get("state"));
            var questions = (Map<String, Map<String, Object>>) payload.get("questions");
            questions.forEach((name, question) -> {
                Object instructions = question.get("instructions");
                var choice = ChoiceQuestion.builder().text(instructions instanceof String ? (String) instructions : Json.toJson(instructions));
                // beta31's options accept strings; retain every structured description as JSON text.
                ((Map<String, Object>) question.get("criteria")).forEach((option, value) ->
                        choice.option(option, value instanceof String ? (String) value : Json.toJson(value)));
                builder.question(name, choice.build());
            });
            long start = System.nanoTime();
            var response = model.decide(builder.build());
            var intent = response.choice("intent");
            var event = response.choice("event");
            String path = intent.confidence() < 0.6 ? "ASK" : intent.value().equals("CHAT") ? "CHAT" :
                    event.confidence() < 0.6 || Set.of("NO_MATCH", "AMBIGUOUS").contains(event.value()) ? "ASK" : "DECLINE";
            var expected = (Map<String, Object>) record.get("expected");
            boolean passed = path.equals(expected.get("path")) && (!path.equals("DECLINE") || event.value().equals(expected.get("eventId")))
                    && response.modelName().equals("jev-1.13.0");
            results.add(Map.of("id",record.get("id"), "passed",passed, "path",path,
                    "model",response.modelName(), "latencyMs",(System.nanoTime()-start)/1_000_000,
                    "intent",answer(intent), "event",answer(event),
                    "usage",Map.of("input_tokens",response.tokenUsage().inputTokenCount(), "output_tokens",response.tokenUsage().outputTokenCount())));
        }
        var report = Map.of("adapter","TypeSafeDecisionModel", "version","1.21.0-beta31", "live",true,
                "passed",results.stream().filter(r -> Boolean.TRUE.equals(r.get("passed"))).count(), "total",results.size(),
                "listenerRequests",requests.get(), "listenerResponses",responses.get(), "listenerErrors",errors.get(), "results",results);
        Files.writeString(Path.of(args[1]),Json.toJson(report));
        System.out.println("Native LC4J Jev: " + report.get("passed") + "/" + results.size() + "; listener requests/responses/errors " + requests + "/" + responses + "/" + errors);
        if (!report.get("passed").equals((long) results.size()) || errors.get() != 0) throw new IllegalStateException("Native adapter parity failed");
    }
    private static Map<String,Object> answer(ChoiceAnswer answer) {
        return Map.of("choice",answer.value(), "confidence",answer.confidence(), "probabilities",answer.probabilities(), "margin",answer.margin());
    }
}
