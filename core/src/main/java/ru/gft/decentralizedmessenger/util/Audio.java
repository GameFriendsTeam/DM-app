package ru.gft.decentralizedmessenger.util;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

/**
 * Java replacement for api.utils.Audio.Audio (which used sounddevice).
 * Captures/plays 16-bit PCM mono audio via javax.sound.sampled.
 */
public class Audio {
    private final int chunk;
    private final int channels;
    private final int rate;
    private TargetDataLine inputLine;
    private SourceDataLine outputLine;

    public Audio(int chunk, int channels, int rate) throws Exception {
        this.chunk = chunk;
        this.channels = channels;
        this.rate = rate;
        AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
        inputLine = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, format));
        inputLine.open(format, chunk * channels * 2);
        inputLine.start();
        outputLine = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
        outputLine.open(format, chunk * channels * 2);
        outputLine.start();
    }

    /** Read one chunk of audio as raw 16-bit PCM bytes. */
    public byte[] readChunk() {
        byte[] buf = new byte[chunk * channels * 2];
        int read = inputLine.read(buf, 0, buf.length);
        if (read <= 0) return new byte[0];
        byte[] result = new byte[read];
        System.arraycopy(buf, 0, result, 0, read);
        return result;
    }

    public void speak(byte[] audio) {
        outputLine.write(audio, 0, audio.length);
    }

    public void close() {
        try { if (inputLine != null) { inputLine.stop(); inputLine.close(); } } catch (Exception ignored) {}
        try { if (outputLine != null) { outputLine.stop(); outputLine.close(); } } catch (Exception ignored) {}
    }
}
