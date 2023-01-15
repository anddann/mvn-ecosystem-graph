package de.upb.maven.ecosystem.persistence.redis;

import com.google.common.base.Strings;
import org.apache.commons.lang3.SerializationUtils;

import java.io.Serializable;

public class RedisSerializerUtil {

  public static byte[] serialize(Serializable obj) {
    return SerializationUtils.serialize(obj);
  }

  public static Object deserialize(byte[] bytes) {
    return SerializationUtils.deserialize(bytes);
  }

  public static String getRedisURLFromEnvironment() {
    String res = System.getenv("REDIS");
    if (Strings.isNullOrEmpty(res)) {
      res = "localhost";
    }
    return res;
  }
}
