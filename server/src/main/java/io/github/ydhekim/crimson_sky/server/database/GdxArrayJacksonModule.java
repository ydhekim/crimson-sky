package io.github.ydhekim.crimson_sky.server.database;

import com.badlogic.gdx.utils.Array;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;

/**
 * Teaches Jackson to (de)serialize LibGDX {@link Array} as a plain JSON array (system design §5/§16).
 *
 * <p><b>Why this is needed, not optional plumbing:</b> the {@code @Json} columns
 * ({@code inventory}/{@code loadout}/{@code skill_tree} values) hold {@code Array}-typed fields.
 * Jackson serializes an {@code Array} as a JSON array on its own (it is {@link Iterable}), but has no
 * way to <i>read</i> one back — it is not a {@code Collection}, so a populated (or even empty)
 * {@code [ ... ]} fails to deserialize, and only the {@code null}-array shape round-trips. Every
 * inventory/loadout persisted to date has been all-null by luck of construction; the moment a real item
 * is granted (the skill-tree grant here, or a repeat of Epic L's bonus grant) the stored arrays become
 * populated and the next read-modify-write must parse them. This module is what makes that read work —
 * and it fixes the latent single-use limitation the L3 bonus grant otherwise had.
 *
 * <p>Registered on both {@code DatabaseManager}'s production mapper and the tests' mapper so the two
 * agree, the same discipline {@code KryoConfig} already applies to the wire format.
 */
public final class GdxArrayJacksonModule extends SimpleModule {

    @SuppressWarnings({"unchecked", "rawtypes"})
    public GdxArrayJacksonModule() {
        super("GdxArrayJacksonModule");
        addSerializer(Array.class, new GdxArraySerializer());
        addDeserializer(Array.class, (JsonDeserializer) new GdxArrayDeserializer(null));
    }

    private static final class GdxArraySerializer extends JsonSerializer<Array> {
        @Override
        public void serialize(Array value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartArray();
            for (Object element : value) {
                provider.defaultSerializeValue(element, gen);
            }
            gen.writeEndArray();
        }
    }

    /**
     * Contextual so it can recover the element type from the declared field (e.g. {@code Array<Weapon>}),
     * then deserialize each element as that type. Falls back to {@code Object} when the element type is
     * erased (a raw {@code Array} property), which no {@code @Json} column uses.
     */
    private static final class GdxArrayDeserializer extends JsonDeserializer<Array<?>>
        implements ContextualDeserializer {

        private final JavaType elementType;

        private GdxArrayDeserializer(JavaType elementType) {
            this.elementType = elementType;
        }

        @Override
        public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
            JavaType wrapper = property != null ? property.getType() : ctxt.getContextualType();
            JavaType element = wrapper != null && wrapper.containedTypeCount() > 0
                ? wrapper.containedType(0)
                : ctxt.constructType(Object.class);
            return new GdxArrayDeserializer(element);
        }

        @Override
        public Array<?> deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
            Array<Object> result = new Array<>();
            if (parser.currentToken() == JsonToken.START_ARRAY) {
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    result.add(ctxt.readValue(parser, elementType));
                }
            }
            return result;
        }
    }
}
