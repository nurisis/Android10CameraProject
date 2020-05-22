package com.hinuri.android10cameraproject

import android.Manifest
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

    // 촬영한 이미지를 담는 이미지파일
    private var imageFile: File? = null
    private var imageUri:Uri? = null
    private val takePhoto = 111
    private val takeGallery = 222

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

                    imageFile = createImageFile()

                    imageUri =
                        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                            FileProvider.getUriForFile(
                                this,
                                BuildConfig.APPLICATION_ID + ".provider",
                                imageFile!!)
                        else
                            Uri.fromFile(imageFile)

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

                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Thread(Runnable {
                        // 설명 : http://bumptech.github.io/glide/doc/getting-started.html#background-threads
                        val futureTarget: FutureTarget<Bitmap> = Glide.with(this)
                            .asBitmap()
                            .load(imageFile)
                            .submit()

                        if(savePhotoAndroidQ(futureTarget.get()) == null)
                            runOnUiThread {
                                Toast.makeText(this, "Failed to save image in gallery ...", Toast.LENGTH_SHORT).show()
                            }

                        // 기존 파일 지움
                        deleteImages(imageFile!!)

                    }).start()

                    Glide.with(this).load(imageFile).into(iv_image)
                }
                else
                    notifyGallery(imageFile!!)

            }
            // 갤러리에서 이미지 선택 후
            takeGallery -> {
                val file = createImageFileAndroidQ(uri = data?.data!!)
                Log.d("LOG>>", "갤러리에서 => ${data?.data}, file : $file")
                Glide.with(this).load(file).into(iv_image)
            }
        }
    }

    private fun savePhotoAndroidQ(bitmap: Bitmap) : Uri? {
        try {
            val relativePath = Environment.DIRECTORY_PICTURES + File.separator + getString(R.string.app_name)
            val mimeType = "image/*"
            val fileName = SimpleDateFormat("YYYY_MM_dd_HH:mm:ss").format(Date())+".jpg"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.IS_PENDING, 1)
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            }

            val resolver = contentResolver ?: return null

            val collection = MediaStore.Images.Media
                .getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)

            if(uri == null) {
                Log.e("LOG>>","Failed to create new  MediaStore record.")
                return null
            }

            val outputStream = resolver.openOutputStream(uri)

            if(outputStream == null) {
                Log.e("LOG>>", "Failed to get output stream.")
            }

            val saved = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            if(!saved) {
                Log.e("LOG>>","파일을 앨범에 저장하는데 실패 ..했 ....")
            }

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            return uri
        } catch (e:Exception) {
            Log.e("LOG>>", "error : $e")
            return null
        }
    }


    @Throws(IOException::class)
    private fun createImageFile() : File {
        // api 29부터는, MediaStore로 파일을 저장해야하므로, 여기서는 일단 앱 내 저장소에서 파일을 만듬.
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            // 이미지가 저장될 디렉토리
            val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            Log.d("LOG>>", "storageDir : $storageDir")
            // 이미지를 담을 파일 생성
            return File.createTempFile(
                SimpleDateFormat("yyMMdd_HH:mm:ss").format(Date()), /* prefix */
                ".jpeg", /* suffix */
                storageDir /* directory */
            )
        }
        // api 28 이하의 경우, 바로 디바이스 앨범에 이미지를 담을 파일을 생성함
        else {
            val storageDir = File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DCIM
                )
                , getString(R.string.app_name)
            )

            // 해당 폴더가 없으면 생성
            if(!storageDir.exists())
                storageDir.mkdir()

            // 이미지를 담을 파일 생성
            return File.createTempFile(
                SimpleDateFormat("yyMMdd_HH:mm:ss").format(Date()), /* prefix */
                ".jpeg", /* suffix */
                storageDir /* directory */
            )
        }
    }

    // 서버에 이미지를 올리기 위해 저장했던 (앱 용 private directory에 저장된) 이미지들 삭제
    private fun deleteImages(file:File) {
        try{
            file.delete()
        }catch (e:Exception){
            Log.e("LOG>>", "error while deleting image : $e")
        }
    }

    private fun createImageFileAndroidQ(uri:Uri): File?{
        return try {
            val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r", null)
            val inputStream = FileInputStream(parcelFileDescriptor?.fileDescriptor)

            val file = File(cacheDir, contentResolver.getFileName(uri))

            val outputStream = FileOutputStream(file)

            inputStream.copyTo(outputStream)

            file
        }catch (e:Exception) {
            Log.e("LOG>>","createImageFileAndroidQ ERror : $e")
            null
        }
    }

    private fun ContentResolver.getFileName(fileUri: Uri): String {
        var name = ""
        val returnCursor = this.query(fileUri, null, null, null, null)
        if (returnCursor != null) {
            val nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            returnCursor.moveToFirst()
            name = returnCursor.getString(nameIndex)
            returnCursor.close()
        }

        return name
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

    /**
     * 앨범에 새로운 사진이 추가되었다고 알림.
     * */
    private fun notifyGallery(imageFile:File) {
        sendBroadcast(
            Intent(
                Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                Uri.fromFile(imageFile!!)
            )
        )
    }

}
