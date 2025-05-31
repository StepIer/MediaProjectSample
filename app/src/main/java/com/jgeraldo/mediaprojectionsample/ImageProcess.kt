package com.jgeraldo.mediaprojectionsample

import android.speech.RecognizerIntent
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer

object ImageProcess {

    fun process(recognizer: TextRecognizer, image: InputImage) {
        val result = recognizer.process(image)
            .addOnSuccessListener { visionText ->
                // Task completed successfully
                // ...
                visionText.textBlocks.forEach {
                    Log.d("rawr","Success text = ${it.text}")
                    Log.d("rawr","Success boundingBox = ${it.boundingBox}")
                }
            }
            .addOnFailureListener { e ->
                // Task failed with an exception
                // ...
                Log.d("rawr","Failure exception = $e")
            }
    }
}