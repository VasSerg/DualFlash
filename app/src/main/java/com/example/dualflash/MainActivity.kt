package com.example.dualflash


import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSession
import androidx.window.area.WindowAreaSessionPresenter
import androidx.window.core.ExperimentalWindowApi
import com.bumptech.glide.Glide
import com.example.dualflash.databinding.ActivityMainBinding
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import kotlin.math.pow


@OptIn(ExperimentalWindowApi::class)
 class MainActivity : AppCompatActivity(), WindowAreaPresentationSessionCallback  {

    //<editor-fold desc="init stuff">
    private lateinit var windowAreaController: WindowAreaController
    private lateinit var displayExecutor: Executor
    private var windowAreaSession: WindowAreaSession? = null
    private var windowAreaInfo: WindowAreaInfo? = null
    private var capabilityStatus: WindowAreaCapability.Status =
        WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED

    //private val dualScreenOperation = WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA
    //private val rearDisplayOperation = WindowAreaCapability.Operation.OPERATION_TRANSFER_ACTIVITY_TO_AREA


    private lateinit var binding: ActivityMainBinding

    private val presentOperation = WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA
    //</editor-fold>



    private var ind = 1

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )


        super.onCreate(savedInstanceState)
        //viewfront = layoutInflater.inflate(R.layout.activity_main, null, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT


        // button to press
        binding.fab.setOnClickListener {
            toggleDualScreenMode()
        }


        binding.fabphoto.setOnClickListener {
            setImage()
        }

        binding.fabdelphoto.setOnClickListener {
            delImage()
        }


        val imgfront: ImageView = findViewById(R.id.backImage)

        //<editor-fold desc="buttons">
        binding.fab0.setOnClickListener {
            ind = 0
            imgfront.setImageResource(R.drawable.w)
        }
        binding.fab1.setOnClickListener {
            ind = 1
            imgfront.setImageResource(R.drawable.y)
        }

        binding.fab2.setOnClickListener {
            ind = 2
            imgfront.setImageResource(R.drawable.u)
        }
        binding.fab3.setOnClickListener {
            ind = 3
            imgfront.setImageResource(R.drawable.t)
        }
        binding.fab4.setOnClickListener {
            ind = 4
            imgfront.setImageResource(R.drawable.r)
        }
        binding.fab5.setOnClickListener {
            ind = 5
            imgfront.setImageResource(R.drawable.q)
        }
        binding.fab6.setOnClickListener {
            ind = 6
            imgfront.setImageResource(R.drawable.i)
        }
        binding.fab7.setOnClickListener {
            ind = 7
            imgfront.setImageResource(R.drawable.e)
        }
        //</editor-fold>


   displayExecutor = ContextCompat.getMainExecutor(this)
   windowAreaController = WindowAreaController.getOrCreate()

   lifecycleScope.launch(Dispatchers.Main) {
       lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
           windowAreaController.windowAreaInfos
               .map { info -> info.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING } }
               .onEach { info -> windowAreaInfo = info }
               .map { it?.getCapability(presentOperation)?.status ?: WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED }
               .distinctUntilChanged()
               .collect {
                   capabilityStatus = it
               }
       }
   }

   // check if it is a flipscreen
   when (capabilityStatus) {
       WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED -> {
           // The selected display mode is not supported on this device.
       }
       WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNAVAILABLE -> {
           // The selected display mode is not currently available to be enabled.
       }
       WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE -> {
           // The selected display mode is currently available to be enabled.
       }
       WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE -> {
           // The selected display mode is already active.
       }
       else -> {
           // The selected display mode status is unknown.
       }
   }


    // draw
    //<editor-fold desc="draw init stuff">

    val eraseBtn = findViewById<ImageButton>(R.id.eraser)
    val undoBtn = findViewById<ImageButton>(R.id.undo)


    val sliderBtn = findViewById<Slider>(R.id.sliderSize)
    val sliderColBtn = findViewById<Slider>(R.id.sliderCol)
    val mDrawLayout = findViewById<Draw2D>(R.id.Draw2D)

    undoBtn.setOnClickListener {
        mDrawLayout.undo()
    }

    eraseBtn.setOnClickListener {
        DrawData.pathList.clear()
        DrawData.paintList.clear()
        mDrawLayout.invalidate()
    }



    sliderBtn.value = 10/20f

    sliderBtn.addOnChangeListener { _, _, _ ->
        mDrawLayout.strokeWidth = sliderBtn.value*20
    }

    sliderColBtn.value = 0.0778f


    val states = arrayOf(
        intArrayOf(android.R.attr.state_enabled), // enabled
        intArrayOf(-android.R.attr.state_enabled) // disabled
    )
    val colors = intArrayOf(mDrawLayout.paintColor,mDrawLayout.paintColor)
    val myList = ColorStateList(states, colors)
    sliderColBtn.thumbTintList = myList


    sliderColBtn.addOnChangeListener { _, _, _ ->
        val men = 100000
        val k = 0.53248
        val rag = 16.0.pow(4.0)
        val dist = (men/(rag) * 16.0.pow(2.0) -1*3*k).toInt()*2


        val m1 = 2*( (1 - 0.49) * - rag - 0 + 10).toInt()-1
        val m2 = ((2*(0.51 * - rag + 0 ).toInt()- 16.0.pow(5.0).toInt()*15)*0.99999-20).toInt()


        val normalize = (sliderColBtn.value*men - (sliderColBtn.value*men % dist))*1f/men


        if (sliderColBtn.value == 0f){
            mDrawLayout.paintColor = Color.WHITE
        }
        else if (sliderColBtn.value == 1f){
            mDrawLayout.paintColor = Color.BLACK
        }
        else if ((sliderColBtn.value > 0.49f) and (sliderColBtn.value < 0.51f )){
            mDrawLayout.paintColor = ((m1 + m2)/2)
        }
        else if (sliderColBtn.value < 0.5){
            mDrawLayout.paintColor = 2*( (1 - normalize) * - rag * 0.9986 - 0 -41).toInt()-1
        }
        else{
            mDrawLayout.paintColor = ((2*(normalize * - rag + 0 ).toInt()- 16.0.pow(5.0).toInt()*15)*0.99999-20).toInt()
        }

        val statess = arrayOf(
            intArrayOf(android.R.attr.state_enabled), // enabled
            intArrayOf(-android.R.attr.state_enabled) // disabled
        )
        val colorss = intArrayOf(mDrawLayout.paintColor,mDrawLayout.paintColor)
        val myListt = ColorStateList(statess, colorss)
        sliderColBtn.thumbTintList = myListt

    }
    //</editor-fold>
}


    private fun toggleDualScreenMode() {
       if (windowAreaSession != null) {
           windowAreaSession?.close()
       }
       else{
           windowAreaInfo?.token?.let { token ->
               windowAreaController.presentContentOnWindowArea(
                   token = token,
                   activity = this,
                   executor = displayExecutor,
                   windowAreaPresentationSessionCallback = this
               )}
       }
    }

    @SuppressLint("InflateParams")
    override fun onSessionStarted(session: WindowAreaSessionPresenter) {


        val imgfront: ImageView = binding.root.findViewById(R.id.backImage)

        windowAreaSession = session

        val ltInflater = layoutInflater

        val view = ltInflater.inflate(R.layout.small_main, null, false)
        session.setContentView(view)

        DrawData.DF.add(view.findViewById(R.id.Draw2DFlip))


        DrawData.DF[0].invalidate()


        val img: ImageView = view.findViewById(R.id.mainImage)


        //<editor-fold desc="set image">
        if (ind == 0){
            img.setImageResource(R.drawable.w)
            imgfront.setImageResource(R.drawable.w)
        }
        if (ind == 1){
            img.setImageResource(R.drawable.y)
            imgfront.setImageResource(R.drawable.y)
        }
        if (ind == 2){
            img.setImageResource(R.drawable.u)
            imgfront.setImageResource(R.drawable.u)
        }
        if (ind == 3){
            img.setImageResource(R.drawable.t)
            imgfront.setImageResource(R.drawable.t)
        }
        if (ind == 4){
            img.setImageResource(R.drawable.r)
            imgfront.setImageResource(R.drawable.r)
        }
        if (ind == 5){
            img.setImageResource(R.drawable.q)
            imgfront.setImageResource(R.drawable.q)
        }
        if (ind == 6){
            img.setImageResource(R.drawable.i)
            imgfront.setImageResource(R.drawable.i)
        }
        if (ind == 7){
            img.setImageResource(R.drawable.e)
            imgfront.setImageResource(R.drawable.e)
        }
        //</editor-fold>

        val galImgMain: ImageView = findViewById(R.id.GalImage)

        val galImg: ImageView = view.findViewById(R.id.GalImageFlip)
        PhotoPicker.FrontImage = galImg
        val frontBg: ImageView = view.findViewById(R.id.BgGalFront)
        PhotoPicker.FrontBg = frontBg

        if (PhotoPicker.Show) {
            frontBg.setImageResource(R.drawable.bg_dark)
            Glide.with(this).load(PhotoPicker.ImgUri).into(galImgMain)
            Glide.with(this).load(PhotoPicker.ImgUri).into(galImg)
        }

        //<editor-fold desc="buttons">
        binding.fab0.setOnClickListener {
            img.setImageResource(R.drawable.w)
            imgfront.setImageResource(R.drawable.w)
        }
        binding.fab1.setOnClickListener{
            img.setImageResource(R.drawable.y)
            imgfront.setImageResource(R.drawable.y)
        }
        binding.fab2.setOnClickListener{
            img.setImageResource(R.drawable.u)
            imgfront.setImageResource(R.drawable.u)
        }
        binding.fab3.setOnClickListener{
            img.setImageResource(R.drawable.t)
            imgfront.setImageResource(R.drawable.t)
        }
        binding.fab4.setOnClickListener{
            img.setImageResource(R.drawable.r)
            imgfront.setImageResource(R.drawable.r)
        }
        binding.fab5.setOnClickListener{
            img.setImageResource(R.drawable.q)
            imgfront.setImageResource(R.drawable.q)
        }
        binding.fab6.setOnClickListener{
           img.setImageResource(R.drawable.i)
           imgfront.setImageResource(R.drawable.i)
        }
        binding.fab7.setOnClickListener{
           img.setImageResource(R.drawable.e)
           imgfront.setImageResource(R.drawable.e)
        }
        //</editor-fold>

    }



    override fun onSessionEnded(t: Throwable?)                {
       windowAreaSession = null

        DrawData.DF.clear()

        val imgfront: ImageView = findViewById(R.id.backImage)
        //<editor-fold desc="buttons">
        binding.fab0.setOnClickListener {
            ind = 0
            imgfront.setImageResource(R.drawable.w)
        }
        binding.fab1.setOnClickListener {
            ind = 1
            imgfront.setImageResource(R.drawable.y)
        }

        binding.fab2.setOnClickListener {
            ind = 2
            imgfront.setImageResource(R.drawable.u)
        }
        binding.fab3.setOnClickListener {
            ind = 3
            imgfront.setImageResource(R.drawable.t)
        }
        binding.fab4.setOnClickListener {
            ind = 4
            imgfront.setImageResource(R.drawable.r)
        }
        binding.fab5.setOnClickListener {
            ind = 5
            imgfront.setImageResource(R.drawable.q)
        }
        binding.fab6.setOnClickListener {
            ind = 6
            imgfront.setImageResource(R.drawable.i)
        }
        binding.fab7.setOnClickListener {
            ind = 7
            imgfront.setImageResource(R.drawable.e)
        }
        //</editor-fold>

       if(t != null) {
           Log.e("2", "Something was broken: ${t.message}")
       }
    }

    override fun onContainerVisibilityChanged(isVisible: Boolean) {
       Log.d("nigga", "onContainerVisibilityChanged. isVisible = $isVisible")
    }

    //create launcher for getImage() activity
    private var launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // There are no request codes
            if (result.data != null){
                val uri = result.data!!.data

                PhotoPicker.Show = true
                val imgGal: ImageView = findViewById(R.id.GalImage)

                Glide.with(this).load(uri).into(imgGal)
                PhotoPicker.FrontImage?.let { Glide.with(this).load(uri).into(it) }

                PhotoPicker.ImgUri = uri

                val bgImg: ImageView = findViewById(R.id.BgGal)
                bgImg.setImageResource(R.drawable.bg_dark)
                PhotoPicker.FrontBg?.setImageResource(R.drawable.bg_dark)

            }
        }
    }


    private fun setImage() {
        val i = Intent()
        i.setType("image/*")
        i.setAction(Intent.ACTION_GET_CONTENT)

        launcher.launch(Intent.createChooser(i, "Select Picture"))

    }

    private fun delImage(){
        PhotoPicker.Show = false
        val imgGal: ImageView = findViewById(R.id.GalImage)
        imgGal.setImageResource(R.drawable.blank)
        PhotoPicker.FrontImage?.setImageResource(R.drawable.blank)

        PhotoPicker.ImgUri = null

        val bgImg: ImageView = findViewById(R.id.BgGal)
        bgImg.setImageResource(R.drawable.blank)
        PhotoPicker.FrontBg?.setImageResource(R.drawable.blank)
    }
}

