package com.android.zj.video

import android.graphics.drawable.Drawable
import com.airbnb.lottie.LottieDrawable

class FrameCreator(private val lottieDrawable: LottieDrawable) {

    private val VIDEO_WIDTH_PX: Float = 600.0f

    init {
        lottieDrawable.scale = VIDEO_WIDTH_PX / lottieDrawable.intrinsicWidth
    }

    private val durationInFrames: Int = lottieDrawable.composition.durationFrames.toInt()
    private var currentFrame: Int = 0

    fun generateFrame(): Drawable {
        lottieDrawable.frame = currentFrame
        ++currentFrame
        return lottieDrawable
    }

    fun hasEnded() = currentFrame > durationInFrames
}