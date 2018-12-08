package com.android.zj.video

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable

class MainActivity : AppCompatActivity() {

    public var TAG: String = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val lottieComposition = LottieCompositionFactory.fromAssetSync(this, "data.json").value
        val lottieDrawable = LottieDrawable()
        lottieDrawable.composition = lottieComposition

        RecordingOperation(Recorder(),FrameCreator(lottieDrawable)).start()
    }
}
