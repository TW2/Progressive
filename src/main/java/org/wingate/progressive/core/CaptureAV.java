/*
 * Copyright (C) 2023 util2
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.wingate.progressive.core;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

/**
 *
 * @author util2
 */
public class CaptureAV implements Runnable {

    private final static int FRAME_RATE = 25;
    private final static int GOP_LENGTH_IN_FRAMES = 50;

    private long startTime = 0;
    private long videoTS = 0;

    private final File media;
    private final Rectangle r;
    private final Mixer.Info mixerInfo;
    
    private final Java2DFrameConverter converter = new Java2DFrameConverter();
    private FFmpegFrameRecorder recorder;
    private Thread process;
    private Thread audio;
    private TargetDataLine line;
    
    private volatile boolean onLoop = false;

    public CaptureAV(File media, Rectangle r, Mixer.Info mixerInfo) {
        this.media = media;
        this.r = r;
        this.mixerInfo = mixerInfo;
    }
    
    @SuppressWarnings("Convert2Lambda")
    private void setup(){
        // org.bytedeco.javacv.FFmpegFrameRecorder.FFmpegFrameRecorder(String
        // filename, int imageWidth, int imageHeight, int audioChannels)
        // For each param, we're passing in...
        // filename = either a path to a local file we wish to create, or an
        // RTMP url to an FMS / Wowza server
        // imageWidth = width we specified for the grabber
        // imageHeight = height we specified for the grabber
        // audioChannels = 2, because we like stereo
        recorder = new FFmpegFrameRecorder(
                media.getPath(),
                r.width,
                r.height, 
                2
        );
        recorder.setInterleaved(true);

        // decrease "startup" latency in FFMPEG (see:
        // https://trac.ffmpeg.org/wiki/StreamingGuide)
        recorder.setVideoOption("tune", "zerolatency");
        // tradeoff between quality and encode speed
        // possible values are ultrafast,superfast, veryfast, faster, fast,
        // medium, slow, slower, veryslow
        // ultrafast offers us the least amount of compression (lower encoder
        // CPU) at the cost of a larger stream size
        // at the other end, veryslow provides the best compression (high
        // encoder CPU) while lowering the stream size
        // (see: https://trac.ffmpeg.org/wiki/Encode/H.264)
        recorder.setVideoOption("preset", "ultrafast");
        // Constant Rate Factor (see: https://trac.ffmpeg.org/wiki/Encode/H.264)
        recorder.setVideoOption("crf", "22");
        // 2000 kb/s, reasonable "sane" area for 720
        recorder.setVideoBitrate(2000000);
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setFormat(media.getName().toLowerCase().substring(media.getName().lastIndexOf(".")+1));
        // FPS (frames per second)
        recorder.setFrameRate(FRAME_RATE);
        // Key frame interval, in our case every 2 seconds -> 30 (fps) * 2 = 60
        // (gop length)
        recorder.setGopSize(GOP_LENGTH_IN_FRAMES);

        // We don't want variable bitrate audio
        recorder.setAudioOption("crf", "0");
        // Highest quality
        recorder.setAudioQuality(0);
        // 192 Kbps
        recorder.setAudioBitrate(192000);
        recorder.setSampleRate(44100);
        recorder.setAudioChannels(2);
        recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
        
        // Thread for audio capture, this could be in a nested private class if you prefer...
        audio = new Thread(new Runnable() {
            @Override
            @SuppressWarnings("CallToPrintStackTrace")
            public void run()
            {
                // Pick a format...
                // NOTE: It is better to enumerate the formats that the system supports,
                // because getLine() can error out with any particular format...
                // For us: 44.1 sample rate, 16 bits, stereo, signed, little endian
                AudioFormat audioFormat = new AudioFormat(44100.0F, 16, 2, true, false);

                // Get TargetDataLine with that format
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);

                System.out.println("1");
                
                try
                {
                    // Open and start capturing audio
                    // It's possible to have more control over the chosen audio device with this line:
                    line = (TargetDataLine)mixer.getLine(dataLineInfo);
                    //final TargetDataLine line = (TargetDataLine)AudioSystem.getLine(dataLineInfo);
                    line.open(audioFormat);
                    line.start();

                    final int sampleRate = (int) audioFormat.getSampleRate();
                    final int numChannels = audioFormat.getChannels();

                    // Let's initialize our audio buffer...
                    final int audioBufferSize = sampleRate * numChannels;
                    final byte[] audioBytes = new byte[audioBufferSize];

                    // Using a ScheduledThreadPoolExecutor vs a while loop with
                    // a Thread.sleep will allow
                    // us to get around some OS specific timing issues, and keep
                    // to a more precise
                    // clock as the fixed rate accounts for garbage collection
                    // time, etc
                    // a similar approach could be used for the webcam capture
                    // as well, if you wish
                    ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1);
                    exec.scheduleAtFixedRate(new Runnable() {
                        @Override
                        @SuppressWarnings("CallToPrintStackTrace")
                        public void run()
                        {
                            try
                            {
                                // Read from the line... non-blocking
                                int nBytesRead = 0;
                                while (nBytesRead == 0) {
                                    nBytesRead = line.read(audioBytes, 0, line.available());
                                    System.out.println("audiobytes: " + audioBytes.length);
                                }

                                // Since we specified 16 bits in the AudioFormat,
                                // we need to convert our read byte[] to short[]
                                // (see source from FFmpegFrameRecorder.recordSamples for AV_SAMPLE_FMT_S16)
                                // Let's initialize our short[] array
                                int nSamplesRead = nBytesRead / 2;
                                short[] samples = new short[nSamplesRead];

                                // Let's wrap our short[] into a ShortBuffer and
                                // pass it to recordSamples
                                ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
                                ShortBuffer sBuff = ShortBuffer.wrap(samples, 0, nSamplesRead);

                                // recorder is instance of
                                // org.bytedeco.javacv.FFmpegFrameRecorder
                                recorder.recordSamples(sampleRate, numChannels, sBuff);
                            } 
                            catch (org.bytedeco.javacv.FrameRecorder.Exception e)
                            {
                                //e.printStackTrace();
                                audio.interrupt();
                                audio = null;
                            }
                        }
                    }, 0, (long) 1000 / FRAME_RATE, TimeUnit.MILLISECONDS);
                } 
                catch (LineUnavailableException e1)
                {
                    e1.printStackTrace();
                }
            }
        });
        
        audio.start();
    }
    
    @SuppressWarnings("Convert2Lambda")
    public void startRecording(){        
        setup();
            
        process = new Thread(this);
        process.start();
        
        onLoop = true;        
        
        try {
            // Jack 'n coke... do it...
            recorder.start();           
            
        } catch (FFmpegFrameRecorder.Exception ex) {
            Logger.getLogger(CaptureAV.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void stopRecording(){
        onLoop = false;
    }
    
    private void doLoop(){
        try {            
            Frame capturedFrame;
            
            // While we are capturing...
            while ((capturedFrame = fromRobot()) != null)
            {
                
                // Let's define our start time...
                // This needs to be initialized as close to when we'll use it as
                // possible,
                // as the delta from assignment to computed time could be too high
                if (startTime == 0)
                    startTime = System.currentTimeMillis();
                
                // Create timestamp for this frame
                videoTS = 1000 * (System.currentTimeMillis() - startTime);
                
                // Check for AV drift
                if (videoTS > recorder.getTimestamp())
                {
                    System.out.println(
                            "Lip-flap correction: "
                                    + videoTS + " : "
                                    + recorder.getTimestamp() + " -> "
                                    + (videoTS - recorder.getTimestamp()));
                    
                    // We tell the recorder to write this frame at this timestamp
                    recorder.setTimestamp(videoTS);
                }
                
                // Send the frame to the org.bytedeco.javacv.FFmpegFrameRecorder
                recorder.record(capturedFrame);
                
                if(!onLoop) break;
            }
            recorder.stop();
            process.interrupt();
            process = null;
        } catch (FFmpegFrameRecorder.Exception | AWTException ex) {
            Logger.getLogger(CaptureAV.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private Frame fromRobot() throws AWTException{
        Robot robot = new Robot();
        BufferedImage image = robot.createScreenCapture(r);
        BufferedImage img2 = new BufferedImage(r.width, r.height, BufferedImage.TYPE_3BYTE_BGR);
        for(int y=0; y<r.height; y++){
            for(int x=0; x<r.width; x++){
                img2.setRGB(x, y, image.getRGB(x, y));
            }
        }        
        return converter.convert(img2);
    }

    @Override
    public void run() {
        while(onLoop){
            doLoop();
        }
    }
}
