package com.adastra.focuswallpaper

import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class StartupSelectionUiTest {
    private fun find(view:View,tag:String):View? {
        if(view.tag==tag)return view
        if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i),tag)?.let{return it}
        return null
    }
    private fun tagged(view:View,tag:String)=find(view,tag)!!
    private fun flush()=shadowOf(Looper.getMainLooper()).idle()
    @Test fun policiesApplyAtNewLaunchButNotOnTabChangesBackgroundResumeOrRecreation() {
        var controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get();val store=Store(activity)
        store.saveCategories(listOf(TaskCategory("work","공부"),TaskCategory(DEFAULT_CATEGORY,"할 일")))
        val repo=NoteRepository(activity);repo.saveCategory(NoteCategory("work","공부",123));repo.close()
        store.checklistStartMode=StartupSelection.FIRST;store.calendarStartMode=StartupSelection.DEFAULT;store.category=DEFAULT_CATEGORY
        controller.pause().stop().destroy();controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        var root=controller.get().window.decorView
        assertEquals("work",Store(controller.get()).category);assertTrue(tagged(root,"tasks:category:work").isSelected)
        tagged(root,"nav:calendar").performClick();assertTrue(tagged(root,"calendar:category:$NOTE_UNCATEGORIZED").isSelected)
        tagged(root,"calendar:category:work").performClick();flush()
        tagged(root,"nav:tasks").performClick();tagged(root,"nav:calendar").performClick();assertTrue(tagged(root,"calendar:category:work").isSelected)
        controller.pause().resume();flush();assertTrue(tagged(root,"calendar:category:work").isSelected)
        controller.recreate();flush();root=controller.get().window.decorView
        assertTrue(tagged(root,"calendar:category:work").isSelected)
        controller.pause().stop().destroy()
    }
    @Test fun settingsSaveIndependentModesAndRememberBothSelectionsAcrossLaunches() {
        var controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get();val store=Store(activity)
        store.saveCategories(store.categories()+TaskCategory("work","공부"))
        val repo=NoteRepository(activity);repo.saveCategory(NoteCategory("work","공부",123));repo.close()
        var root=activity.window.decorView
        tagged(root,"nav:settings").performClick();tagged(root,"settings:other-toggle").performClick()
        tagged(root,"settings:checklist-start:last").performClick();tagged(root,"settings:calendar-start:last").performClick()
        assertEquals("last",store.checklistStartMode);assertEquals("last",store.calendarStartMode)
        tagged(root,"nav:tasks").performClick();tagged(root,"tasks:category:work").performClick()
        tagged(root,"nav:calendar").performClick();tagged(root,"calendar:category:work").performClick();flush()
        assertEquals("work",store.lastCalendarCategory)
        controller.pause().stop().destroy();controller=Robolectric.buildActivity(MainActivity::class.java).setup();root=controller.get().window.decorView
        assertTrue(tagged(root,"tasks:category:work").isSelected)
        tagged(root,"nav:calendar").performClick();assertTrue(tagged(root,"calendar:category:work").isSelected)
        tagged(root,"nav:settings").performClick();tagged(root,"settings:other-toggle").performClick();tagged(root,"settings:checklist-start:default").performClick()
        assertEquals("last",Store(controller.get()).calendarStartMode)
        tagged(root,"nav:tasks").performClick();assertTrue(tagged(root,"tasks:category:work").isSelected)
        controller.pause().stop().destroy();controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        assertTrue(tagged(controller.get().window.decorView,"tasks:category:$DEFAULT_CATEGORY").isSelected)
        controller.pause().stop().destroy()
    }
    @Test fun aFreshProcessAppliesPolicyEvenWhenAndroidRestoresThePreviousActivityState() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        val repo=NoteRepository(activity);repo.saveCategory(NoteCategory("work","공부",123));repo.close()
        Store(activity).calendarStartMode=StartupSelection.DEFAULT
        val root=activity.window.decorView;tagged(root,"nav:calendar").performClick();tagged(root,"calendar:category:work").performClick();flush()
        val state=Bundle();controller.saveInstanceState(state);controller.pause().stop().destroy()
        val app=activity.application as JigeumApplication
        JigeumApplication::class.java.getDeclaredField("mainActivityStarted").apply { isAccessible=true }.setBoolean(app,false)
        val restored=Robolectric.buildActivity(MainActivity::class.java).create(state).start().resume().visible();flush()
        assertTrue(tagged(restored.get().window.decorView,"calendar:category:$NOTE_UNCATEGORIZED").isSelected)
        restored.pause().stop().destroy()
    }
}
