package com.aware.phone.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.PermissionChecker
import com.aware.Aware
import com.aware.Aware_Preferences
import com.aware.phone.R
import com.aware.ui.PermissionsHandler
import kotlinx.android.synthetic.main.aware_ui_participant.*
import java.util.ArrayList

class Aware_Participant : AppCompatActivity() {

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        (supportActionBar as ActionBar).setDisplayHomeAsUpEnabled(false)
        (supportActionBar as ActionBar).setDisplayShowHomeEnabled(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.aware_ui_participant)
    }

    override fun onResume() {
        super.onResume()
        device_id.text = Aware.getSetting(this, Aware_Preferences.DEVICE_ID)
        device_name.text = Aware.getSetting(this, Aware_Preferences.DEVICE_LABEL)
        study_url.text = Aware.getSetting(this, Aware_Preferences.WEBSERVICE_SERVER)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.aware_menu, menu)
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (item.title.toString().equals(resources.getString(R.string.aware_qrcode), ignoreCase = true)) item.isVisible = false
            if (item.title.toString().equals(resources.getString(R.string.aware_team), ignoreCase = true)) item.isVisible = false
            if (item.title.toString().equals(resources.getString(R.string.aware_study), ignoreCase = true)) item.isVisible = true
            if (item.title.toString().equals(resources.getString(R.string.aware_sync), ignoreCase = true)) item.isVisible = true
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.title.toString().equals(resources.getString(R.string.aware_qrcode), ignoreCase = true)) {
            if (PermissionChecker.checkSelfPermission(this, Manifest.permission.CAMERA) != PermissionChecker.PERMISSION_GRANTED) {
                val permission = ArrayList<String>()
                permission.add(Manifest.permission.CAMERA)

                val permissions = Intent(this, PermissionsHandler::class.java)
                permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, permission)
                permissions.putExtra(PermissionsHandler.EXTRA_REDIRECT_ACTIVITY, "$packageName/$packageName.ui.Aware_QRCode")
                permissions.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(permissions)
            } else {
                val qrcode = Intent(this@Aware_Participant, Aware_QRCode::class.java)
                qrcode.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(qrcode)
            }
        }
        if (item.title.toString().equals(resources.getString(R.string.aware_study), ignoreCase = true)) {
            val studyInfo = Intent(this@Aware_Participant, Aware_Join_Study::class.java)
            studyInfo.putExtra(Aware_Join_Study.EXTRA_STUDY_URL, Aware.getSetting(this, Aware_Preferences.WEBSERVICE_SERVER))
            startActivity(studyInfo)
        }
        if (item.title.toString().equals(resources.getString(R.string.aware_team), ignoreCase = true)) {
            val about_us = Intent(this@Aware_Participant, About::class.java)
            startActivity(about_us)
        }
        if (item.title.toString().equals(resources.getString(R.string.aware_sync), ignoreCase = true)) {
            Toast.makeText(applicationContext, "Syncing data...", Toast.LENGTH_SHORT).show()
            val sync = Intent(Aware.ACTION_AWARE_SYNC_DATA)
            sendBroadcast(sync)
        }
        return super.onOptionsItemSelected(item)
    }
}