package com.example.polaris_mk2



import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
//====================================================================//
//タイマーに関するライブラリ　　　　　　　　　　　　　　　　　　　　　　　　　　　//
//===================================================================//
import android.widget.Button
import android.widget.Chronometer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
//======================================================================//
//グラフに関するライブラリ　　　　　　　　　　　　　　　　　　　　　　　　　　　　   //
//======================================================================//
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.location.Location
import android.location.LocationListener
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
//=======================================================================//
//アンドロイドの通信に関わるライブラリ                                         //
//=======================================================================//
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.ArrayList
import kotlin.concurrent.thread
//==========================================================================//
//音システムに関わるライブラリ　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　//
//==========================================================================//
import android.media.AudioAttributes
import android.media.SoundPool
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() , LocationListener {


    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 使用が許可された
            locationStart()

        } else {
            // それでも拒否された時の対応
            val toast = Toast.makeText(this,
                "これ以上なにもできません", Toast.LENGTH_SHORT)
            toast.show()

        }
    }

    private lateinit var locationManager: LocationManager
    private var speed = 0f
    lateinit var rot_barData :BarData
    lateinit var rot_barChart:BarChart
    lateinit var alt_barData :BarData
    lateinit var alt_barChart:BarChart
    //=======================================================//
    //音システムのフィールド　　　　　　　　　　　　　　　　　　　　　　//
    //=======================================================//
    lateinit var sp0:SoundPool

    var count:Int = 0
    final var INTERVAL :Int = 300

    var spdValue: Float= 0f
    var rpmValue: Float = 0f
    var altValue: Float = 0f
    var snd0=0
    var check=0
    var streamid=0
    var go_rpm:Boolean= false
    var go_spd:Boolean= true
    private var waitperiod = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //クロノメーター開始
        chronometer.start()
        //========================================================//
        //データベース
        //========================================================//


        // LiveDataだとデータに変更があった場合即時反映される
        // 取得をこちらに切り替えて、データ表示後、データ追加して即時表示が変わることを確認してください
