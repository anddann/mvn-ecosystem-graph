package de.upb.maven.ecosystem.persistence;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.util.Map;

public class CustomNullDeserializer extends StdDeserializer<Map<String, String>> {

  public CustomNullDeserializer() {
    this(null);
  }

  protected CustomNullDeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public Map<String, String> deserialize(JsonParser p, DeserializationContext ctxt) {
    // TODO write correct deserializer

    return null;
  }
}
