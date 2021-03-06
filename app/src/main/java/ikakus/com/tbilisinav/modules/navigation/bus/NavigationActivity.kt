package ikakus.com.tbilisinav.modules.navigation.bus

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import com.google.android.gms.maps.model.LatLng
import ikakus.com.tbilisinav.BaseActivity
import ikakus.com.tbilisinav.R
import ikakus.com.tbilisinav.core.mvibase.MviView
import ikakus.com.tbilisinav.data.source.navigation.models.Itinerary
import ikakus.com.tbilisinav.data.source.navigation.models.Leg
import ikakus.com.tbilisinav.data.source.navigation.models.Mode
import ikakus.com.tbilisinav.data.source.navigation.models.Plan
import ikakus.com.tbilisinav.modules.navigation.bus.base.NavigationIntent
import ikakus.com.tbilisinav.modules.navigation.bus.base.NavigationViewModel
import ikakus.com.tbilisinav.modules.navigation.bus.base.NavigationViewState
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.activty_navigation.*
import org.koin.android.architecture.ext.viewModel
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.util.*

class NavigationActivity : BaseActivity(), MviView<NavigationIntent, NavigationViewState>, ILegListener {


    private val disposables: CompositeDisposable = CompositeDisposable()
    private val vModel: NavigationViewModel by viewModel()
    private val navListener: NavListener by inject()

    private val selectLegIntent = PublishSubject.create<NavigationIntent.SelectLegIntent>()
    private val selectRouteIntent = PublishSubject.create<NavigationIntent.SelectRouteIntent>()

    private var fromLatLng: LatLng? = null
    private var toLatLng: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activty_navigation)
        navigationMapView.onCreate(savedInstanceState)

        fromLatLng = intent.extras?.getParcelable(FROM_LATLNG)
        toLatLng = intent.extras?.getParcelable(TO_LATLNG)

        navListener.receiver = this

        navigationMapView.mapReadyPublisher.subscribe {
            enableMyLocationIfPermitted()
        }

        stepGuideView.pageChangePublisher.subscribe {
            selectLegIntent.onNext(NavigationIntent.SelectLegIntent(it))
        }

        routeSelector.selectedRoutePublisher.subscribe {
            selectRouteIntent.onNext(NavigationIntent.SelectRouteIntent(it))
        }

    }

    private fun bind() {
        // Subscribe to the ViewModel and call render for every emitted state
        disposables.add(
                vModel.states().subscribe { this.render(it) }
        )
        // Pass the UI's intents to the ViewModel
        vModel.processIntents(intents())
    }

    override fun intents(): Observable<NavigationIntent> {
        return Observable.merge(initialIntent(), getSelectLegIntent(), getSelectRouteIntent())
    }

    private fun initialIntent(): Observable<NavigationIntent> {
        return Observable.just(NavigationIntent.BusNavigateIntent(fromLatLng!!, toLatLng!!))
    }

    private fun getSelectLegIntent(): Observable<NavigationIntent.SelectLegIntent> {
        return selectLegIntent
    }

    private fun getSelectRouteIntent(): Observable<NavigationIntent.SelectRouteIntent> {
        return selectRouteIntent
    }

    var plan: Plan? = null
    var route: Itinerary? = null
    var leg: Leg? = null

    override fun render(state: NavigationViewState) {
        if (state.isLoading) {
            loadingLayout.visibility = VISIBLE
        } else {
            loadingLayout.visibility = GONE
        }

        if (state.error != null) {
            Toast.makeText(this, state.error.message, Toast.LENGTH_SHORT).show()
            Timber.d(state.error.message)
        }

        if (state.busNavigation != null && state.busNavigation?.plan == null && !state.isLoading) {
            Toast.makeText(this, getString(R.string.no_route_found), Toast.LENGTH_SHORT).show()
            finish()
        }

        val setPlan = plan == null || !(plan?.equals(state.busNavigation?.plan)!!)
        if (state.busNavigation?.plan != null && setPlan) {
            plan = state.busNavigation.plan
            routeSelector.setPlan(plan!!)
        }

        val setRoute = route == null || !(route?.equals(state.selectedRoute)!!)
        if (state.selectedRoute != null && setRoute) {
            route = state.selectedRoute
            routeSelector.setSelectedRoute(route!!)
            navigationMapView.clear()

            state.selectedRoute.legs.forEach {
                val legColor = getLegColor(it)
                navigationMapView.addLeg(it, legColor)
            }

            stepGuideView.setNavigationData(state.selectedRoute)
            val leg = state.selectedRoute.legs.first()
            navigationMapView.show(leg)
            selectLegIntent.onNext(NavigationIntent.SelectLegIntent(leg))
        }

        val setLeg = leg == null || !(leg?.equals(state.selectedLeg)!!)
        if (state.selectedLeg != null && setLeg) {
            leg = state.selectedLeg
            navigationMapView.show(state.selectedLeg)
            stepGuideView.setSelectedLeg(state.selectedLeg)
        }

    }

    override fun showLeg(leg: Leg) {
        navigationMapView.show(leg)
    }

    override fun showPoint(point: LatLng) {
        navigationMapView.show(point)
    }

    private fun getRandomColor(): Int {
        val rnd = Random()
        return Color.argb(255,
                rnd.nextInt(256),
                rnd.nextInt(256),
                rnd.nextInt(256))
    }

    private fun getLegColor(leg: Leg): Int {
        return when (leg.mode) {
            Mode.BUS -> resources.getColor(R.color.bus_step_color)
            Mode.WALK -> resources.getColor(R.color.walk_green)
            Mode.SUBWAY -> resources.getColor(R.color.subway_step_color)
        }
    }

    override fun onBackPressed() {
        if(routeSelector.isListOpen){
            routeSelector.toggleList()
        }else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        navigationMapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.dispose()
        navigationMapView.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        navigationMapView.onPause()
    }

    override fun onResume() {
        super.onResume()
        navigationMapView.onResume()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        navigationMapView.onLowMemory()
    }

    private val LOCATION_PERMISSION_REQUEST_CODE = 111

    private fun enableMyLocationIfPermitted() {
        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            bind()
            navigationMapView.setLocationEnabled()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    enableMyLocationIfPermitted()
                }
                return
            }
        }
    }

    companion object {
        private val FROM_LATLNG = "from"
        private val TO_LATLNG = "to"

        fun start(context: Context, from: LatLng, to: LatLng) {
            val intent = Intent(context, NavigationActivity::class.java)
            intent.putExtra(FROM_LATLNG, from)
            intent.putExtra(TO_LATLNG, to)
            context.startActivity(intent)
        }
    }
}