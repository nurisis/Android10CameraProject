package com.hinuri.android10cameraproject

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: CameraViewModel

    // file that store a captured image from camera
    private var imageFile: File? = null

    private val takePhoto = 111
    private val takeGallery = 222

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // create a viewmodel
        viewModel = ViewModelProvider(viewModelStore, CameraViewModelFactory(application)).get(CameraViewModel::class.java)

        tv_cta_camera.setOnClickListener {
            checkCameraPermission(ImageType.CAMERA)
        }
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
                // Launch camera app only when image file exists
                try {
                    // create a file to contain photos to be taken with the camera
                    imageFile = viewModel.createImageFile()

                    val imageUri =
                        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                            FileProvider.getUriForFile(
                                this,
                                        BuildConfig.APPLICATION_ID + ".provider",
                                imageFile!!)
                        else
                            Uri.fromFile(imageFile)

                    // launch camera app
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
            // After taking a picture
            takePhoto -> {
                if(imageFile == null) {
                    Log.e("LOG>>", "After taking a picture, imageFile null. ....")
                    return
                }
                if(resultCode != Activity.RESULT_OK) {
                    return
                }

                Glide.with(this).load(imageFile).into(iv_image)

                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Thread(Runnable {
                        // convert image file to bitmap using Glide
                        // description : http://bumptech.github.io/glide/doc/getting-started.html#background-threads
                        val futureTarget: FutureTarget<Bitmap> = Glide.with(this)
                            .asBitmap()
                            .load(imageFile)
                            .submit()

                        // save bitmap to gallery
                        if(viewModel.savePhotoAndroidQ(futureTarget.get()) == null)
                            runOnUiThread {
                                Toast.makeText(this, "Failed to save image in gallery ...", Toast.LENGTH_SHORT).show()
                            }

                        // delete a temporary file stored in the internal storage
                        viewModel.deleteImages(imageFile!!)

                    }).start()
                }
                else
                    // Notice that images have been added to the gallery
                    viewModel.notifyGallery(imageFile!!)

            }
            // After selecting an image from gallery.
            takeGallery -> {
                if(resultCode == Activity.RESULT_OK) {
                    // convert uri of user selected image to file
                    val file = viewModel.createImageFileAndroidQ(uri = data?.data!!)
                    Glide.with(this).load(file).into(iv_image)
                }
            }
        }
    }

    /**
     * Permission check. Use TedPermission library.
     * */
    private fun checkCameraPermission(type:ImageType) {
        TedPermission.with(this)
            .setPermissionListener(object : PermissionListener {
                // Allow permissions
                override fun onPermissionGranted() {
                    when(type) {
                        ImageType.CAMERA -> callCamera()
                        ImageType.GALLERY -> callGallery()
                    }
                }
                // Deny permissions
                override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
                }
            })
            .setDeniedMessage("Please allow permissions to use this app. \uD83D\uDE2D\uD83D\uDE2D")
            .apply {
                when(type){
                    ImageType.CAMERA -> {
                        // No storage permission required for accessing scoped storage from Android 10+
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
