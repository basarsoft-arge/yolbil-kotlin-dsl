package com.example.yolbil_jetpack_sample_dsl

import android.annotation.SuppressLint
import android.util.Log
import com.basarsoft.yolbil.core.MapPos
import com.basarsoft.yolbil.datasources.BlueDotDataSource
import com.basarsoft.yolbil.layers.VectorLayer
import com.basarsoft.yolbil.location.GPSLocationSource
import com.basarsoft.yolbil.location.Location
import com.basarsoft.yolbil.location.LocationListener
import com.basarsoft.yolbil.location.LocationSourceSnapProxy
import com.basarsoft.yolbil.navigation.CommandListener
import com.basarsoft.yolbil.navigation.NavigationCommand
import com.basarsoft.yolbil.navigation.YolbilNavigationBundle
import com.basarsoft.yolbil.navigation.YolbilNavigationBundleBuilder
import com.basarsoft.yolbil.projections.EPSG4326
import com.basarsoft.yolbil.routing.NavigationResult
import com.basarsoft.yolbil.ui.MapView

class YolbilNavigationUsage {

    private val TAG: String = "YolbilNavigationUsage"

    var lastRouteMessage: String? = null
    private var mapView: MapView? = null
    private var snapLocationSourceProxy: LocationSourceSnapProxy? = null
    private var bundle: YolbilNavigationBundle? = null
    private var locationSource: GPSLocationSource? = null
    private var lastLocation: Location? = null
    private var navigationResult: NavigationResult? = null
    private var blueDotVectorLayer: VectorLayer? = null

    private var blueDotDataSource: BlueDotDataSource? = null

