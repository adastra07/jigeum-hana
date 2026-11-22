package com.adastra.focuswallpaper

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.time.LocalDate
import java.time.YearMonth

object CalendarLayout {
    fun rowHeight(viewportDp: Int): Int = ((viewportDp - 136) / 6).coerceIn(48, 72)
    fun dateInMonth(date: LocalDate, month: YearMonth): LocalDate = month.atDay(minOf(date.dayOfMonth, month.lengthOfMonth()))
}

/** Standard horizontally paged months: drag feedback, neighbouring pages and snap/fling. */
class CalendarMonthView(
    context: Context,
    private val origin: YearMonth,
    private val pageHeight: Int,
    createPage: (YearMonth) -> View,
    private val selected: (YearMonth) -> Unit,
    private val settled: () -> Unit
) : FrameLayout(context) {
    val pager = ViewPager2(context)
    private val centre = 1200
    val shownMonth: YearMonth get() = origin.plusMonths((pager.currentItem - centre).toLong())
    init {
        contentDescription = "월간 달력, 좌우로 밀어 월 이동"
        pager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        pager.offscreenPageLimit = 1
        pager.adapter = object : RecyclerView.Adapter<MonthHolder>() {
            override fun getItemCount() = centre * 2 + 1
            override fun onCreateViewHolder(parent: ViewGroup, type: Int): MonthHolder = MonthHolder(FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            })
            override fun onBindViewHolder(holder: MonthHolder, position: Int) {
                holder.host.removeAllViews()
                holder.host.addView(createPage(origin.plusMonths((position - centre).toLong())), FrameLayout.LayoutParams(-1, -1))
            }
        }
        addView(pager, LayoutParams(-1, -1))
        pager.setCurrentItem(centre, false)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { selected(origin.plusMonths((position - centre).toLong())) }
            override fun onPageScrollStateChanged(state: Int) { if (state == ViewPager2.SCROLL_STATE_IDLE) settled() }
        })
    }
    private class MonthHolder(val host: FrameLayout) : RecyclerView.ViewHolder(host)
    fun showMonth(month: YearMonth) {
        val position = (java.time.temporal.ChronoUnit.MONTHS.between(origin, month) + centre).toInt()
        if (position !in 0..centre * 2) return
        if (position == pager.currentItem) { selected(month); settled() }
        else pager.setCurrentItem(position, true)
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(pageHeight, MeasureSpec.EXACTLY))
    }
}
