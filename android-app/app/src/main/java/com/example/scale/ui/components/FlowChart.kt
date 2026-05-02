package com.example.scale.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.scale.ui.theme.ScaleColors
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

@Composable
fun FlowChart(
    points: List<Pair<Float, Float>>,
    accent: Color,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = "WEIGHT · TIME",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = ScaleColors.TEXT_SECONDARY_45,
                letterSpacing = 2.sp,
            ),
        )
        Spacer(modifier = Modifier.height(6.dp))

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            factory = { context ->
                LineChart(context).apply {
                    description.isEnabled = false
                    legend.isEnabled = false
                    axisRight.isEnabled = false
                    setNoDataText("Waiting for samples...")
                    setNoDataTextColor(ScaleColors.TEXT_SECONDARY_45.toArgb())

                    axisLeft.textColor = ScaleColors.TEXT_SECONDARY_55.toArgb()
                    axisLeft.gridColor = Color.White.copy(alpha = 0.08f).toArgb()
                    axisLeft.axisLineColor = Color.White.copy(alpha = 0.12f).toArgb()
                    axisLeft.axisMinimum = -2f

                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.textColor = ScaleColors.TEXT_SECONDARY_55.toArgb()
                    xAxis.gridColor = Color.White.copy(alpha = 0.06f).toArgb()
                    xAxis.axisLineColor = Color.White.copy(alpha = 0.12f).toArgb()
                    xAxis.granularity = 1f
                    xAxis.valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val total = value.toInt().coerceAtLeast(0)
                            val m = total / 60
                            val s = total % 60
                            return "%d:%02d".format(m, s)
                        }
                    }

                    isDragEnabled = false
                    setScaleEnabled(false)
                    setPinchZoom(false)
                    isAutoScaleMinMaxEnabled = false
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { chart ->
                val accentArgb = accent.toArgb()
                val entries = points.map { Entry(it.first, it.second) }
                val dataSet = LineDataSet(entries, "Weight (g)").apply {
                    setDrawValues(false)
                    setDrawCircles(false)
                    lineWidth = 2f
                    color = accentArgb
                    setDrawFilled(true)
                    fillColor = accentArgb
                    fillAlpha = 36
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }
                chart.data = LineData(dataSet)
                val maxX = entries.lastOrNull()?.x ?: 0f
                chart.xAxis.axisMinimum = 0f
                chart.xAxis.axisMaximum = maxOf(20f, maxX + 1f)
                chart.notifyDataSetChanged()
                chart.invalidate()
            },
        )
    }
}
