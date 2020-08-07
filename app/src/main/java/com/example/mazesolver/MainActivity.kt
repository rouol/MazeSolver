package com.example.mazesolver

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import android.widget.ImageView
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import kotlin.Pair
import android.graphics.*
import kotlinx.coroutines.*
import kotlin.math.abs
import java.util.*
import android.graphics.Bitmap


// new types
typealias Dot = Pair<Int, Int>
//typealias prioritizedDot = Pair<Int, Int>

public class prioritizedDot(inputDot: Dot, inputPriority : Int) {
    var dot = inputDot
    var priority = inputPriority
}

//const
//val nullDot = Dot(null, null)


class MainActivity : AppCompatActivity() {

    lateinit var srcimageBitmap : Bitmap
    lateinit var scaledimageBitmap : Bitmap
    var isSolving = false
    var flags = mutableListOf<Dot>()
    var path = mutableListOf<Dot>()

    var scale = 4
    var imageScale : Float = 0.0f
    var visualize = false

    var currentPath : String? = null
    val TAKE_PICTURE = 1
    val SELECT_PICTURE = 2

    lateinit var imageView : ImageView

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imgView)

        imageView.setOnTouchListener OnTouchListener@{ _, motionEvent ->
            when (motionEvent.action){
                MotionEvent.ACTION_DOWN -> {
                    if (isSolving){
                        val touchX = motionEvent.x.toInt()
                        val touchY = motionEvent.y.toInt()
                        setFlag(touchX, touchY)
                    }
                }
            }
            return@OnTouchListener true
        }

        // button Listeners
        buttonCamera.setOnClickListener{
            dispatchCameraIntent()
        }

        buttonVisualization.setOnClickListener {
            visualize = !visualize
            if (visualize){
                buttonVisualization.setBackgroundResource(R.drawable.button_bg_green)
                buttonVisualization.text = "построение пути"
            } else{
                buttonVisualization.setBackgroundResource(R.drawable.button_bg_red)
                buttonVisualization.text = "только решение"
            }
        }

        buttonGallery.setOnClickListener {
            dispatchGalleryIntent()
        }

        buttonSolve.setOnClickListener {
            buttonSolve.text = "Выберите точки"
            isSolving = true
        }

    }

    //@SuppressLint("MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == TAKE_PICTURE && resultCode == Activity.RESULT_OK){
            try {
                val file = File(currentPath!!)
                val uri = Uri.fromFile(file)
                imageView.setImageURI(uri)
            }catch (e: IOException){
                e.printStackTrace()
            }
        }

        if (requestCode == SELECT_PICTURE && resultCode == Activity.RESULT_OK){
            try {
                val uri = data!!.data
                imageView.setImageURI(uri)
            }catch (e: IOException){
                e.printStackTrace()
            }
        }

    }

    fun dispatchCameraIntent() {
        val intentCamera = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intentCamera.resolveActivity(packageManager) != null) {
            var filePhoto : File? = null;
            try {
                filePhoto = createImage()
            }catch (e: IOException){
                e.printStackTrace()
            }
            if (filePhoto != null){
                //
                var photoUri = FileProvider.getUriForFile(this,
                    "com.example.mazesolver.fileprovider", filePhoto)
                intentCamera.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                startActivityForResult(intentCamera, TAKE_PICTURE)
            }
        }
    }

    fun dispatchGalleryIntent() {
        val intentGallery = Intent()
        intentGallery.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intentGallery, "Выберите изображение"), SELECT_PICTURE)
    }

    fun createImage(): File {
        val imageName = "temp"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        var image = File.createTempFile(imageName, ".jpg", storageDir)
        currentPath = image.absolutePath
        return image
    }

    fun setFlag(x : Int, y : Int){

        if (flags.isEmpty()){
            srcimageBitmap = (imgView.drawable as BitmapDrawable).bitmap
            // calc scale
            //scale = 4

            // scale down
            scaledimageBitmap = Bitmap.createScaledBitmap(
                srcimageBitmap, (srcimageBitmap.width / scale),
                (srcimageBitmap.height / scale), false
            )

        }

        val currentimageBitmap = (imgView.drawable as BitmapDrawable).bitmap
        var imageBitmap = currentimageBitmap.copy(currentimageBitmap.config, true)

        //calc real dimensions
        val imageWidth = imageBitmap.width.toFloat()
        //val imageHeight = imageBitmap.height.toFloat()
        //val imageRatio = imageHeight / imageWidth
        val viewWidth = imageView.width.toFloat()
        //val viewHeight = viewWidth * imageRatio
        imageScale = imageWidth / viewWidth

        //calc real coordinates
        val realx = (x * imageScale).toInt()
        val realy = (y * imageScale).toInt()

        val pixel = imageBitmap.getPixel(realx, realy)
        val averageLuminosity = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3

        if (averageLuminosity > 100){
            // make a Flag (area)
            for (i in -10..10){
                for (j in -10..10){
                    imageBitmap.setPixel(realx + i, realy + j, Color.rgb(255,0,0))
                }
            }

            imageView.setImageBitmap(imageBitmap)
            flags.add(Dot(realx, realy))

            if (flags.size >= 2){
                isSolving = false
                buttonSolve.text = "В процессе"
                buttonSolve.isEnabled = false
                buttonSolve.setTextColor(Color.BLACK)
                buttonSolve.setBackgroundResource(R.drawable.button_bg_disabled)
                GlobalScope.launch {
                    solve()
                }
            }
        }
    }

    fun drawPath(){

        val currentimageBitmap = (imgView.drawable as BitmapDrawable).bitmap
        var imageBitmap = currentimageBitmap.copy(currentimageBitmap.config, true)
        val scatter = (imageScale * scale * 2).toInt()

        path.forEach {
            for (i in -scatter..scatter){
                for (j in -scatter..scatter){
                    imageBitmap.setPixel(it.first * scale + i, it.second * scale + j, Color.rgb(255,0,0))
                }
            }
        }

        imageView.setImageBitmap(imageBitmap)

    }

    fun end(){
        runBlocking (Dispatchers.Main) {

            drawPath()

            buttonSolve.text = "Решить"
            buttonSolve.isEnabled = true
            buttonSolve.setTextColor(Color.WHITE)
            buttonSolve.setBackgroundResource(R.drawable.button_bg)
            flags.clear()
            path.clear()
        }
    }

    /*
    suspend fun equalsGoal(a : Dot) : Boolean {
        return (srcGoal <= a.first <= src) && (a.second == b.second)
    }
     */

    suspend fun isBlocked(dot : Dot) : Boolean {
        val pixel = scaledimageBitmap.getPixel(dot.first, dot.second)
        val averageLuminosity = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
        return averageLuminosity < 150
    }

    suspend fun neighbors(currentDot : Dot) : List<Dot> {

        val tempneighborsList = listOf(Dot(currentDot.first + 1, currentDot.second + 1), Dot(currentDot.first + 1, currentDot.second - 1),
            Dot(currentDot.first - 1, currentDot.second + 1), Dot(currentDot.first - 1, currentDot.second - 1))
        var neighborsList = listOf<Dot>()

        tempneighborsList.forEach {
            if (!isBlocked(it)){
                neighborsList = neighborsList.plus(it)
            }
        }

        return neighborsList
    }

    suspend fun reconstructPath(goalDot : Dot, dataPath : Map<Dot, Dot?>){

        var currentDot = goalDot

        while (dataPath[currentDot] != null){
            path.add(currentDot)
            currentDot = dataPath.getValue(currentDot)!!
        }
    }

    suspend fun solve(){

        fun heuristic(a : Dot, b : Dot): Int {
            // Manhattan distance on a square grid
            return abs(a.first - b.first) + abs(a.second - b.second)
        }

        val srcGoal = Dot(flags[1].first, flags[1].second)

        val start = Dot(flags[0].first / scale, flags[0].second / scale)
        var goal = Dot(flags[1].first / scale, flags[1].second / scale)

        //var frontier : Queue<Dot> = LinkedList() // for usual queue
        //val frontier = PriorityQueue<Dot> { a, b -> heuristic(a, goal) - heuristic(b, goal) }
        val frontier = PriorityQueue<prioritizedDot> { a, b -> a.priority - b.priority }

        frontier.add(prioritizedDot(start, 0))

        var cameFrom = mutableMapOf<Dot, Dot?>()
        cameFrom[start] = null

        var costSoFar = mutableMapOf<Dot, Int>()
        costSoFar[start] = 0

        while (!frontier.isEmpty()){
            var current = frontier.poll()!!

            if ( ((srcGoal.first - scale) <= (current.dot.first * scale)) && ((current.dot.first * scale) <= (srcGoal.first + scale))
                && ((srcGoal.second - scale) <= (current.dot.second * scale)) && ((current.dot.second * scale) <= (srcGoal.second + scale))){
                goal = current.dot
                break
            }

            for (next in neighbors(current.dot)){
                val newCost = costSoFar.getValue(current.dot) + 1
                if ((next !in cameFrom) || (newCost < costSoFar.getValue(next))){
                    costSoFar[next] = newCost
                    val priorityNext = newCost + heuristic(goal, next)
                    frontier.add(prioritizedDot(next, priorityNext))
                    cameFrom[next] = current.dot

                    if (visualize){
                        // drawing path
                        runBlocking (Dispatchers.Main) {
                            val currentimageBitmap = (imgView.drawable as BitmapDrawable).bitmap
                            var imageBitmap = currentimageBitmap.copy(currentimageBitmap.config, true)

                            val scatter = 1 * scale

                            for (i in -scatter..scatter){
                                for (j in -scatter..scatter){
                                    imageBitmap.setPixel(current.dot.first * scale + i, current.dot.second * scale + j, Color.rgb(0,255,0))
                                }
                            }

                            //imageBitmap.setPixel(current.dot.first * scale, current.dot.second * scale, Color.rgb(0,255,0))
                            imageView.setImageBitmap(imageBitmap)
                        }
                    }

                }
            }

        }

        reconstructPath(goal, cameFrom)

        end()

    }

}