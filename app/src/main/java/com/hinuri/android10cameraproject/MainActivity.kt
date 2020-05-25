package com.hinuri.android10cameraproject

import android.Manifest
import android.app.Application
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.request.FutureTarget
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: CameraViewModel

    // 촬영한 이미지를 담는 이미지파일
    private var imageFile: File? = null
    private var imageUri:Uri? = null
    private val takePhoto = 111
    private val takeGallery = 222

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Create viewmodel
        val factory = CameraViewModelFactory(application)
        viewModel = ViewModelProvider(viewModelStore, factory).get(CameraViewModel::class.java)

        tv_cta_camera.setOnClickListener { checkCameraPermission(ImageType.CAMERA) }
        tv_cta_gallery.setOnClickListener {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                callGallery()
            else
                checkCameraPermission(ImageType.GALLERY)
        }
    }

    private fun callCamera() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure that there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                // 이미지 파일이 존재할 경우에만 카메라앱 실행
                try {

                    imageFile = viewModel.createImageFile()

                    imageUri =
                        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                            FileProvider.getUriForFile(
                                this,
                                        BuildConfig.APPLICATION_ID + ".provider",
                                imageFile!!)
                        else
                            Uri.fromFile(imageFile)

                    Log.d("LOG>>", "imageUri : $imageUri")

                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
                    startActivityForResult(takePictureIntent, takePhoto)

                }catch (e:IOException) {
                    imageFile = null
                    Log.e("LOG>>", "IOException while creating file : $e")
                }catch (e:Exception) {
                    imageFile = null
                    Log.e("LOG>>", "Exception while creating file : $e")
                }

            }
        }
    }

    private fun callGallery() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(intent, takeGallery)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when(requestCode) {
            // 카메라 촬영 후
            takePhoto -> {
                if(imageFile == null) {
                    Log.e("LOG>>", "카메라 촬영 후 imageFile null. ....")
                    return
                }

                Glide.with(this).load(imageFile).into(iv_image)

                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Thread(Runnable {
                        // 설명 : http://bumptech.github.io/glide/doc/getting-started.html#background-threads
                        val futureTarget: FutureTarget<Bitmap> = Glide.with(this)
                            .asBitmap()
                            .load(imageFile)
                            .submit()

                        if(viewModel.savePhotoAndroidQ(futureTarget.get()) == null)
                            runOnUiThread {
                                Toast.makeText(this, "Failed to save image in gallery ...", Toast.LENGTH_SHORT).show()
                            }

                        // 기존 파일 지움
                        viewModel.deleteImages(imageFile!!)

                    }).start()
                }
                else
                    viewModel.notifyGallery(imageFile!!)

            }
            // 갤러리에서 이미지 선택 후
            takeGallery -> {
                val file = viewModel.createImageFileAndroidQ(uri = data?.data!!)
                Log.d("LOG>>", "갤러리에서 => ${data?.data}, file : $file")
                Glide.with(this).load(file).into(iv_image)
            }
        }
    }

    private fun checkCameraPermission(type:ImageType) {
        TedPermission.with(this)
            .setPermissionListener(object : PermissionListener {
                // 권한 허용 시
                override fun onPermissionGranted() {
                    when(type) {
                        ImageType.CAMERA -> callCamera()
                        ImageType.GALLERY -> callGallery()
                    }
                }
                // 권한 거부..
                override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
                }
            })
            .setDeniedMessage("거절하시면 이미지를 올리실 수 없어요 \uD83D\uDE2D\uD83D\uDE2D")
            .apply {
                when(type){
                    ImageType.CAMERA -> {
                        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                            setPermissions(Manifest.permission.CAMERA)
                        else
                            setPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
                    }
                    ImageType.GALLERY -> setPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
            .check()
    }

}
