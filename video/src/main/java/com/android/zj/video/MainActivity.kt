package com.android.zj.video

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import java.io.File

class MainActivity : AppCompatActivity() {

    val TAG: String = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val lottieComposition = LottieCompositionFactory.fromAssetSync(this, "data.json").value
        val lottieDrawable = LottieDrawable()
        lottieDrawable.composition = lottieComposition

        RecordingOperation(Recorder(this, File(filesDir, "my.mp4")), FrameCreator(lottieDrawable)).start()
    }
}
