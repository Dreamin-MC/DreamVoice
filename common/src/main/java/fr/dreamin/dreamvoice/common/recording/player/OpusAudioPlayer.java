package fr.dreamin.dreamvoice.common.recording.player;

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

  private final @NotNull Collection<? extends AudioChannel> channels;
  private final @NotNull List<TimedAudioFrame> frames;
  private final boolean loop;
  private final @Nullable java.util.function.Consumer<TimedAudioFrame> frameConsumer;
  private final @NotNull AtomicBoolean started = new AtomicBoolean(false);
  private final @NotNull AtomicBoolean playing = new AtomicBoolean(false);
  private final @NotNull AtomicBoolean stopped = new AtomicBoolean(false);
  private @Nullable Runnable onStopped;
  private @Nullable Thread playbackThread;
  private final long totalDurationMs;

  public OpusAudioPlayer(
    final @NotNull Collection<? extends AudioChannel> channels,
    final @NotNull List<TimedAudioFrame> frames,
    final boolean loop
  ) {
    this(channels, frames, frames.isEmpty() ? 0L : frames.get(frames.size() - 1).timestampMs() + 20L, loop, null);
  }

  public OpusAudioPlayer(
    final @NotNull Collection<? extends AudioChannel> channels,
    final @NotNull List<TimedAudioFrame> frames,
    final long totalDurationMs,
    final boolean loop
  ) {
    this(channels, frames, totalDurationMs, loop, null);
  }

  public OpusAudioPlayer(
    final @NotNull Collection<? extends AudioChannel> channels,
    final @NotNull List<TimedAudioFrame> frames,
    final long totalDurationMs,
    final boolean loop,
    final @Nullable java.util.function.Consumer<TimedAudioFrame> frameConsumer
  ) {
    this.channels = channels;
    this.frames = frames;
    this.totalDurationMs = Math.max(totalDurationMs, frames.isEmpty() ? 0L : frames.get(frames.size() - 1).timestampMs() + 20L);
    this.loop = loop;
    this.frameConsumer = frameConsumer;
  }

  @Override
  public void startPlaying() {
    if (this.frames.isEmpty() || (this.channels.isEmpty() && this.frameConsumer == null)) {
      stopPlaying();
      return;
    }

    if (this.playing.getAndSet(true))
      return;

    this.started.set(true);
    this.stopped.set(false);

    this.playbackThread = new Thread(this::runPlayback, "DreamVoice-OpusPlayer-" + System.currentTimeMillis());
    this.playbackThread.setDaemon(true);
    this.playbackThread.start();
  }

  private void runPlayback() {
    try {
      final var sortedFrames = new java.util.ArrayList<>(this.frames);
      sortedFrames.sort(java.util.Comparator.comparingLong(TimedAudioFrame::timestampMs));

      do {
        final long startNano = System.nanoTime();

        for (final var frame : sortedFrames) {
          if (this.stopped.get() || !this.playing.get() || Thread.currentThread().isInterrupted())
            return;

          final long targetNano = startNano + (frame.timestampMs() * 1_000_000L);
          while (true) {
            final long remainingNano = targetNano - System.nanoTime();
            if (remainingNano <= 0)
              break;
            if (this.stopped.get() || !this.playing.get())
              return;
            if (remainingNano > 2_000_000L) {
              java.util.concurrent.locks.LockSupport.parkNanos(remainingNano - 1_000_000L);
            } else {
              Thread.onSpinWait();
            }
          }

          final var data = frame.data();
          if (data != null && data.length > 0) {
            if (this.frameConsumer != null) {
              try {
                this.frameConsumer.accept(frame);
              } catch (Throwable ignored) {}
            }

            for (final var ch : this.channels) {
              if (!ch.isClosed()) {
                try {
                  ch.send(data);
                } catch (Throwable ignored) {}
              }
            }
          }
        }

        final long endTargetNano = startNano + (this.totalDurationMs * 1_000_000L);
        while (true) {
          final long remainingNano = endTargetNano - System.nanoTime();
          if (remainingNano <= 0)
            break;
          if (this.stopped.get() || !this.playing.get())
            return;
          java.util.concurrent.locks.LockSupport.parkNanos(Math.min(remainingNano, 50_000_000L));
        }

      } while (this.loop && !this.stopped.get() && this.playing.get());
    } finally {
      stopPlaying();
    }
  }

  @Override
  public void stopPlaying() {
    if (this.stopped.getAndSet(true))
      return;

    this.playing.set(false);

    final var t = this.playbackThread;
    if (t != null) {
      t.interrupt();
      java.util.concurrent.locks.LockSupport.unpark(t);
      this.playbackThread = null;
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
