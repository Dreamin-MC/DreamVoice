package fr.dreamin.dreamvoice.core.recording.player;

import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import fr.dreamin.dreamvoice.api.recording.model.TimedAudioFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight, zero-CPU audio player that streams pre-encoded Opus frames directly
 * to Simple Voice Chat audio channels without any Concentus decoding or re-encoding.
 */
public final class OpusAudioPlayer implements AudioPlayer {

  private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(2, r -> {
    final var t = new Thread(r, "DreamVoice-OpusPlayer");
    t.setDaemon(true);
    return t;
  });

  private final @NotNull Collection<? extends AudioChannel> channels;
  private final @NotNull List<TimedAudioFrame> frames;
  private final boolean loop;
  private final @NotNull AtomicBoolean started = new AtomicBoolean(false);
  private final @NotNull AtomicBoolean playing = new AtomicBoolean(false);
  private final @NotNull AtomicBoolean stopped = new AtomicBoolean(false);
  private @Nullable Runnable onStopped;
  private @Nullable ScheduledFuture<?> scheduledTask;
  private final @NotNull AtomicInteger currentFrameIndex = new AtomicInteger(0);
  private long playbackStartTimeMs;
  private final long totalDurationMs;

  public OpusAudioPlayer(
    final @NotNull Collection<? extends AudioChannel> channels,
    final @NotNull List<TimedAudioFrame> frames,
    final boolean loop
  ) {
    this(channels, frames, frames.isEmpty() ? 0L : frames.get(frames.size() - 1).timestampMs() + 20L, loop);
  }

  public OpusAudioPlayer(
    final @NotNull Collection<? extends AudioChannel> channels,
    final @NotNull List<TimedAudioFrame> frames,
    final long totalDurationMs,
    final boolean loop
  ) {
    this.channels = channels;
    this.frames = frames;
    this.totalDurationMs = Math.max(totalDurationMs, frames.isEmpty() ? 0L : frames.get(frames.size() - 1).timestampMs() + 20L);
    this.loop = loop;
  }

  @Override
  public void startPlaying() {
    if (this.frames.isEmpty() || this.channels.isEmpty()) {
      stopPlaying();
      return;
    }

    if (this.playing.getAndSet(true))
      return;

    this.started.set(true);
    this.stopped.set(false);
    this.currentFrameIndex.set(0);
    this.playbackStartTimeMs = System.currentTimeMillis();

    this.scheduledTask = SCHEDULER.scheduleAtFixedRate(this::tick, 0L, 10L, TimeUnit.MILLISECONDS);
  }

  private void tick() {
    if (!this.playing.get() || this.stopped.get())
      return;

    final var elapsedMs = System.currentTimeMillis() - this.playbackStartTimeMs;
    var idx = this.currentFrameIndex.get();

    while (idx < this.frames.size()) {
      final var frame = this.frames.get(idx);
      final var targetTimeMs = frame.timestampMs();

      if (targetTimeMs > elapsedMs)
        break;

      final var data = frame.data();
      if (data != null && data.length > 0) {
        for (final var ch : this.channels) {
          if (!ch.isClosed()) {
            try {
              ch.send(data);
            } catch (Throwable ignored) {}
          }
        }
      }

      idx = this.currentFrameIndex.incrementAndGet();
    }

    if (idx >= this.frames.size() && elapsedMs >= this.totalDurationMs) {
      if (this.loop && this.playing.get() && !this.stopped.get()) {
        this.currentFrameIndex.set(0);
        this.playbackStartTimeMs = System.currentTimeMillis();
      } else {
        stopPlaying();
      }
    }
  }

  @Override
  public void stopPlaying() {
    if (this.stopped.getAndSet(true))
      return;

    this.playing.set(false);

    if (this.scheduledTask != null) {
      this.scheduledTask.cancel(false);
      this.scheduledTask = null;
    }

    if (this.onStopped != null) {
      try {
        this.onStopped.run();
      } catch (Throwable ignored) {}
    }
  }

  @Override
  public boolean isStarted() {
    return this.started.get();
  }

  @Override
  public boolean isPlaying() {
    return this.playing.get() && !this.stopped.get();
  }

  @Override
  public boolean isStopped() {
    return this.stopped.get();
  }

  @Override
  public void setOnStopped(final @NotNull Runnable onStopped) {
    this.onStopped = onStopped;
  }

}
