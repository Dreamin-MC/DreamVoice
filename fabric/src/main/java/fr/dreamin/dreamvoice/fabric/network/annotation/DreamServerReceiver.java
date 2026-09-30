package fr.dreamin.dreamvoice.fabric.network.annotation;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DreamServerReceiver {
  Class<? extends CustomPacketPayload> value() default VoidPayload.class;

  final class VoidPayload implements CustomPacketPayload {
    private VoidPayload() {}

    @Override
    public Type<? extends CustomPacketPayload> type() {
      return null;
    }
  }
}
