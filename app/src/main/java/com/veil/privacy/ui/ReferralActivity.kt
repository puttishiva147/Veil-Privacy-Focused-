package com.veil.privacy.ui
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.veil.privacy.BuildConfig
import com.veil.privacy.licensing.LicenseApi
import com.veil.privacy.licensing.LicenseRepository
import com.veil.privacy.licensing.LicenseTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class ReferralActivity:AppCompatActivity(){
 private lateinit var codeView:TextView;private lateinit var statusView:TextView;private var referralCode=""
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);render();loadReferral()}
 private fun render(){
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(28),dp(24),dp(28));setBackgroundColor(Color.rgb(9,10,12))}
  val back=TextView(this).apply{text="‹  Settings";textSize=16f;setTextColor(Color.LTGRAY);setOnClickListener{finish()}}
  val title=TextView(this).apply{text="Invite friends";textSize=28f;setTextColor(Color.WHITE);setPadding(0,dp(30),0,dp(8))}
  val desc=TextView(this).apply{text="Share your unique VEIL referral code. When a friend buys and activates their first paid subscription, the currently configured reward is added to your subscription.";textSize=14f;setTextColor(Color.LTGRAY)}
  codeView=TextView(this).apply{text="Loading referral code…";textSize=24f;gravity=Gravity.CENTER;setTextColor(Color.rgb(216,243,106));setPadding(0,dp(28),0,dp(20))}
  statusView=TextView(this).apply{textSize=13f;gravity=Gravity.CENTER;setTextColor(Color.LTGRAY)}
  val copy=Button(this).apply{text="Copy referral code";isAllCaps=false;setOnClickListener{if(referralCode.isNotBlank()){val clip=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager;clip.setPrimaryClip(android.content.ClipData.newPlainText("VEIL referral code",referralCode));Toast.makeText(this@ReferralActivity,"Referral code copied",Toast.LENGTH_SHORT).show()}}}
  val share=Button(this).apply{text="Share invitation";isAllCaps=false;setOnClickListener{shareInvite()}}
  root.addView(back);root.addView(title);root.addView(desc);root.addView(codeView,LinearLayout.LayoutParams(-1,-2));root.addView(copy);root.addView(share);root.addView(statusView)
  setContentView(root)
 }
 private fun loadReferral(){lifecycleScope.launch{try{val valid=LicenseRepository.verifyAndRefresh(this@ReferralActivity);if(!valid)throw IllegalStateException("Your VEIL subscription could not be verified. Please connect to the internet and try again.");val token=LicenseTokenStore.token(this@ReferralActivity)?:throw IllegalStateException("Your activation session has expired.");val data=withContext(Dispatchers.IO){LicenseApi.referralInfo(token)};referralCode=data.getString("referralCode");codeView.text=referralCode;statusView.text=(if(data.optBoolean("referralsEnabled",true)) "Reward per eligible referral: ${data.optInt("rewardDaysPerReferral",15)} days" else "Referral programme is temporarily paused") + "\nSuccessful referrals: ${data.optInt("qualifiedReferrals")}\nFree days earned: ${data.optInt("earnedDays")}\nSubscription expires: ${data.optString("expiresAt")}"}catch(e:Exception){codeView.text="Referral details unavailable";statusView.text=e.localizedMessage?:"Please try again."}}}
 private fun shareInvite(){if(referralCode.isBlank())return;val api=BuildConfig.VEIL_LICENSE_API_BASE_URL;val site=if(api.contains("/api/v1"))api.substringBefore("/api/v1") else "";if(site.isBlank()){Toast.makeText(this,"VEIL website address is not configured.",Toast.LENGTH_LONG).show();return};val message="Join VEIL and start your 5-day free trial: $site/trial\nWhen you purchase your first paid subscription, use my invite link: $site/subscribe?ref=$referralCode\nMy referral reward is earned only after your first paid activation.";startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,message)},"Invite with VEIL"))}
 private fun dp(value:Int):Int=(value*resources.displayMetrics.density).toInt()
}
