package ir.mahdiparastesh.instatools.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.concurrent.CopyOnWriteArrayList

class CopyOnWriteArrayListSerializer<T>(private val elementSerializer: KSerializer<T>) :
    KSerializer<CopyOnWriteArrayList<T>> {
    override val descriptor: SerialDescriptor = ListSerializer(elementSerializer).descriptor

    override fun serialize(encoder: Encoder, value: CopyOnWriteArrayList<T>) {
        encoder.encodeSerializableValue(ListSerializer(elementSerializer), value.toList())
    }

    override fun deserialize(decoder: Decoder): CopyOnWriteArrayList<T> =
        CopyOnWriteArrayList(
            decoder.decodeSerializableValue(ListSerializer(elementSerializer))
        )
}
