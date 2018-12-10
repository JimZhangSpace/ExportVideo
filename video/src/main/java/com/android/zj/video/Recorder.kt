package com.android.zj.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import java.io.File
import java.io.IOException
import java.io.InputStream

class Recorder(context: Context, file: File) {

    var TAG: String = "Recorder"
    private val VERBOSE: Boolean = true

    private val MIME_TYPE = "video/avc"
    private val WIDTH = 640
    private val HEIGHT = 480
    private val BIT_RATE = 4000000
    private val FRAMES_PER_SECOND = 4
    private val IFRAME_INTERVAL = 5
    private val NUM_FRAMES = 8

    // "live" state during recording
    private var mBufferInfo: MediaCodec.BufferInfo? = null
    private var mEncoder: MediaCodec? = null
    private var mMuxer: MediaMuxer? = null
    private var mInputSurface: Surface? = null
    private var mTrackIndex: Int = 0
    private var mMuxerStarted: Boolean = false
    private var mFakePts: Long = 0
    private val mContext: Context = context

    init {
        prepareEncoder(file)
    }

    fun nextFrame(currentFrame: Drawable) {

        drainEncoder(false)
        val canvas = mInputSurface?.lockCanvas(null)
        try {
            val inputStream: InputStream = mContext.resources.openRawResource(R.raw.ic_launcher)
            val bitmapDrawable: BitmapDrawable = BitmapDrawable(inputStream)
            val test: Bitmap = bitmapDrawable.bitmap

//            val bitmap: Bitmap = BitmapFactory.decodeResource(mContext.resources, R.drawable.bg_oval_white)
            canvas?.drawBitmap(test, 0f, 0f, null)
            currentFrame.draw(canvas)
        } finally {
            mInputSurface?.unlockCanvasAndPost(canvas)
        }

    }

    fun end() {
        drainEncoder(true)
        releaseEncoder()
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val TIMEOUT_USEC: Int = 10000
        if (VERBOSE) {
            Log.d(TAG, "drainEncoder(" + endOfStream + ")")
        }

        if (endOfStream) {
            if (VERBOSE) Log.d(TAG, "sending EOS to encoder")
            mEncoder?.signalEndOfInputStream()
        }

        var encoderOutputBuffers = mEncoder!!.getOutputBuffers()
        while (true) {
            val encoderStatus = mEncoder!!.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC.toLong())
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break      // out of while
                } else {
                    if (VERBOSE) Log.d(TAG, "no output available, spinning to await EOS")
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mEncoder?.getOutputBuffers()
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (mMuxerStarted) {
                    throw RuntimeException("format changed twice")
                }
                val newFormat = mEncoder?.getOutputFormat()
                Log.d(TAG, "encoder output format changed: $newFormat")

                // now that we have the Magic Goodies, start the muxer
                mTrackIndex = mMuxer!!.addTrack(newFormat)
                mMuxer?.start()
                mMuxerStarted = true
            } else if (encoderStatus < 0) {
                Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: $encoderStatus")
                // let's ignore it
            } else {
                val encodedData = encoderOutputBuffers[encoderStatus]
                        ?: throw RuntimeException("encoderOutputBuffer " + encoderStatus +
                                " was null")

                if (mBufferInfo?.flags?.and(MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG")
                    mBufferInfo?.size = 0
                }

                if (mBufferInfo?.size != 0) {
                    if (!mMuxerStarted) {
                        throw RuntimeException("muxer hasn't started")
                    }

                    // adjust the ByteBuffer values to match BufferInfo
                    encodedData.position(mBufferInfo!!.offset)
                    encodedData.limit(mBufferInfo!!.offset + mBufferInfo!!.size)
                    mBufferInfo?.presentationTimeUs = mFakePts
                    mFakePts += 1000000L / FRAMES_PER_SECOND

                    mMuxer?.writeSampleData(mTrackIndex, encodedData, mBufferInfo)
                    if (VERBOSE) Log.d(TAG, "sent " + mBufferInfo!!.size + " bytes to muxer")
                }

                mEncoder?.releaseOutputBuffer(encoderStatus, false)

                if (mBufferInfo!!.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    if (!endOfStream) {
                        Log.w(TAG, "reached end of stream unexpectedly")
                    } else {
                        if (VERBOSE) Log.d(TAG, "end of stream reached")
                    }
                    break      // out of while
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun prepareEncoder(outputFile: File) {
        mBufferInfo = MediaCodec.BufferInfo()

        val format = MediaFormat.createVideoFormat(MIME_TYPE, WIDTH, HEIGHT)

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAMES_PER_SECOND)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
        if (VERBOSE) Log.d(TAG, "format: $format")

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE)
        mEncoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mInputSurface = mEncoder?.createInputSurface()
        mEncoder?.start()

        // Create a MediaMuxer.  We can't add the video track and start() the muxer here,
        // because our MediaFormat doesn't have the Magic Goodies.  These can only be
        // obtained from the encoder after it has started processing data.
        //
        // We're not actually interested in multiplexing audio.  We just want to convert
        // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
        if (VERBOSE) Log.d(TAG, "output will go to $outputFile")
        mMuxer = MediaMuxer(outputFile.toString(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        mTrackIndex = -1
        mMuxerStarted = false
    }


    private fun releaseEncoder() {
        if (VERBOSE) Log.d(TAG, "releasing encoder objects")
        if (mEncoder != null) {
            mEncoder?.stop()
            mEncoder?.release()
            mEncoder = null
        }
        if (mInputSurface != null) {
            mInputSurface?.release()
            mInputSurface = null
        }
        if (mMuxer != null) {
            mMuxer?.stop()
            mMuxer?.release()
            mMuxer = null
        }
    }
}