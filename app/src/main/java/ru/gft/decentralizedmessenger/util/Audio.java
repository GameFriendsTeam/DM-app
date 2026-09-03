package ru.gft.decentralizedmessenger.util;

import android.annotation.SuppressLint;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

public class Audio {
    private final int chunk;
    private final int channels;
    private final int rate;

    private AudioRecord inputLine;
    private AudioTrack outputLine;

    @SuppressLint("MissingPermission")
    public Audio(int chunk, int channels, int rate) throws Exception {
        this.chunk = chunk;
        this.channels = channels;
        this.rate = rate;

        int androidFormat = AudioFormat.ENCODING_PCM_16BIT;

        int inChannelConfig = (channels == 1) ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO;
        int outChannelConfig = (channels == 1) ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;

        int bufferSizeBytes = chunk * channels * 2;

        int minInBufferSize = AudioRecord.getMinBufferSize(rate, inChannelConfig, androidFormat);
        int finalInBufferSize = Math.max(bufferSizeBytes, minInBufferSize);

        int minOutBufferSize = AudioTrack.getMinBufferSize(rate, outChannelConfig, androidFormat);
        int finalOutBufferSize = Math.max(bufferSizeBytes, minOutBufferSize);

        inputLine = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                rate,
                inChannelConfig,
                androidFormat,
                finalInBufferSize
        );

        if (inputLine.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new Exception("Failed to initialize AudioRecord. Check microphone permissions.");
        }
        inputLine.startRecording();

        outputLine = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(androidFormat)
                        .setSampleRate(rate)
                        .setChannelMask(outChannelConfig)
                        .build())
                .setBufferSizeInBytes(finalOutBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        if (outputLine.getState() != AudioTrack.STATE_INITIALIZED) {
            throw new Exception("Failed to initialize AudioTrack.");
        }
        outputLine.play();
    }

    public byte[] readChunk() {
        if (inputLine == null || inputLine.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            return new byte[0];
        }

        byte[] buf = new byte[chunk * channels * 2];
        int read = inputLine.read(buf, 0, buf.length);

        if (read <= 0) return new byte[0];
        if (read == buf.length) return buf;

        byte[] result = new byte[read];
        System.arraycopy(buf, 0, result, 0, read);
        return result;
    }

    public void speak(byte[] audio) {
        if (outputLine != null && outputLine.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            outputLine.write(audio, 0, audio.length);
        }
    }

    public void close() {
        try {
            if (inputLine != null) {
                if (inputLine.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    inputLine.stop();
                }
                inputLine.release();
                inputLine = null;
            }
        } catch (Exception ignored) {}

        try {
            if (outputLine != null) {
                if (outputLine.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    outputLine.stop();
                }
                outputLine.release();
                outputLine = null;
            }
        } catch (Exception ignored) {}
    }
}