    var navigationInfoListener: ((NavigationInfo) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun fullExample(
        mapView: MapView,
        start: MapPos?,
        end: MapPos?,
        isOffline: Boolean,
        locationSource: GPSLocationSource
    ): NavigationResult? {

        lastRouteMessage = null
        this.mapView = mapView

        if (start != null) mapView.setFocusPos(start, 0f)

        this.locationSource = locationSource
        locationSource.addListener(object : LocationListener() {
            override fun onLocationChange(location: Location) {
                // Gerekirse burada UI güncelle
            }
        })

        // Doğru kaynakla snap proxy oluştur


        lastLocation = Location().apply { coordinate = start }

        addLocationSourceToMap(mapView)
        bundle = getNavigationBundle(isOffline)
        addNavigationToMapLayers(mapView)

        val localBundle = bundle
        if (localBundle != null) {
            // >>> KRİTİK DÜZELTME: navigationResult ataması <<<
            val navVectorResult = runCatching { bundle!!.startNavigation(start, end) }
            val navVector = navVectorResult.getOrNull()
            lastRouteMessage = navVectorResult.exceptionOrNull()?.message

            if (navVector == null || navVector.isEmpty) {
                if (lastRouteMessage == null) {
                    lastRouteMessage = "Tercihlerinize göre varış noktasına erişilemez."
                }
                navigationResult = null
                return null
            }
            navigationResult = navVector.get(0)
            val navRes = navigationResult ?: return null

            if(navigationResult != null){ // NavigationResult üzerinden getPointsGeoJSON
            //    getPointsEncodedPolyline ve getPointsLineString erişimi sağlanıyor.
                navigationResult!!.getPointsGeoJSON()
                navigationResult!!.getPointsEncodedPolyline()
                navigationResult!!.getPointsLineString()
             }
            snapLocationSourceProxy?.setRoutingPoints(navRes.points)
            navigationInfoListener?.invoke(navRes.toNavigationInfo())

            for (i in 0 until navRes.instructions.size()) {
                val rI = navRes.instructions[i.toInt()]
                Log.e("Instruction", rI.instruction)
                Log.e("Instruction", rI.action.toString())
                // rI.geometryTag.getObjectElement("commands").getArrayElement(0) // gerekiyorsa
            }

            // start null ise mock gönderme
            start?.let { locationSource.sendMockLocation(it) }

            mapView.fitRouteOnMap(navRes.points)
            try {
                val size = navRes.points.size()
                Log.d("NAVIGATION_POINTS", "Toplam Nokta: $size")

                for (i in 0 until size) {
                    val pos = navRes.points.get(i.toInt())
                    Log.d("NAVIGASYON NOKTALARI", "Nokta $i -> Lon: ${pos.x}, Lat: ${pos.y}")
                }

            } catch (e: Exception) {
                Log.e("NAVIGATION_POINTS", "Rota noktaları loglanırken hata: ${e.localizedMessage}")
            }
            return navRes
        } else {
            Log.e(TAG, "Navigation bundle is null")
            return null
        }
    }

    fun addLocationSourceToMap(mapView: MapView) {
        if (blueDotDataSource == null) {
            blueDotDataSource = BlueDotDataSource(EPSG4326(), locationSource).also { it.init() }
        }
        if (blueDotVectorLayer == null) {
            blueDotVectorLayer = VectorLayer(blueDotDataSource)
        }

        // --- tekrar eklemeyi engelle ---
        var exists = false
        val layerCount = mapView.layers.count()
        for (i in 0 until layerCount) {
            if (mapView.layers.get(i) === blueDotVectorLayer) {
                exists = true
                break
            }
        }
        if (!exists) {
            mapView.layers.add(blueDotVectorLayer)
            Log.d("BlueDot", "BlueDot layer eklendi")
        } else {
            Log.d("BlueDot", "BlueDot layer zaten var, eklenmedi")
        }

        if (snapLocationSourceProxy == null) {
            snapLocationSourceProxy = LocationSourceSnapProxy(blueDotDataSource).also {
                it.setMaxSnapDistanceMeter(500.0)
                it.init()
            }
        }
    }

    fun updateLocation(mapPos: MapPos?) {
        val newLocation = Location().apply { coordinate = mapPos }
        snapLocationSourceProxy?.updateLocation(newLocation)
    }

    fun stopNavigation() {
        mapView?.let { mv ->
            blueDotVectorLayer?.let { mv.layers.remove(it) }
            bundle?.let { mv.layers.removeAll(it.layers) }
        }
        bundle?.stopNavigation()
        blueDotVectorLayer = null
        navigationResult = null
    }

    fun getNavigationBundle(isOffline: Boolean): YolbilNavigationBundle {
        val baseUrl = "bms.basarsoft.com.tr"
        val accountId = "acc_id"   // senin değerlerin
        val appCode = "app_code"     // senin değerlerin

        val navigationBundleBuilder = YolbilNavigationBundleBuilder(
            baseUrl,
            accountId,
            appCode,
            // Mümkünse snap’li kaynak, yoksa GPS
            snapLocationSourceProxy!!.getBlueDotDataSource().getLocationSource()
        )

        navigationBundleBuilder.setBlueDotDataSourceEnabled(true)
        navigationBundleBuilder.setOfflineEnabled(isOffline)
        navigationBundleBuilder.setOfflineDataPath("/storage/emulated/0/yolbilxdata/TR.vtiles")
        navigationBundleBuilder.setCommandListener(object : CommandListener() {
            override fun onNavigationStarted(): Boolean {
                Log.e(TAG, "onNavigationStarted: navigation started")
                return super.onNavigationStarted()
            }

            // Güncel komut geldiğinde kalan mesafeyi/süreyi UI'ya ilet
            override fun onCommandReady(command: NavigationCommand): Boolean {
                Log.e(TAG, "onCommandReady: $command")
                NavigationInfo.fromRemaining(
                    command.totalDistanceToCommand,
                    command.remainingTimeInSec
                )?.let { info ->
                    navigationInfoListener?.invoke(info)
                }
                return super.onCommandReady(command)
            }

            // Rota yeniden hesaplandığında snap noktalarını ve kamera hizasını yenile
            override fun onNavigationRecalculated(navigationResult: NavigationResult): Boolean {
                Log.e(TAG, "onNavigationRecalculated: $navigationResult")
                mapView?.isDeviceOrientationFocused = false
                this@YolbilNavigationUsage.navigationResult = navigationResult
                snapLocationSourceProxy?.setRoutingPoints(navigationResult.points)
                mapView?.let { mv ->
                    val width = mv.width.takeIf { it > 0 } ?: mv.measuredWidth.takeIf { it > 0 } ?: 1080
                    val height = mv.height.takeIf { it > 0 } ?: mv.measuredHeight.takeIf { it > 0 } ?: 540
                    snapLocationSourceProxy?.setmoveToHeadingDistance(40.0, width, height)
                }
                navigationInfoListener?.invoke(navigationResult.toNavigationInfo())
                return super.onNavigationRecalculated(navigationResult)
            }

            // Konum güncellemelerinde kalan bilgileri taze tut
            override fun onLocationChanged(command: NavigationCommand?): Boolean {
                if (command != null) {
                    NavigationInfo.fromRemaining(
                        command.totalDistanceToCommand,
                        command.remainingTimeInSec
                    )?.let { info ->
                        navigationInfoListener?.invoke(info)
                    }
                }
                return super.onLocationChanged(command)
            }
        })

        return navigationBundleBuilder.build()
    }


    fun addNavigationToMapLayers(mapView: MapView) {
        bundle?.layers?.let { mapView.layers.addAll(it) }
    }

    @SuppressLint("MissingPermission")
    fun startNavigation() {
        val mv = mapView ?: run {
            Log.e(TAG, "MapView is null")
            return
        }
        val nav = navigationResult ?: run {
            Log.e(TAG, "navigationResult is null; call fullExample() first")
            return
        }

        bundle?.beginNavigation(nav)
        mv.setDeviceOrientationFocused(false)
        lastLocation?.coordinate?.let { mv.setFocusPos(it, 1.0f) }
        mv.setZoom(17f, 1.0f)

        locationSource?.addListener(object : LocationListener() {
            override fun onLocationChange(location: Location) {
                mv.setDeviceOrientationFocused(false)

                if (mv.isDeviceOrientationFocused){
                    mv.setZoom(17f, 1.0f)
                    mv.setFocusPos(location.coordinate, 1f)
                }

            }
        })
    }

    fun routeLayers(): com.basarsoft.yolbil.layers.LayerVector? = bundle?.layers


}
