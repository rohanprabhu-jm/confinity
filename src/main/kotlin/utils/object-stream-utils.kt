package utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.*

/**
 * Converts an object to a Base64 serialized string, based on standard Java serialization
 */
fun Serializable.toSerializedBytes() : String {
    val baos = ByteArrayOutputStream()
    val objectOutputStream = ObjectOutputStream(baos)

    objectOutputStream.writeObject(this)
    objectOutputStream.close()

    return Base64.getEncoder().encodeToString(baos.toByteArray())
}

/**
 * Converts a Base64 serialized string (based on standard Java serialization) to a Java object. Do note that that
 * class cast is not guaranteed to work.
 */
@Suppress("UNCHECKED_CAST")
fun <T> String.fromSerializedBytes() : T = ObjectInputStream(ByteArrayInputStream(Base64.getDecoder().decode(this)))
        .readObject() as T
