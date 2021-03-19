import com.rohanprabhu.confinity.Confinity
import com.rohanprabhu.confinity.demo.Example
import com.rohanprabhu.confinity.demo.ExamplePayload
import kotlin.math.pow

fun main() {
    val confinity = Confinity(cleanupRate = Long.MAX_VALUE)
    val handle = confinity.registerConfinedInvocable(Example())
    confinity.commit()

    val durations = mutableListOf<Long>()

    (1 .. 20).forEach {
        val start = System.nanoTime()
        val response = handle.call(ExamplePayload("rohan$it"))
        val end = System.nanoTime()
        durations.add(end - start)
        println("> $response")
    }

    println("Average Time: ${durations.average() / 10.0.pow(9.0)}s")
}