//            notificationDao.selectAllWithLiveData().observe(this, Observer {
//                main_text.text = it.toString()
//            })

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            locationStart()
        }
        //==================================================================//
        //ここはクロノメーターを用いたタイマーのプログラム開始　author自分
        //==================================================================//
        //オブジェクト取得
        val chronometer = findViewById<Chronometer>(R.id.chronometer)
        val restart :Button= findViewById<Button>(R.id.restart)
        val stopButton :Button= findViewById<Button>(R.id.stopButton)
        //開始
        restart.setOnClickListener()
        {
            restart(this,waitperiod)
        }



        val manager: UsbManager = getSystemService(USB_SERVICE) as UsbManager
        val availableDrivers: List<UsbSerialDriver> =
            UsbSerialProber.getDefaultProber().findAllDrivers(manager)

        if (availableDrivers.isEmpty()) {
            Toast.makeText(this, "Activity:ドライバ該当なし", Toast.LENGTH_LONG).show()
        } else {

            val driver: UsbSerialDriver = availableDrivers[0]
            val connection: UsbDeviceConnection = manager.openDevice(driver.device)

            if (connection == null) { //Condition 'connection == null' is always 'false'って真か？
                // manager.requestPermission(driver.device,) 権限求める操作入れると親切なアプリになります。
                Toast.makeText(this, "Activity:権限がありません", Toast.LENGTH_LONG).show()
            } else {

                var port = driver.ports[0]
                port.open(connection)
                port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                Toast.makeText(this, "Activity:接続されました", Toast.LENGTH_LONG).show()


                var usbIoManager = SerialInputOutputManager(port, Listener())

                thread {
                    usbIoManager.start()
                }
            }
        }
    }




    private inner class Listener : SerialInputOutputManager.Listener {
        override fun onRunError(e: Exception?) {
            return
        }

        override fun onNewData(data: ByteArray) {

            check(data, data.size)


        }


    }


    private val corrctData = mutableListOf<Byte>()
    private fun check(data: ByteArray, length: Int) { // 確認処理
        if (length == 12) {//繋がってきたとき
            detection(data)
            corrctData.clear()//リセット。（不正データの後に正しいデータが入るとfix関数のclear()が実行されないため。
        } else if (length < 12) {//分離されてきたとき
            correction(length, data)
        }
    }

    private fun correction(length: Int, data: ByteArray) { //データ補正
        for (i in 0 until length) {

            corrctData += data[i]

        }
        if (corrctData.size == 12) {
            detection(corrctData.toByteArray())//mutableList<Byte>で宣言されているため、ByteArrayに統一
            corrctData.clear()
        }
    }

    private fun detection(data: ByteArray) {//誤り検知

        var txCkSum: Int
        var rxCkSum: Int


        if (data[0].toChar() == 'S') {

            txCkSum = data[4] * 10 + data[5]
            rxCkSum = data[1] + data[2] + data[3]

            if (txCkSum == rxCkSum) {
                spdValue = data[1].toFloat() * 10 + data[2].toFloat() + data[3].toFloat() / 10
                //=============================================================//
                //spdValue null =>Toastをもちいてメッセージを表示                   //
                //spdValue が値を持つ=>グラフ描写                                  //
                //==============================================================//
                if(spdValue==null) {

                }else{

                    rotation_upgrade(createdata(spdValue))

                }
                if (data[6].toChar() == 'R') {
                    //txCkSum = data[10] * 10 + data[11]
                    //rxCkSum = data[7] + data[8] + data[9]
                    //  if (txCkSum == rxCkSum) {
                    rpmValue =
                        data[7].toFloat() * 100 + data[8].toFloat() * 10 + data[9].toFloat()

                    alt_upgrade(createdata(rpmValue))


                    //updateGraph(spdValue, rpmValue)
                    /*    if (data[12].toChar() == 'A') {
                            altValue =
                                data[13].toFloat() * 10 + data[14].toFloat() + data[15].toFloat() / 10


                        }*/
                    Log.d("main", "update")
                    //}
                }
            }
        }

    }
    //==================================================================//
    //データを作る関数です。データの組を返します                               //
    //==================================================================//
    fun createdata(value_two:Float):MutableList<BarEntry>
    {
        val entries: MutableList<BarEntry> = ArrayList()
        entries.clear()
        entries.add(BarEntry(1f,value_two))
        return entries

    }
    //==================================================================//
    //与えられたデータをもとにグラフを作成します                               //
    //==================================================================//
    //回転数
    fun rotation_upgrade(rot_entries: MutableList<BarEntry>)
    {
        rot_barChart = findViewById(R.id.rotation)
        //縦軸(左)設定
        val left: YAxis = rot_barChart.axisLeft
        left.axisMinimum = 0f
        left.axisMaximum = 20f
        left.labelCount = 5
        left.setDrawTopYLabelEntry(true)
        left.setDrawGridLines(true)

        //縦軸(右)設定
        val right: YAxis = rot_barChart.axisRight
        right.setDrawLabels(false)
        right.setDrawGridLines(false)
        right.setDrawZeroLine(false)
        right.setDrawTopYLabelEntry(false)

        //横軸設定
        val bottomAxis: XAxis = rot_barChart.xAxis
        bottomAxis.position = XAxis.XAxisPosition.BOTTOM
        bottomAxis.setDrawLabels(false)
        bottomAxis.setDrawGridLines(false)
        bottomAxis.setDrawAxisLine(false)

        //オプション設定
        rot_barChart.setDrawValueAboveBar(false)

        rot_barChart.isClickable = false

        //label設定
        rot_barChart.description.text="spd"
        rot_barChart.description.textSize=15f
        rot_barChart.description.yOffset=310f


        //凡例設定
        rot_barChart.legend.isEnabled = false
        rot_barChart.setScaleEnabled(false)

        val rot_dataSet = BarDataSet(rot_entries, "Label")
        val rot_barData = BarData(rot_dataSet)
        rot_barData.setValueTextSize(40f)//値テキストサイズ
        rot_barChart.data = rot_barData
        rot_barChart.invalidate()


    }
    //高度
    fun alt_upgrade(entries: MutableList<BarEntry>)
    {
        alt_barChart = findViewById(R.id.altitude)
        //縦軸(左)設定
        val left: YAxis = alt_barChart.axisLeft
        left.axisMinimum = 0f
        left.axisMaximum = 100f
        left.labelCount = 5
        left.setDrawTopYLabelEntry(true)
        left.setDrawGridLines(true)

        //縦軸(右)設定
        val right: YAxis = alt_barChart.axisRight
        right.setDrawLabels(false)
        right.setDrawGridLines(false)
        right.setDrawZeroLine(false)
        right.setDrawTopYLabelEntry(false)

        //横軸設定
        val bottomAxis: XAxis = alt_barChart.xAxis
        bottomAxis.position = XAxis.XAxisPosition.BOTTOM
        bottomAxis.setDrawLabels(false)
        bottomAxis.setDrawGridLines(false)
        bottomAxis.setDrawAxisLine(false)

        //オプション設定
        alt_barChart.setDrawValueAboveBar(false)

        alt_barChart.isClickable = false

        //label設定
        alt_barChart.description.text="rot"
        alt_barChart.description.textSize=15f
        alt_barChart.description.yOffset=310f


        //凡例設定
        alt_barChart.legend.isEnabled = false
        alt_barChart.setScaleEnabled(false)


        val alt_dataSet = BarDataSet(entries, "Label")
        val alt_barData = BarData(alt_dataSet)
        alt_barData.setValueTextSize(40f)//値テキストサイズ
        alt_barChart.data = alt_barData
        alt_barChart.invalidate()


    }

    //======================================================================//
    //音を鳴らす関数です。
    //======================================================================//
    fun make_a_sound(altValue :Float)
    {
        if(altValue<900f){

            val aa0=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            sp0=SoundPool.Builder().setAudioAttributes(aa0).setMaxStreams(1).build()
            snd0=sp0.load(this,R.raw.one,1)
            streamid=sp0.play(snd0, 1.0f, 1.0f, 0, -1, 1f)
            check=1
        }
    }
    private fun locationStart() {
        Log.d("debug", "locationStart()")

        // Instances of LocationManager class must be obtained using Context.getSystemService(Class)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.d("debug", "location manager Enabled")
        } else {
            // to prompt setting up GPS
            val settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(settingsIntent)
            Log.d("debug", "not gpsEnable, startActivity")
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1000)

            Log.d("debug", "checkSelfPermission false")
            return
        }

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1000,
            5f,
            this)

    }
    //=====================================================================================//
//アンドロイドで測る機速計。正直意味はないので機速計ができるまえの気休めに考えたほうが良い
//=====================================================================================//
    override fun onLocationChanged(location: Location) {
        if(location.hasSpeed()) {
            //速度が出ている時
            speed = location.getSpeed()
        } else {
            //速度が出ていない時
            speed = 0f;
        }
        // alt_upgrade(createdata(speed))


    }
    //再起動
    open fun restart(cnt: Context, period: Int) {
        // intent 設定で自分自身のクラスを設定
        val mainActivity = Intent(cnt, MainActivity::class.java)

        // PendingIntent , ID=0
        val pendingIntent = PendingIntent.getActivity(
            cnt,
            0, mainActivity, PendingIntent.FLAG_CANCEL_CURRENT
        )

        // AlarmManager のインスタンス生成
        val alarmManager = cnt.getSystemService(
            ALARM_SERVICE
        ) as AlarmManager
        // １回のアラームを現在の時間からperiod（５秒）後に実行させる
        if (alarmManager != null) {
            val trigger = System.currentTimeMillis() + period
            alarmManager.setExact(AlarmManager.RTC, trigger, pendingIntent)
        }
        // アプリ終了
        finish()
    }

}