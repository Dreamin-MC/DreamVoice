package fr.dreamin.dreamvoice.api.wall.event;

import fr.dreamin.dreamvoice.api.event.VoiceCancelEvent;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired during VoiceWall acoustic raycasting between a sender and receiver.
 * Canceling this event bypasses acoustic occlusion (making sound 100% direct and unattenuated).
 */
@Getter
@Setter
public final class VoiceWallOcclusionEvent extends VoiceCancelEvent {

  private final @NotNull VPlayer sender;
  private final @NotNull VPlayer receiver;
  private final double originalLossDb;
  private double lossDb;
  private boolean blocked;

  public VoiceWallOcclusionEvent(
    final @NotNull VPlayer sender,
    final @NotNull VPlayer receiver,
    final double originalLossDb,
    final double lossDb,
    final boolean blocked
  ) {
    this.sender = sender;
    this.receiver = receiver;
    this.originalLossDb = originalLossDb;
    this.lossDb = lossDb;
    this.blocked = blocked;
  }

}
