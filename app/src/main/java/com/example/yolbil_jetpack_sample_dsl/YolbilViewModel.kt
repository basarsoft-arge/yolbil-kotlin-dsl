package com.example.yolbil_jetpack_sample_dsl

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.basarsoft.inavi.libs.sensormanager.SensorManager
import com.basarsoft.yolbil.core.MapBounds
import com.basarsoft.yolbil.core.MapPos
import com.basarsoft.yolbil.datasources.HTTPTileDataSource
import com.basarsoft.yolbil.datasources.TileDownloadListener
import com.basarsoft.yolbil.datasources.YBOfflineStoredDataSource
import com.basarsoft.yolbil.layers.RasterTileLayer
import com.basarsoft.yolbil.layers.TileLoadListener
import com.basarsoft.yolbil.layers.VectorTileLayer
import com.basarsoft.yolbil.layers.VectorTileRenderOrder
import com.basarsoft.yolbil.location.GPSLocationSource
import com.basarsoft.yolbil.location.Location
import com.basarsoft.yolbil.location.LocationListener
import com.basarsoft.yolbil.projections.EPSG4326
import com.basarsoft.yolbil.styles.CompiledStyleSet
import com.basarsoft.yolbil.ui.MapView
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.RequiresPermission
import com.basarsoft.yolbil.utils.AssetUtils
import com.basarsoft.yolbil.utils.ZippedAssetPackage
import com.basarsoft.yolbil.vectortiles.MBVectorTileDecoder
import com.basarsoft.yolbil.routing.NavigationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class YolbilViewModel @Inject constructor() : ViewModel() {

    // ---- BMS erişim bilgilerin
    private val appCode = "app_code"
    private val accId = "acc_id"

    private var mapView: MapView? = null

    var gpsLocationSource: GPSLocationSource? = null
    var lastLocation: Location? = null
    var focusPos: Button? = null
    var startNavigation: android.widget.Button? = null
    var isLocationFound: Boolean = false


    // Navigasyon sınıfın (senin paylaştığın sürümle uyumlu)
    private val navigationUsage: YolbilNavigationUsage = YolbilNavigationUsage()

    var startPos: MapPos? = null
    var endPos: MapPos? = null

    // ---- Katman geçişi için alanlar
    private var googleRasterLayer: RasterTileLayer? = null
    private var googleRasterCache: YBOfflineStoredDataSource? = null

    private var bmsVectorLayer: VectorTileLayer? = null
    private var bmsVectorDataSource: YBOfflineStoredDataSource? = null
    private var bmsVectorDecoder: MBVectorTileDecoder? = null

    var navigationInfo by mutableStateOf<NavigationInfo?>(null)
        private set
    init {
        navigationUsage.navigationInfoListener = { info ->
            navigationInfo = info
        }
    }

    @SuppressLint("MissingPermission")
    fun initializeMapView(mapView: MapView) {
        this.mapView = mapView

        // Başlangıç/varış örnekleri
        startPos = MapPos(32.8597, 39.9334) // Kızılay
        endPos   = MapPos(32.8547, 39.9250) // Anıtkabir

        // Projeksiyon & kamera
        mapView.options.baseProjection = EPSG4326()
        mapView.setFocusPos(MapPos(32.836262, 39.960160), 0f)
        mapView.setZoom(17.0f, 0.0f)

        Log.e("sensormanager", SensorManager.getModuleInfo())

        // --- GOOGLE RASTER TABANI (varsayılan)
        val googleHttp = HTTPTileDataSource(
            0, 18,
            "https://mt0.google.com/vt/lyrs=m&hl=tr&scale=4&apistyle=s.t%3A2%7Cs.e%3Al%7Cp.v%3Aoff&x={x}&y={y}&z={zoom}"
        )
        googleRasterCache = YBOfflineStoredDataSource(googleHttp, "/storage/emulated/0/.cachetile.db").apply {
            isCacheOnlyMode = false
        }

        val bounds = MapBounds(MapPos(32.836262, 39.960160), MapPos(32.836262, 39.960160))
        googleRasterCache?.startDownloadArea(bounds, 0, 10, object : TileDownloadListener() {
            override fun onDownloadProgress(progress: Float) {}
            override fun onDownloadCompleted() {}
        })

        googleRasterLayer = RasterTileLayer(googleRasterCache).also { layer ->
            layer.tileLoadListener = object : TileLoadListener() {
                override fun onVisibleTilesLoaded() {
                    Log.d("YolbilViewModel", "Visible tiles loaded")
                }
            }
        }
        mapView.layers.add(googleRasterLayer)

        // --- GPS
        gpsLocationSource = GPSLocationSource(mapView.context).also { it.startLocationUpdates() }
        gpsLocationSource!!.addListener(object : LocationListener() {
            override fun onLocationChange(location: Location) {
                if (!isLocationFound) {
                    lastLocation = location
                    isLocationFound = true
                }
            }
        })
    }


    /** Navigasyonu başlatır: önce rota kur, sonra beginNavigation */
    fun startNavigation() {
        createRoute()
        mapView?.isDeviceOrientationFocused = false
        navigationUsage.startNavigation()
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun createRoute() {
        val mv = mapView ?: run {
            Log.e("YolbilViewModel", "MapView not initialized")
            return
        }
        if (!hasInternetConnection(mv.context)) {
            mv.post {
                Toast.makeText(
                    mv.context,
                    "İnternet olmadığı için rota çizilemedi.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }
        val start = startPos ?: return
        val end = endPos ?: return
        val gps = gpsLocationSource ?: return

        navigationInfo = null
        val result = navigationUsage.fullExample(
            mapView = mv,
            start = start,
            end = end,
            isOffline = false,
            locationSource = gps
        )
        when {
            result == null -> {
                val errorMessage = navigationUsage.lastRouteMessage
                mv.post {
                    Toast.makeText(
                        mv.context,
                        errorMessage ?: "Tercihlerinize göre varış noktasına erişilemez.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return
            }
            result.points.isEmpty -> {
                val errorMessage = navigationUsage.lastRouteMessage
                mv.post {
                    Toast.makeText(
                        mv.context,
                        errorMessage ?: "Servis kaynaklı bir hata oluştu, rota oluşturulamadı.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return
            }
            else -> navigationInfo = result.toNavigationInfo()
        }
    }

    fun stopNavigation() {
        navigationUsage.stopNavigation()
        navigationInfo = null
    }

    // ------------------------------------------------------------
    //                 KATMAN GEÇİŞİ (Google ↔ BMS)
    // ------------------------------------------------------------

    /** Google raster katmanını gösterir, BMS vector’ü kaldırır. */
    fun showGoogleRaster() {
        val mv = mapView ?: return
        bmsVectorLayer?.let { mv.layers.remove(it) }
        googleRasterLayer?.let {
            mv.layers.remove(it)
            mv.layers.insert(0, it) // altlığı en alta koy
        }
        bringRouteLayersToTop(mv, navigationUsage.routeLayers())
    }



    /** BMS vector tile katmanını gösterir, Google raster’ı kaldırır. */
    fun showBmsVector(context: Context) {
        val mv = mapView ?: return

        // Google raster varsa kaldır
        googleRasterLayer?.let { layer ->
            runCatching { mv.layers.remove(layer) }
        }

        // İlk kez yükleniyorsa decoder + datasource + layer oluştur
        if (bmsVectorLayer == null) {
            try {
                // 1) Stil paketini assets’ten yükle
                // assets içine "transport_style_final_package_latest_light.zip" dosyasını eklemelisin.
                val styleAsset = AssetUtils.loadAsset("transport_style_final_package_latest_light.zip")
                if (styleAsset == null) {
                    Log.e("YolbilViewModel", "Vector style asset bulunamadı!")
                    showGoogleRaster()
                    return
                }

                val assetPackage = ZippedAssetPackage(styleAsset)
                val compiledStyle = CompiledStyleSet(assetPackage, "transport_style") // style adın
                bmsVectorDecoder = MBVectorTileDecoder(compiledStyle).apply {
                    // Tema parametresi istersen:
                    setStyleParameter("selectedTheme", "light")
                }

                // 2) BMS vektör PBF kaynağı
                val vectorHttp = HTTPTileDataSource(
                    0, 15,
                    "https://bms.basarsoft.com.tr/Service/api/v1/VectorMap/Pbf?accId=$accId&appCode=$appCode&x={x}&y={y}&z={z}"
                )
                bmsVectorDataSource = YBOfflineStoredDataSource(
                    vectorHttp,
                    "/storage/emulated/0/.vector.cachetile.db"
                ).apply {
                    isCacheOnlyMode = false
                }

                // 3) Vector tile layer
                bmsVectorLayer = VectorTileLayer(bmsVectorDataSource, bmsVectorDecoder).apply {
                    labelRenderOrder = VectorTileRenderOrder.VECTOR_TILE_RENDER_ORDER_LAST
                    buildingRenderOrder = VectorTileRenderOrder.VECTOR_TILE_RENDER_ORDER_LAYER
                }
            } catch (e: Exception) {
                Log.e("YolbilViewModel", "BMS vector layer oluşturulamadı: ${e.message}")
                showGoogleRaster()
                return
            }

        }

        // BMS layer’ı ekle (ekli değilse)
        bmsVectorLayer?.let { layer ->
            mv.layers.remove(layer)
            mv.layers.add(layer)
            bringRouteLayersToTop(mv, navigationUsage.routeLayers())

        }
    }
}

data class NavigationInfo(
    val remainingTime: String,
    val remainingDistance: String,
    val eta: String
) {
    companion object {
        fun fromRemaining(distanceMeters: Double?, timeSeconds: Double?): NavigationInfo? {
            if (distanceMeters == null || timeSeconds == null) return null
            val totalMinutes = kotlin.math.ceil(timeSeconds / 60.0).toInt()
            val formattedTime = formatDuration(totalMinutes)
            val formattedDistance = formatDistance(distanceMeters)
            val eta = Calendar.getInstance().apply {
                add(Calendar.MINUTE, totalMinutes)
            }.let { cal ->
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
            }
            return NavigationInfo(
                remainingTime = formattedTime,
                remainingDistance = formattedDistance,
                eta = eta
            )
        }
    }
}

fun NavigationResult.toNavigationInfo(): NavigationInfo {
    val totalSeconds = totalTime
    val totalMinutes = kotlin.math.ceil(totalSeconds / 60.0).toInt()
    val distanceMeters = totalDistance

    val formattedTime = formatDuration(totalMinutes)
    val formattedDistance = formatDistance(distanceMeters)
    val formattedEta = Calendar.getInstance().apply {
        add(Calendar.MINUTE, totalMinutes)
    }.let { cal ->
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
    }

    return NavigationInfo(
        remainingTime = formattedTime,
        remainingDistance = formattedDistance,
        eta = formattedEta
    )
}

private fun formatDuration(totalMinutes: Int): String {
    return if (totalMinutes < 60) {
        "$totalMinutes dk"
    } else {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        if (minutes == 0) "$hours sa" else "$hours sa $minutes dk"
    }
}

private fun formatDistance(distanceMeters: Double): String {
    return if (distanceMeters < 1000) {
        "${distanceMeters.toInt()} m"
    } else {
        val km = distanceMeters / 1000.0
        if (km >= 100) {
            String.format("%.0f km", km)
        } else {
            String.format("%.1f km", km)
        }
    }
}


private fun bringRouteLayersToTop(mv: MapView, lv: com.basarsoft.yolbil.layers.LayerVector?) {
    if (lv == null) return
    val n = lv.size().toInt()
    for (i in 0 until n) {
        val l = lv.get(i)
        mv.layers.remove(l)
        mv.layers.add(l) // en üste
    }
}

@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
private fun hasInternetConnection(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } else {
        @Suppress("DEPRECATION")
        connectivityManager.activeNetworkInfo?.isConnected == true
    }
}
