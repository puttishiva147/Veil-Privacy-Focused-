package com.veil.privacy.ui
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.veil.privacy.BuildConfig
import com.veil.privacy.licensing.LicenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class LicenseGateActivity:AppCompatActivity(){
 private lateinit var codeInput:EditText;private lateinit var activateButton:Button;private lateinit var progress:ProgressBar;private lateinit var message:TextView
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);renderGate()}
 private fun renderGate(){val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(28),dp(32),dp(28),dp(32));setBackgroundColor(Color.rgb(9,10,12))}
  val logo=TextView(this).apply{text="VEIL";textSize=34f;letterSpacing=.24f;setTextColor(Color.WHITE);typeface=Typeface.create("sans-serif",Typeface.BOLD);gravity=Gravity.CENTER}
  val title=TextView(this).apply{text="Secure activation";textSize=22f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setPadding(0,dp(18),0,dp(8))}
  val subtitle=TextView(this).apply{text="Enter your 12-digit activation code to access VEIL. Get a free 5-day trial or purchase a subscription from the official website.";textSize=14f;setTextColor(Color.LTGRAY);gravity=Gravity.CENTER}
  codeInput=EditText(this).apply{hint="12-digit activation code";inputType=InputType.TYPE_CLASS_NUMBER;filters=arrayOf(InputFilter.LengthFilter(12));textSize=20f;letterSpacing=.12f;setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);setSingleLine(true);gravity=Gravity.CENTER;setPadding(dp(12),dp(14),dp(12),dp(14));setBackgroundColor(Color.rgb(29,32,36))}
  activateButton=Button(this).apply{text="Activate VEIL";isAllCaps=false;setTextColor(Color.BLACK);setBackgroundColor(Color.rgb(216,243,106));setOnClickListener{activate()}}
  progress=ProgressBar(this).apply{visibility=View.GONE}
  message=TextView(this).apply{textSize=13f;setTextColor(Color.rgb(255,145,145));gravity=Gravity.CENTER;setPadding(0,dp(10),0,dp(10))}
  val website=Button(this).apply{text="Get free trial / subscription";isAllCaps=false;setTextColor(Color.WHITE);setBackgroundColor(Color.TRANSPARENT);setOnClickListener{openWebsite()}}
  listOf(logo,title,subtitle,codeInput,activateButton,progress,message,website).forEachIndexed{index,view->root.addView(view,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT).apply{topMargin=when(index){3->dp(26);4->dp(14);5->dp(18);7->dp(8);else->0}})}
  setContentView(root)
 }
 private fun activate(){val code=codeInput.text?.toString()?.trim().orEmpty();if(!code.matches(Regex("\\d{12}"))){message.text="Please enter exactly 12 digits.";return};activateButton.isEnabled=false;progress.visibility=View.VISIBLE;message.text="Verifying activation code securely…"
  lifecycleScope.launch{try{withContext(Dispatchers.IO){LicenseRepository.activateNewCode(this@LicenseGateActivity,code)};startActivity(Intent(this@LicenseGateActivity,MainActivity::class.java).apply{flags=Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK});finish()}catch(error:Exception){message.text=error.localizedMessage?:"Activation failed. Check your connection and try again."}finally{progress.visibility=View.GONE;activateButton.isEnabled=true}}
 }
 private fun openWebsite(){val api=BuildConfig.VEIL_LICENSE_API_BASE_URL;if(!api.startsWith("https://")||api.contains("YOUR-VEIL-WEBSITE-DOMAIN")){message.text="VEIL website address is not configured in this build.";return};try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(api.removeSuffix("/api/v1"))))}catch(_:Exception){message.text="Could not open the VEIL website."}}
 private fun dp(value:Int):Int=(value*resources.displayMetrics.density).toInt()
}