object DrawData {
    var pathList = ArrayList<Path>()
    var paintList = ArrayList<Paint>()
    var DF = ArrayList<Draw2DFlip>()
}

@SuppressLint("StaticFieldLeak")
object PhotoPicker {
    var Show = false
    var ImgUri: Uri? = null
    var FrontImage: ImageView? = null
    var FrontBg: ImageView? = null
}


class Draw2D @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {
    //<editor-fold desc="draw">


    private var drawPath = Path()
    private var drawPaint = Paint()
    private var canvasPaint = Paint()
    var paintColor = -120787
    var strokeWidth = 10f
    private var drawCanvas = Canvas()
    private var canvasBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)


    fun undo() {
        if (DrawData.pathList.size != 0){
            DrawData.pathList.removeLast()
            DrawData.paintList.removeLast()
            invalidate()
        }
    }

    private fun setPencil() {
        drawPath = Path()
        DrawData.pathList.add(drawPath)
        drawPaint = Paint()
        DrawData.paintList.add(drawPaint)
        drawPaint.color = paintColor
        drawPaint.isAntiAlias = true
        drawPaint.strokeWidth = strokeWidth
        drawPaint.style = Paint.Style.STROKE
        drawPaint.strokeJoin = Paint.Join.ROUND
        drawPaint.strokeCap = Paint.Cap.ROUND
        //canvasPaint = Paint(Paint.DITHER_FLAG)
    }

    //************************************   draw view  *************************************************************
    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(canvasBitmap, 0f, 0f, canvasPaint)
        for (i in DrawData.pathList.indices) {
            canvas.drawPath(DrawData.pathList[i], DrawData.paintList[i])
            //Log.e("nigger", i.toString())
        }
        Log.e("nigger",DrawData.DF.toString())
        if (DrawData.DF.size != 0){
            DrawData.DF[0].invalidate()
        }

    }


    //***************************   respond to touch interaction   **************************************************
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        canvasPaint.color = paintColor
        val touchX = event.x
        val touchY = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                setPencil()
                drawPath.moveTo(touchX, touchY)
            }

            MotionEvent.ACTION_MOVE -> {
                drawCanvas.drawPath(drawPath, drawPaint)
                drawPath.lineTo(touchX, touchY)
            }

            MotionEvent.ACTION_UP -> {
                drawPath.lineTo(touchX, touchY)
                drawCanvas.drawPath(drawPath, drawPaint)

            }

            else -> return false
        }
        //redraw
        invalidate()
        return true
    }
    //</editor-fold>
}

class Draw2DFlip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
): View(context, attrs, defStyle){
    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        var ptoff: Path
        var scaleMatrix: Matrix
        var paioff: Paint
        for (i in DrawData.pathList.indices) {
            ptoff = Path(DrawData.pathList[i])

            scaleMatrix = Matrix()
            scaleMatrix.setScale(0.6968f, 0.7045f, 20f,920f)
            ptoff.transform(scaleMatrix)

            paioff = Paint(DrawData.paintList[i])
            paioff.strokeWidth *= 0.7f

            canvas.drawPath(ptoff, paioff)
            //Log.e("nigger", i.toString())
        }
    }
}


