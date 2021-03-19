package money.jupiter.confinity.demo

import money.jupiter.confinity.models.ConfinedInvocable
import java.io.Serializable

data class ExamplePayload(
    val greetingSubject: String
) : Serializable

data class ExampleResponse(
    val greetingResponse: String,
    val altResponses: List<String>
) : Serializable

class Example : ConfinedInvocable<ExamplePayload, ExampleResponse> {
    override fun invoke(payload: ExamplePayload): ExampleResponse {
        println("I'm shouting into nowhere!!")
        return ExampleResponse(
            "Hello, ${payload.greetingSubject}",
            listOf(
                "Beinvenu, ${payload.greetingSubject}",
                "What's up, ${payload.greetingSubject}")
        )
    }
}
