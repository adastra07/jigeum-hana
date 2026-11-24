package com.adastra.focuswallpaper

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Switch
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChecklistAndHeaderTest {
    private fun find(view: View, predicate: (View)->Boolean): View? {
        if(predicate(view))return view
        if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i),predicate)?.let {return it}
        return null
    }
    private fun tag(view: View,name: String)=find(view){it.tag==name}!!
    private fun measure(view: View) {repeat(3){view.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(740,View.MeasureSpec.EXACTLY));view.layout(0,0,360,740);shadowOf(Looper.getMainLooper()).idle()}}
    private fun labels(root: View): List<String> {
        val card=tag(root,"tasks:main-card") as ViewGroup
        return (0 until card.childCount).map { ((card.getChildAt(it) as ViewGroup).getChildAt(1) as TextView).text.toString() }
    }
    private fun preview(view: View,name: String) {
        val bitmap=android.graphics.Bitmap.createBitmap(360,740,android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(bitmap))
        val file=File("build/reports/ui/$name.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun checkboxMovesToEndReturnsToItsPositionAndSwitchCanDisableIt() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get();val store=Store(activity)
        store.saveTasks(listOf(Task("a","A"),Task("b","B"),Task("c","C")))
        val root=activity.window.decorView;tag(root,"nav:settings").performClick();tag(root,"nav:tasks").performClick();measure(root)
        fun check(title:String){(find(root){it is CheckBox && it.contentDescription=="$title 완료 여부"} as CheckBox).performClick();measure(root)}
        check("C");check("B");assertEquals(listOf("A","C","B"),labels(root))
        assertEquals(listOf("a","b","c"),Store(activity).tasks().map {it.id})
        preview(root,"checklist-completed-bottom")
        check("B");assertEquals(listOf("A","B","C"),labels(root))
        tag(root,"nav:settings").performClick();tag(root,"settings:other-toggle").performClick()
        val toggle=tag(root,"settings:completed-last") as Switch;assertTrue(toggle.isChecked);toggle.performClick();assertFalse(Store(activity).completedTasksLast)
        tag(root,"nav:tasks").performClick();check("A");assertEquals(listOf("A","B","C"),labels(root))
        controller.pause().stop().destroy()
    }
    @Test fun addButtonsHaveSameRoundedSquareGeometryAndStillOpenTheirEditors() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get();val root=activity.window.decorView;measure(root)
        val taskAdd=tag(root,"tasks:category-add");assertEquals(taskAdd.width,taskAdd.height);assertTrue(taskAdd.width in 36..44)
        val taskShape=(taskAdd.background as RippleDrawable).getDrawable(0) as GradientDrawable;assertTrue(taskShape.cornerRadius>0)
        val size=taskAdd.width;taskAdd.performClick();shadowOf(Looper.getMainLooper()).idle();assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing)
        ShadowAlertDialog.getLatestAlertDialog().dismiss();shadowOf(Looper.getMainLooper()).idle()
        tag(root,"nav:calendar").performClick();measure(root)
        val calendarAdd=tag(root,"calendar:category-add");assertEquals(size,calendarAdd.width);assertEquals(size,calendarAdd.height)
        val calendarShape=(calendarAdd.background as RippleDrawable).getDrawable(0) as GradientDrawable;assertEquals(taskShape.cornerRadius,calendarShape.cornerRadius,0f)
        calendarAdd.performClick();shadowOf(Looper.getMainLooper()).idle();assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing)
        ShadowAlertDialog.getLatestAlertDialog().dismiss();shadowOf(Looper.getMainLooper()).idle();controller.pause().stop().destroy()
    }
    @Test fun todayIsFilledSquareAndSearchHasSpacingAndRightInset() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val root=controller.get().window.decorView
        tag(root,"nav:calendar").performClick();measure(root)
        val today=tag(root,"calendar:today");val search=tag(root,"calendar:search");val header=tag(root,"calendar:header")
        assertEquals(today.width,today.height)
        val shape=(today.background as RippleDrawable).getDrawable(0) as GradientDrawable
        assertEquals(NoteUi(controller.get()).soft,shape.color!!.defaultColor)
        assertEquals(8,search.left-today.right);assertTrue(header.width-search.right>=10)
        val location=IntArray(2);search.getLocationOnScreen(location);assertTrue(360-location[0]-search.width>=26)
        today.performClick();shadowOf(Looper.getMainLooper()).idle();measure(root)
        assertEquals(java.time.YearMonth.now(),(find(root){it is CalendarMonthView} as CalendarMonthView).shownMonth)
        preview(root,"calendar-header-buttons")
        search.performClick();shadowOf(Looper.getMainLooper()).idle();assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing)
        ShadowAlertDialog.getLatestAlertDialog().dismiss();shadowOf(Looper.getMainLooper()).idle();controller.pause().stop().destroy()
    }
}
