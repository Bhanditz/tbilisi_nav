package ikakus.com.tbilisinav.modules.navigation.bus.views.steps

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import ikakus.com.tbilisinav.R
import ikakus.com.tbilisinav.data.source.navigation.models.Leg
import kotlinx.android.synthetic.main.walk_step_view_layout.view.*
import java.util.concurrent.TimeUnit

class SubwayStepView(context: Context) :
        FrameLayout(context) {

    constructor(context: Context, leg: Leg) : this(context) {
        LayoutInflater.from(context).inflate(R.layout.subway_step_view_layout, this, true)
        tvTime.text = TimeUnit.MILLISECONDS.toMinutes(leg.duration.toLong()).toString() + " min"
        tvFrom.text = leg.from.name
        tvTo.text = leg.to.name
    }
}