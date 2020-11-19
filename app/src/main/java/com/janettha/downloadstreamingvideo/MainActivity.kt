package com.janettha.downloadstreamingvideo

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import org.jcodec.api.FrameGrab
import org.jcodec.common.AndroidUtil
import org.jcodec.common.io.FileChannelWrapper
import org.jcodec.common.io.NIOUtils
import org.jcodec.common.io.TiledChannel
import org.jcodec.common.model.Picture
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*


@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    val STORAGE_PERMISSION_CODE : Int = 1000

    val MOVIE_LOCATION_ORIGINAL = "http://jcodec.org/downloads/sample.mov"
    val URL : String = "https://gripplaces.s3.us-east-1.wasabisys.com/media/device/95270001GE1CX435/15.mp4"
    val subPath = "${System.currentTimeMillis()}"

    var available: LinkedList<Bitmap> = LinkedList<Bitmap>()

    private val executors: AppExecutors by lazy { AppExecutors() }

    var downloadReference: Long = 0


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        download.setOnClickListener {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_DENIED){
                    // Permission denied, request it

                    //show pop up
                    requestPermissions(
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        STORAGE_PERMISSION_CODE
                    )
                } else {
                    // Permission already granted, perform download
                    startDownloading()
                }
            } else {
                startDownloading()
            }
        }

        show.setOnClickListener {
            // con MediaMetadataRetriever (nativa de Android :: funciona descargando o simplemente accediendo al archivo)
            getFrames()
            // con JCODEC (librería externa) :: no es posible abrir archivos
            //doInBackground()
        }
    }

    private fun getFrames() { // MediaMetadataRetriever
        var bitmap: Bitmap

        var file = File(
            this.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            subPath+".mov"
        )
        Log.d("file", "getFrames: " + file.exists() + " :: absolutePath: " + file.absolutePath)

        videoView.setVideoURI(Uri.fromFile(file))
        videoView.start()

        val mMMR = MediaMetadataRetriever()
        var inputStream = FileInputStream(file)
        var uri = Uri.fromFile(file)
        mMMR.setDataSource(this, uri)

        val timeMs =
            mMMR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) // video time in ms

        val totalVideoTime = 1000 * Integer.valueOf(timeMs) // total video time, in uS
        val deltaT = 250000
        var time_us = 1
        while (time_us < totalVideoTime) {

            bitmap = mMMR.getFrameAtTime(
                time_us.toLong(),
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )!! // extract a bitmap element from the closest key frame from the specified time_us
            if (bitmap == null) break
            else {
                available.add(bitmap)
            }
            //frame = Frame.Builder().bitmap(bitmap).build() // generates a "Frame" object, which can be fed to a face detector
            //faces = detector.detect(frame) // detect the faces (detector is a FaceDetector)
            Log.d("time_us", "extractFramesFromVideo: " + time_us + "/" + totalVideoTime)
            time_us += deltaT
        }

        Log.d("bitmapList", "extractFramesFromVideo: " + available.size)
        /*var i = 0
        while(i < available.size-1) {
            Thread.sleep(250)*/
        //Log.d("available bitmaps", "extractFramesFromVideo: "+i+" :: "+available[i])
        executors.mainThread().execute {
            if(available.size>0)
                display()
        }
    }

    private fun doInBackground(){ // getFrames with JCODEC
        var file = File(
            this.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            subPath+".mov"
        ) // Set Your File Name
        Log.d("file", "getFrames: " + file.exists() + " :: absolutePath: " + file.absolutePath)

        //-------- getFrames from WEB
        var ch: FileChannelWrapper? = null
        ch = NIOUtils.readableFileChannel(file.absolutePath)
        var ch1 = TiledChannel(ch)

        //if (frameGrab == null) frameGrab = AndroidFrameGrab.createAndroidFrameGrab(ch1) // -- WEB
        val grab = FrameGrab.createFrameGrab(NIOUtils.readableChannel(file))   // TODO: Aquí surge el problema
        var picture: Picture
        var i = 0
        while (null != grab.nativeFrame.also { picture = it }) {
            println(
                picture.getWidth().toString() + "x" + picture.getHeight() + " " + picture.getColor()
            )
            val bitmap: Bitmap = AndroidUtil.toBitmap(picture)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream("frame$i.png"))
            i++
            available.add(bitmap)
        }
        executors.mainThread().execute {
            if(available.size>0)
                display()
        }
    }

    private fun display() {
        progress.visibility = View.GONE
        image.setImageBitmap(available[available.size / 2])
    }

    private fun startDownloading() {
        val request = DownloadManager.Request(Uri.parse(MOVIE_LOCATION_ORIGINAL))

        //allow types of networks to download file(s) by default both
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        request.setTitle("Downloaded")
        request.setDescription("File is downloading...")

        request.allowScanningByMediaScanner()
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        //request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${System.currentTimeMillis()}")
        request.setDestinationInExternalFilesDir(
            this,
            Environment.DIRECTORY_DOWNLOADS,
            subPath+".mov"
        ) //file:///storage/emulated/0/Android/data/com.janettha.downloadstreamingvideo/files/Download/1605770944248

        // get download service and enqueue file
        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadReference = manager.enqueue(request)
        registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    private val downloadReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            //check if the broadcast message is for our Enqueued download
            val referenceId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            intent.data?.path
            if (downloadReference === referenceId) {
                //val cancelDownload: Button = findViewById(R.id.cancelDownload) as Button
                //cancelDownload.setEnabled(false)
                val toast = Toast.makeText(
                    this@MainActivity,
                    "Download completed.", Toast.LENGTH_LONG
                )
                toast.setGravity(Gravity.TOP, 25, 400)
                toast.show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode){
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    startDownloading()
                else {
                    // Permission from popup was denied, show error message
                    Toast.makeText(this, "Permission denied!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

}

