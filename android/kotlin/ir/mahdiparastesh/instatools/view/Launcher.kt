package ir.mahdiparastesh.instatools.view

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import ir.mahdiparastesh.instatools.InstaTools
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.Main

class Launcher : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c: InstaTools = applicationContext as InstaTools
        startActivity(Intent(this, if (c.acc != null) Main::class.java else Login::class.java))
        finish()
    }
}
