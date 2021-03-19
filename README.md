# Confinity - Achieve Peak Containerization™
*Because we are not peak containerized™ yet*

Confinity is a Java library that allows you to execute code within a separate docker container that is generated and operated purely at run time. This allows execution of code that has no access to any filesystem, stdin/stdout or even the network of the host.

## Usage

Any code that you might want to peak containerize™ must be declared as a subclass of `ConfinedInvocable<T, R>` where `T` is the input type and `R` is the output type, for example:

```kotlin
data class ExampleInput(val greetingSubject: String) : Serializable
data class ExampleOutput(
  val greeting: String,
  val alternateGreetings: List<String>
) : Serializable
class ExampleConfinedInvocation : ConfinedInvocable<ExampleInput, ExampleOutput> {
    fun invoke(input: ExampleInput) = ExampleOutput(
        greeting = "Hello, ${input.greetingSubject}",
        alternateGreetings = listOf(
          "What's up, ${input.greetingSubject}",
          "Hi ${input.greetingSubject}"
        )
    )
}
```

Now wherever your application starts, you need to specify upfront the objects you need to be able to invoke in a peak containerized™ model:

```kotlin
val confinity = Confinity()
val functionHandle = confinity.registerConfinedInvocable(
  ExampleConfinedInvocation::class
)
confinity.commit()
```

Post the commit call, you can use the returned value `functionHandle` wherever you need to call the function:

```kotlin
@RestController
class MyController(
    private fun functionHandle: ConfinedInvocableHandle<ExampleInput, ExampleOutput>
) {
    @GetMapping("/v1/hello/{name}")
    fun sayHello(@PathVariable("name") name: String): ExampleOutput {
        return functionHandle.call(ExampleInput(name))
    }
}
```

When called using postman or curl, this is what you get as an output:

```json
GET /v1/hello/Rohan
{
    "greeting": "Hello, Rohan",
    "alternateGreetings": [
        "What's up, Rohan",
        "Hi Rohan"
    ]
}
```

What is happening here is that at runtime all relevant classes for running the invocable have been packages into a separate docker container, and the function was invoked from within that container. Following is a video of confinity in action:


https://user-images.githubusercontent.com/58416530/111779479-321ee400-88dc-11eb-8082-a5afb8214bfa.mp4

In this video:
1. Confinity is generating a docker image of a new java packaged application - all during runtime
2. Whenever the `call` method on the handle is invoked, it invokes your specific function in a new container - this container is never reused
3. As soon as a container finishes invoking, it is scheduled for deletion - confinity always cleans up after itself, including the generated image and all invocation containers

Since this is all happening during runtime, you can create as many images as you want, and can even dynamically create containers packaging stuff that is figured out dynamically.

### Advanced Usage

#### Adding other classes to the confinity image

While confinity automatically adds input/output classes and other required packaging classes, you might have other dependencies that needed to be bundled in. You can add your other classes by using:

```kotlin
confinity.addDependantClasses(
    ClassA::class,
    ClassB::class,
    ...
)
```

#### Adding other JARs to the confinity image

Do note that as a convenience, `kotlin-stdlib` and `kotlin-runtime` are already made available to all confinity invocations.

Any JARs must be present in the main applications classpath for you to be able to access it from the confinity container. To make a JAR available in the confinity container, add it to `src/main/resources/vendored`.

> **NOTE** Because of how packaging works, bundling of JARs in a parent JAR is tricky since every JAR is expanded by gradle while doing a tree walk. Hence whenever you are adding a JAR to the `vendored` directory, change the extension to `.jarx`. Confinity while packaging will automatically rename to `.jar` before setting up your container.

#### Caveats

There are certain limitations that come with unbounded power, and confinity is no different:
1. You cannot use anonymous inner classes, proxied classes. Static inner classes also most likely will not work. Newer version of `confinity` will bundle in a bytecode disassembler alongwith `jlink` in the confinity image to overcome this limitations.
2. Any implementation of a `ConfinedInvocable` must have a single, primary constructor accepting no arguments.
3. The input/output types for a `ConfinedInvocable` must implement the `Serializable` interface.

## How does it work

Simply put, when you call `.commit()`, confinity does the following:
1. Extracts all dependant classes, preamble classes (these are classes that confinity bundles in to be able to make the module invocable and accessible), any vendored libraries into a separate directory.
2. All classes are then bundled to a new JAR called `confinity.jar`
3. One of the preamble classes contains a confinity-specific class which exports
4. A distroless image is then created in your local docker daemon with the vendored and confinity JARs.
5. Creates a container from this image and then keeps it ready for usage

Any handles of type `ConfinedInvocableHandle<T, R>` basically have a reference to the image and container that was created as part of this process.

Whenever you do call the function handle:
1. Your input type is serialized using standard Java serialization
2. The packaging class (file: `ConfinityPackager.java`) takes two arguments, one would be the class that is to be invoked and the second being the serialized format of the input payload
3. `ConfinityPackager` then invokes the function, serializes the output and then prints it out to stdin (of the docker container) with specific boundaries
4. The `Confinity` manager class (running in your host) then reads this from the docker stdin, parses from the boundary and deserializes it to the output type.

## Performance

A highly sophisticated benchmark on confinity showed that on average a peak containerized™ invocation uses upto 600ms on a server-grade machine. This is four times faster than the time taken for an average human breath, so it is super-fast.

## FAQ

### What is the primary use case?
[Science](https://in.pinterest.com/pin/217720963208496126/)

### Have we achieved peak containerization?
Sadly, no. We are working on solutions that auto-detect all classes in a classpath and containerizes them separately, while changing jvm `invoke` opcodes (`static`, `dynamic` and `special`) to transact via inter-container sockets. This might create too many containers, which is why future versions of confinity will be based on `k3s` which will create each `confinity` image as a kubernetes-like cluster. We are afraid that that still might not be peak containerization but we owe it to ourselves to try.

### Will confinity support instantiating sister containers when launching the main container?
Yes - this is the most requested feature for confinity. It is very useful to have a `postgres` container so that you can do arithmetic operations of the type `SELECT EXP(SQRT(-1)*PI()) + 1` which is commonly required for high-precision science.

### Isn't the 600ms penalty too high for highly transactional calls?
If 600ms bothers you, you are not ready for peak containerization™

### Does confinity plan to support remote docker daemons?
No. We think that would be a bad idea.

### What are the future plans for confinity?
Definitely incorporating AI and ML - however we are currently trying to figure out the problems that it will solve.

### Is this production ready?
This was always production ready.

### Is this a joke?
If you got this far and still have this question, you probably have worse things running in production.

### I need my invocation class to accept other classpath dependencies without enumerating the dependency tree
Write your own runtime containerization and invocation isolation library.

## Author

Rohan Prabhu <rohan.prabhu@jupiter.money>
