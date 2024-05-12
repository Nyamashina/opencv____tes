package com.example.opencv____tes


import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.opencv____tes.databinding.FragmentFirstBinding
import com.googlecode.tesseract.android.TessBaseAPI
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class FirstFragment : Fragment() {
    //opencvのロード　
    companion object {
        init {
            if (!OpenCVLoader.initDebug()) {
                Log.d("OpenCV", "OpenCV not loaded")
            } else {
                Log.d("OpenCV", "OpenCV loaded")
            }
        }
    }
    private var languages = arrayOf(
        "English",
        "French",
        "Japanese (JPN)",
        "Japanese (JPN Vertical)",
        "Japanese",
        "Japanese_vert"
    )
    val languageCodes = arrayOf(
        "eng",
        "fra",
        "jpn",
        "jpn_vert",
        "Japanese",
        "Japanese_vert"
    ) //訓練データの指定用
    private var selectedLanguageCode: String = "jpn"


    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!
    private var TAG = "FirstFragment"
    private lateinit var imageResultLauncher: ActivityResultLauncher<String>
    private lateinit var imageUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 画像選択のためのActivityResultLauncherを登録
        imageResultLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                setImageUri(uri)
            } else {
                Log.d(TAG, "Image selection URI:null")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.languageSpinner.adapter = adapter



        // 画像選択ボタンのクリックリスナー
        binding.btnSelectImage.setOnClickListener {
            Log.d(TAG, "Select image button clicked")
            openImageSelector()
        }

        // 画像処理ボタンのクリックリスナー
        binding.btnProcessImage.setOnClickListener {
            Log.d(TAG, "Process image button clicked")
            if (::imageUri.isInitialized) {
                processImage()
            } else {
                // 画像が選択されていない場合の処理
                Toast.makeText(requireContext(), "画像が選択されていません", Toast.LENGTH_SHORT).show()
            }
        }

        binding.languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedLanguageCode = languageCodes[position]
                Log.d(TAG, "Selected language code: $selectedLanguageCode")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                Log.d(TAG, "onNothingSelected")

            }
        }


    }

    private fun setImageUri(uri: Uri) {
        imageUri = uri
        binding.imageViewOriginal.setImageURI(uri)
        binding.textViewPath.text = uri.toString()
        Log.d(TAG, "Image selected: $uri")
    }

    private fun openImageSelector() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        imageResultLauncher.launch(intent.type!!)
    }

    private fun processImage() {
        Log.d(TAG, "Image Uri: $imageUri")

        try {
            val tempFile = createImageFileFromUri(requireContext(), imageUri)

            // OpenCVを使った画像の前処理
            val originalBitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
            val processedBitmap = preprocessImageForOcr(originalBitmap,
                binding.switchGrayscale.isChecked,
                binding.switchBinarization.isChecked,
                binding.switchDenoise.isChecked)
            binding.preprocess.setImageBitmap(processedBitmap)


            val tessDataPath = context?.filesDir?.path ?: ""
            copyTrainedDataToInternalStorage(requireContext(), "$selectedLanguageCode.traineddata", tessDataPath)

            Log.d(TAG, "TessDataPath: $tessDataPath and language: $selectedLanguageCode")

            val tessBaseApi = TessBaseAPI()
            if (!tessBaseApi.init(tessDataPath, selectedLanguageCode)) {
                Log.e(TAG, "Tesseract initialization failed for language code: $selectedLanguageCode")
                return
            }

            // OCR処理の実行
            tessBaseApi.setImage(processedBitmap)
            val recognizedText = tessBaseApi.utF8Text

            // 結果をUIに反映
            binding.resultText.text = recognizedText
            Log.d(TAG, "recognizedText: $recognizedText")

            // Tesseract OCRエンジンの終了
            tessBaseApi.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ${e.message}", e)
            Toast.makeText(requireContext(), "OCR処理に失敗しました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun preprocessImageForOcr(bitmap: Bitmap, grayscale: Boolean, binarization: Boolean, denoise: Boolean): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        if (grayscale) {
            // グレースケール変換
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY)
        }

        if (binarization) {
            // 二値化
            Imgproc.threshold(mat, mat, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)
        }

        if (denoise) {
            // ノイズ除去
            val morphKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
            Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_CLOSE, morphKernel)
        }

        val preprocessedBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, preprocessedBitmap)
        return preprocessedBitmap
    }



    private fun copyTrainedDataToInternalStorage(context: Context, trainedDataName: String, tessDataPath: String) {
        try {
            // assets内のtraineddataファイルのストリームを開く
            context.assets.open("tessdata/$trainedDataName").use { inputStream ->
                // コピー先のパスを指定
                val tessDataFolderPath = File(tessDataPath, "tessdata")
                if (!tessDataFolderPath.exists()) tessDataFolderPath.mkdirs()
                val destinationFile = File(tessDataFolderPath, trainedDataName)

                // ファイルがまだ存在しない場合のみコピーを実行
                if (!destinationFile.exists()) {
                    FileOutputStream(destinationFile).use { outputStream ->
                        // ファイルのコピー
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy trained data: ${e.message}", e)
        }
    }



    private fun createImageFileFromUri(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        val tempFile = File(context.cacheDir, "tempImage.jpg")
        val outputStream = FileOutputStream(tempFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.close()
        inputStream?.close()
        Log.d(TAG, "Temporary file created: ${tempFile.absolutePath}")
        return tempFile
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
