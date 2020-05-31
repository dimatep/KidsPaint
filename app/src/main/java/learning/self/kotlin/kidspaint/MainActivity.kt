package learning.self.kotlin.kidspaint

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_brush_size.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    private var mImageButtonCurrentPaint : ImageButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //select the second element in the linear layout
        mImageButtonCurrentPaint = paint_colors_ll[1] as ImageButton //black color
        //
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_selected)
        )

        drawing_view.setBrushSize(20.toFloat())

        brush_ib.setOnClickListener{
            showBrushSizeChooseDialog()
        }

        gallery_ib.setOnClickListener {
            if(isReadStorageAllowed()){
                val pickPhotoIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI) //open gallery and pick image
                startActivityForResult(pickPhotoIntent, GALLERY)

            }else{
                requestStoragePermission()
            }
        }

        undo_ib.setOnClickListener {
            drawing_view.onClickUndo()
        }

        redo_ib.setOnClickListener {
            drawing_view.onClickRedo()
        }

        save_ib.setOnClickListener {
            if(isReadStorageAllowed()){
                BitmapAsyncTask(getBitmapFromView(drawing_view_container_fl)).execute()
            }else{
                requestStoragePermission()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode == Activity.RESULT_OK){
            if(requestCode == GALLERY){
                try {
                    if(data!!.data != null){ //if the user picked a image make it visible and set the new background
                        background_iv.visibility = View.VISIBLE
                        background_iv.setImageURI(data.data)
                    }else{ //the user did not select any image or something went wrong
                        Toast.makeText(this,"Error in parsing image or its corrupted", Toast.LENGTH_SHORT).show()
                    }
                }catch (ex : Exception){
                    ex.printStackTrace()
                }
            }
        }
    }

    private fun showBrushSizeChooseDialog(){
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush Size: ")

        val smallBtn = brushDialog.small_brush_ib
        smallBtn.setOnClickListener{
            drawing_view.setBrushSize(10.toFloat())
            brushDialog.dismiss()
        }

        val mediumBtn = brushDialog.medium_brush_ib
        mediumBtn.setOnClickListener{
            drawing_view.setBrushSize(20.toFloat())
            brushDialog.dismiss()
        }

        val largeBtn = brushDialog.large_brush_ib
        largeBtn.setOnClickListener{
            drawing_view.setBrushSize(30.toFloat())
            brushDialog.dismiss()
        }

        brushDialog.show()

    }

    fun paintClicked(view: View){//the button that was clicked on
        if(view != mImageButtonCurrentPaint){
            val imageBtn = view as ImageButton

            val colorTag = imageBtn.tag.toString() //getting the color string #RRGGBB
            drawing_view.setColor(colorTag)
            imageBtn.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_selected)) //set un-selected background

            mImageButtonCurrentPaint!!.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_normal))

            mImageButtonCurrentPaint = view

        }

    }

    private fun requestStoragePermission(){
        if(ActivityCompat
                .shouldShowRequestPermissionRationale(this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE).toString())){
            Toast.makeText(this,"Need permission to add a Background",Toast.LENGTH_SHORT).show()
        }

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == STORAGE_PERMISSION_CODE){
            if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {//user pressed ALLOW
                Toast.makeText(
                    this,
                    "Permission granted, now you can read storage files",
                    Toast.LENGTH_SHORT
                ).show()
            } else { //user pressed deny
                Toast.makeText(this, "Oops you just denied the permission",Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun isReadStorageAllowed() : Boolean{
        val result = ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE)

        return result == PackageManager.PERMISSION_GRANTED //return true if yes
    }

    private fun getBitmapFromView(view : View) : Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)

        val canvas = Canvas(returnedBitmap)
        val bgDrawble = view.background
        if(bgDrawble != null){
            bgDrawble.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }

        view.draw(canvas) // finalizing

        return returnedBitmap
    }

    private inner class BitmapAsyncTask(val mBitmap: Bitmap) : AsyncTask<Any,Void,String>(){

        private lateinit var mProgressDialog: Dialog

        override fun onPreExecute() {
            super.onPreExecute()
            showProgressDialog()
        }

        override fun doInBackground(vararg params: Any?): String {
            var result = ""

            if(mBitmap != null){
                try{ //need to be done to store something on a device
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)
                    val f = File(externalCacheDir!!.absoluteFile.toString()
                            + File.separator + "KidsPaintApp_"
                            + System.currentTimeMillis()/1000 + ".png")

                    val fos = FileOutputStream(f)
                    fos.write(bytes.toByteArray())
                    fos.close()

                    result = f.absolutePath
                }catch (e:Exception){
                    result = ""
                    e.printStackTrace()
                }
            }

            return result
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)

            closeProgressDialog()

            if(!result!!.isEmpty()){
                Toast.makeText(this@MainActivity, "File saved successfuly : $result" ,Toast.LENGTH_SHORT).show()
            }else{
                Toast.makeText(this@MainActivity,"Something went wrong while saving the file",Toast.LENGTH_SHORT).show()
            }

            MediaScannerConnection.scanFile(this@MainActivity, arrayOf(result), null){
                path, uri -> val shareIntent = Intent()
                shareIntent.action = Intent.ACTION_SEND
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                shareIntent.type = "image/png"

                startActivity(Intent.createChooser(
                    shareIntent,"Share"
                ))
            }
        }

        private fun showProgressDialog(){
            mProgressDialog = Dialog(this@MainActivity)
            mProgressDialog.setContentView(R.layout.dialog_custom_progress)
            mProgressDialog.show()
        }

        private fun closeProgressDialog(){
            mProgressDialog.dismiss()
        }
    }

    companion object{
        private const val STORAGE_PERMISSION_CODE = 1
        private const val GALLERY = 2
    }
}
