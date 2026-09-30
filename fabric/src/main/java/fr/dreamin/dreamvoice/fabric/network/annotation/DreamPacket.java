package fr.dreamin.dreamvoice.fabric.network.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DreamPacket {
  Type type();

  enum Type {
    SERVER_BOUND_PLAY,
    SERVER_BOUND_CONFIGURATION,
    CLIENT_BOUND_PLAY,
    CLIENT_BOUND_CONFIGURATION
  }
}
