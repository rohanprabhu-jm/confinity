package money.jupiter.confinity.models

import java.io.Serializable

interface ConfinedInvocable<P: Serializable, R: Serializable> {
    fun invoke(payload: P): R
}
