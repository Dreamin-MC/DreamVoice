package fr.dreamin.dreamvoice.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;

public final class JsonUtils {

  public static final ObjectMapper MAPPER = new ObjectMapper()
    .enable(SerializationFeature.INDENT_OUTPUT);

  private JsonUtils() {}

  public static ObjectMapper mapper() {
    return MAPPER;
  }

  public static void save(final File file, final Object value) throws IOException {
    if (file.getParentFile() != null && !file.getParentFile().exists())
      file.getParentFile().mkdirs();
    MAPPER.writeValue(file, value);
  }

  public static <T> T load(final File file, final Class<T> clazz) throws IOException {
    if (!file.exists())
      return null;
    return MAPPER.readValue(file, clazz);
  }

  public static <T> T load(final File file, final TypeReference<T> typeRef) throws IOException {
    if (!file.exists())
      return null;
    return MAPPER.readValue(file, typeRef);
  }

}
