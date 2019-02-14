package com.example.variometer2

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.util.*

class MainActivity : AppCompatActivity() {
    val data= DataUtil.getEmptyDataInstance()
    val sensorEventListener=ImplSensorEventListener(data)

    fun pursueView(lineChart: LineChart,data:Data){
        val XAxisWidth=30
        val entries=data.fileterdValue
        lineChart.xAxis.axisMinimum=lineChart.data.xMax-XAxisWidth.toInt()

        val recentYMax=entries.takeLast(XAxisWidth.toInt()).min() ?: 0f//エルビス演算子 ?: の左がnullならば右を返す
        val recentYMin=entries.takeLast(XAxisWidth.toInt()).max() ?: 0f
        val margin=(recentYMax-recentYMin)*0.1f
        lineChart.axisLeft.axisMinimum=recentYMax+margin
        lineChart.axisLeft.axisMaximum=recentYMin-margin


    }

    fun initChart(){
        val lineChart=findViewById<LineChart>(R.id.chart)

        //raw data
        val rawDataList= mutableListOf<Entry>(Entry(0f,0f))
        val rawLineDataSet= LineDataSet(rawDataList,"Raw")

        //filtered data
        val filteredDataList= mutableListOf<Entry>(Entry(0f,0f))
        val filteredLineDataSet= LineDataSet(filteredDataList,"Filtered")

        val lineData= LineData(rawLineDataSet,filteredLineDataSet)
        lineChart.data=lineData

        //appearance and interactive
        rawLineDataSet.setDrawCircles(false)

        filteredLineDataSet.setDrawCircles(false)
        filteredLineDataSet.lineWidth=2f
        filteredLineDataSet.color= Color.BLACK

        lineChart.legend.isEnabled=true
        lineChart.description.isEnabled=false

        lineChart.isDragEnabled=true
        lineChart.setTouchEnabled(true)

        lineChart.invalidate()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        //Initialize Sensor
        val sensorManager=getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val pressureSensor=sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        sensorManager.registerListener(sensorEventListener,pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)

        //Get UI
        val lineChart=findViewById<LineChart>(R.id.chart)

        //Initialize Chart
        initChart()

        var tone=Tone()

        val handler= Handler()
        val runnable= object:Runnable {
            override fun run() {

                //update chart
                lineChart.lineData.dataSets[0].addEntry(Entry(data.index.last().toFloat(),data.getValue().last()))


                lineChart.lineData.dataSets[1].addEntry(Entry(data.index.last().toFloat(),data.fileterdValue.last()))

                lineChart.lineData.notifyDataChanged()
                lineChart.notifyDataSetChanged()

                pursueView(lineChart, data)

                lineChart.invalidate()

                //calc variation[Pa/s]
                val variation=(data.fileterdValue.last()-data.fileterdValue[data.fileterdValue.lastIndex-1])/(data.time.last().timeInMillis-data.time[data.time.lastIndex-1].timeInMillis)*1000*100

                //play tone
                tone.play(440+10*variation.toInt())


                handler.postDelayed(this,1000)
            }
        }
        handler.post(runnable)

    }
}

class ImplSensorEventListener(data:Data): SensorEventListener {
    private val data=data
    private var count=0

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    override fun onSensorChanged(event: SensorEvent?) {
        if(event?.sensor?.type==Sensor.TYPE_PRESSURE){
            data.index.add(count)
            data.time.add(Calendar.getInstance())
            data.addValue(event.values[0])
            count++
        }


    }

}

class Data(var index:ArrayList<Int>, var time:ArrayList<Calendar>, private var value:ArrayList<Float>){
    var fileterdValue= arrayListOf<Float>(0f,0f)

    fun addValue(value: Float){
        this.value.add(value)

        fileterdValue.add(this.value.takeLast(30).average().toFloat())
    }
    fun getValue():ArrayList<Float>{
        return this.value
    }
}

object DataUtil{
    fun getEmptyDataInstance():Data{
        val index= arrayListOf<Int>(0,0)
        val time= arrayListOf<Calendar>(Calendar.getInstance(),Calendar.getInstance())
        val value= arrayListOf<Float>(0f,0f)
        return  Data(index, time, value)
    }
}

class Tone{
    val SAMPLE_RATE=44100
    val LENGTH=1

    val audioAttributes= AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    val audioFormat= AudioFormat.Builder()
        .setSampleRate(SAMPLE_RATE)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setChannelMask(AudioFormat.CHANNEL_OUT_DEFAULT)
        .build()
    val audioTrack= AudioTrack.Builder()
        .setAudioAttributes(audioAttributes)
        .setAudioFormat(audioFormat)
        .setBufferSizeInBytes(SAMPLE_RATE*LENGTH)
        .build()

    fun play(frequency:Int){
        val buff=generateSinWave(frequency, SAMPLE_RATE)

        audioTrack.play()
        audioTrack.write(buff,0,buff.size)

        audioTrack.stop()
        //audioTrack.flush()

    }

    // 1second
    private fun generateSinWave(frequency: Int, samplingRate:Int):ByteArray{
        var buff=ByteArray(samplingRate)
        for(i in 0 .. samplingRate-1){
            buff[i]=(100*Math.sin(2*Math.PI/samplingRate*i*frequency)).toByte()
            //Log.d("buff",buff[i].toString())
        }

        return buff
    }
}
