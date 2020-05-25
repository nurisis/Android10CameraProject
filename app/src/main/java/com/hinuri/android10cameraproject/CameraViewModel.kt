package com.hinuri.android10cameraproject

import android.app.Application
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class CameraViewModelFactory(private val myApplication: Application) : ViewModelProvider.Factory{
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return modelClass.getConstructor(Application::class.java).newInstance(myApplication)
    }
}

class CameraViewModel(
    private val myApplication: Application
) :AndroidViewModel(myApplication) {

    fun savePhotoAndroidQ(bitmap: Bitmap) : Uri? {
        try {
            val relativePath = Environment.DIRECTORY_PICTURES + File.separator + myApplication.getString(R.string.app_name)
            val mimeType = "image/*"
            val fileName = SimpleDateFormat("YYYY_MM_dd_HH:mm:ss").format(Date())+".jpg"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.IS_PENDING, 1)
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            }

            val resolver = myApplication.contentResolver ?: return null

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

    fun createImageFileAndroidQ(uri:Uri): File?{
        return try {
            val parcelFileDescriptor = myApplication.contentResolver.openFileDescriptor(uri, "r", null)
            val inputStream = FileInputStream(parcelFileDescriptor?.fileDescriptor)

            val file = File(myApplication.cacheDir, myApplication.contentResolver.getFileName(uri))
            val outputStream = FileOutputStream(file)

            inputStream.copyTo(outputStream)

            file
        }catch (e:Exception) {
            Log.e("LOG>>","createImageFileAndroidQ ERror : $e")
            null
        }
    }

    @Throws(IOException::class)
    fun createImageFile() : File {
        // api 29부터는, MediaStore로 파일을 저장해야하므로, 여기서는 일단 앱 내 저장소에서 파일을 만듬.
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            // 이미지가 저장될 디렉토리
            val storageDir: File? = myApplication.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
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
                    Environment.DIRECTORY_PICTURES
                )
                , myApplication.getString(R.string.app_name)
            )

            // 해당 폴더가 없으면 생성
            if(!storageDir.exists())
                storageDir.mkdir()

            // 이미지를 담을 파일 생성
            return File.createTempFile(
                SimpleDateFormat("yyMMdd_HH:mm:ss").format(Date()), /* prefix */
                ".jpeg", /* suffix */
                storageDir /* directory */
            ).also { Log.d("LOG>>","10 이하 파일생성 : $it") }
        }
    }


    /**
     * 앨범에 새로운 사진이 추가되었다고 알림.
     * */
    fun notifyGallery(imageFile:File) {
        myApplication.sendBroadcast(
            Intent(
                Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                Uri.fromFile(imageFile!!)
            )
        )
    }

    // 서버에 이미지를 올리기 위해 저장했던 (앱 용 private directory에 저장된) 이미지들 삭제
    fun deleteImages(file:File) {
        try{
            file.delete()
        }catch (e:Exception){
            Log.e("LOG>>", "error while deleting image : $e")
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

}