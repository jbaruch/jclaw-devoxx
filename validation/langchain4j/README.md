# Released native LangChain4j Jev validation

This standalone JDK 21 application pins `langchain4j-typesafe:1.21.0-beta31`
and calls `TypeSafeDecisionModel` for the 16 untouched holdout scenarios admitted
by the shared Jev evaluator. It exercises `DecisionModelListener` request, response
and error hooks. It does not draft, call action tools or send a message.

The October 2 live result passed **16/16**, with listener counts **16/16/0**
and 162 ms median call latency. The record is
[`../jev/results/langchain4j-native.json`](../jev/results/langchain4j-native.json).
These are fictional fixture checks, not a general accuracy claim or a completed
LangChain4j multi-agent demo.

## Run

Install JDK 21 and Python 3. Supply `TYPESAFE_API_KEY` through the environment
or the repository's ignored `.env`; do not put the key in commands or reports.
From the repository or the handoff bundle root:

```bash
python3 validation/langchain4j/run.py
./gradlew -p validation/langchain4j classes --console=plain  # compile without an API call
```

The runner uses the root Gradle wrapper. It selects JDK 21 on macOS and writes a
new result to ignored `build/jev-validation/langchain4j-native-current.json`,
leaving the admitted report intact. Override with `--report /path/to/result.json`.

The API here is the final **DecisionModel**, not the earlier
StructuredDecisionModel proposal in [PR #6469](https://github.com/langchain4j/langchain4j/pull/6469).
The published beta31 release accepts string question and option descriptions.
The probe serializes the frozen structured descriptions as JSON strings, while
keeping state unchanged. All facts are retained; its wire payload is not identical
to the direct TypeSafe request. The transport's typed response checks remain
active. The application then applies the shared confidence floor and route policy.

For Viktor's app, reuse the domain resources and implement the application gates,
conversation context, canonical assembly and trace listeners with LangChain4j's
idiomatic constructs. The validator does not supply that complete workflow.
